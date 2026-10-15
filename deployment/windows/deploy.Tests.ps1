# Self-contained tests: service and HTTP commands are mocked. No service or network is touched.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$deployScript = Join-Path $PSScriptRoot 'deploy.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('regulus-deploy-tests-' + [Guid]::NewGuid().ToString('N'))
$originalUsername = $env:HEALTHCHECK_USERNAME
$originalPassword = $env:HEALTHCHECK_PASSWORD
$global:RegulusDeploymentTestState = @{}

function Get-Service {
    [CmdletBinding()]param([string]$Name)
    [pscustomobject]@{ Status = $global:RegulusDeploymentTestState.Status }
}
function Stop-Service {
    [CmdletBinding()]param([string]$Name)
    $global:RegulusDeploymentTestState.Stops++
    $global:RegulusDeploymentTestState.Status = 'Stopped'
}
function Start-Service {
    [CmdletBinding()]param([string]$Name)
    $global:RegulusDeploymentTestState.Starts++
    $installed = Get-Content -LiteralPath $global:RegulusDeploymentTestState.Jar -Raw
    if ($global:RegulusDeploymentTestState.FailStart -and $installed.Trim() -eq 'new') { throw 'Simulated start failure' }
    $global:RegulusDeploymentTestState.Status = 'Running'
}
function Invoke-WebRequest {
    [CmdletBinding()]param($Uri, $Headers, [switch]$UseBasicParsing, $TimeoutSec, $MaximumRedirection)
    if ($Headers.Authorization -notlike 'Basic *') { throw 'Missing authorization' }
    $installed = Get-Content -LiteralPath $global:RegulusDeploymentTestState.Jar -Raw
    if ($global:RegulusDeploymentTestState.FailHealth -and $installed.Trim() -eq 'new') { throw 'Simulated unhealthy release' }
    [pscustomobject]@{ StatusCode = 200; Content = 'test-database' }
}
function Start-Sleep { param($Seconds) Microsoft.PowerShell.Utility\Start-Sleep -Milliseconds 10 }
function Assert-True($Condition, [string]$Message) { if (-not $Condition) { throw "Assertion failed: $Message" } }

function New-Fixture([string]$Name) {
    $folder = Join-Path $testRoot $Name
    $root = Join-Path $folder 'server'
    $artifact = Join-Path $folder 'artifact'
    foreach ($dir in @($artifact, (Join-Path $root 'current'), (Join-Path $root 'config'))) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $jar = Join-Path $root 'current/regulus-api.jar'
    Set-Content -LiteralPath $jar -Value 'old' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $root 'config/application.yml') -Value 'server: {}' -Encoding ASCII
    $source = Join-Path $artifact 'regulus-api.jar'
    Set-Content -LiteralPath $source -Value 'new' -Encoding ASCII
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    Set-Content -LiteralPath (Join-Path $artifact 'regulus-api.jar.sha256') -Value "$hash  regulus-api.jar" -Encoding ASCII
    $global:RegulusDeploymentTestState = @{ Status = 'Running'; Stops = 0; Starts = 0; Jar = $jar; FailStart = $false; FailHealth = $false }
    return @{ ArtifactDirectory = $artifact; DeploymentRoot = $root; ServiceName = 'MockRegulus';
        HealthcheckUrl = 'http://127.0.0.1:8089/api/database'; ReleaseId = $Name; TimeoutSeconds = 1 }
}

function Assert-Rejected($Arguments) {
    $failed = $false
    try { & $deployScript @Arguments } catch { $failed = $true }
    Assert-True $failed 'deployment must report failure'
}

try {
    $env:HEALTHCHECK_USERNAME = 'test-user'
    $env:HEALTHCHECK_PASSWORD = 'test-password'
    $args1 = New-Fixture 'success'
    & $deployScript @args1
    Assert-True ((Get-Content $global:RegulusDeploymentTestState.Jar -Raw).Trim() -eq 'new') 'new JAR installed'
    Assert-True ($global:RegulusDeploymentTestState.Status -eq 'Running') 'service running'
    Assert-True (Test-Path (Join-Path $args1.DeploymentRoot 'current/release.json')) 'release metadata recorded'
    Assert-True ((Get-Content (Join-Path $args1.DeploymentRoot 'releases/success/previous-regulus-api.jar') -Raw).Trim() -eq 'old') 'previous JAR retained'

    $args2 = New-Fixture 'checksum-failure'
    Add-Content (Join-Path $args2.ArtifactDirectory 'regulus-api.jar') 'tampered'
    Assert-Rejected $args2
    Assert-True ($global:RegulusDeploymentTestState.Stops -eq 0) 'checksum failure does not stop service'

    $args3 = New-Fixture 'start-failure'
    $global:RegulusDeploymentTestState.FailStart = $true
    Assert-Rejected $args3
    Assert-True ((Get-Content $global:RegulusDeploymentTestState.Jar -Raw).Trim() -eq 'old') 'start failure restores old JAR'
    Assert-True ($global:RegulusDeploymentTestState.Status -eq 'Running') 'rollback restarts service'

    $args4 = New-Fixture 'health-failure'
    $global:RegulusDeploymentTestState.FailHealth = $true
    Assert-Rejected $args4
    Assert-True ((Get-Content $global:RegulusDeploymentTestState.Jar -Raw).Trim() -eq 'old') 'health failure restores old JAR'
    Assert-True ($global:RegulusDeploymentTestState.Status -eq 'Running') 'rollback passes health check'

    $args5 = New-Fixture 'missing-config'
    Remove-Item (Join-Path $args5.DeploymentRoot 'config/application.yml')
    Assert-Rejected $args5
    Assert-True ($global:RegulusDeploymentTestState.Stops -eq 0) 'missing config does not stop service'

    $args6 = New-Fixture 'remote-http'
    $args6.HealthcheckUrl = 'http://example.com/api/database'
    Assert-Rejected $args6
    Assert-True ($global:RegulusDeploymentTestState.Stops -eq 0) 'remote plaintext credentials rejected before service changes'
    Write-Output 'All six deployment-script scenarios passed.'
} finally {
    $env:HEALTHCHECK_USERNAME = $originalUsername
    $env:HEALTHCHECK_PASSWORD = $originalPassword
    Remove-Variable RegulusDeploymentTestState -Scope Global -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
