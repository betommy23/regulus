[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ArtifactDirectory,
    [Parameter(Mandatory = $true)][string]$DeploymentRoot,
    [Parameter(Mandatory = $true)][string]$ServiceName,
    [Parameter(Mandatory = $true)][uri]$HealthcheckUrl,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$ReleaseId,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Wait-ServiceState([string]$Expected) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if ((Get-Service -Name $ServiceName).Status.ToString() -eq $Expected) { return }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Service did not reach $Expected within $TimeoutSeconds seconds."
}

function Test-Application {
    $credentials = '{0}:{1}' -f $env:HEALTHCHECK_USERNAME, $env:HEALTHCHECK_PASSWORD
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($credentials))
    $headers = @{ Authorization = "Basic $encoded" }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $HealthcheckUrl -Headers $headers -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 0
            if ($response.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace([string]$response.Content)) {
                return
            }
        } catch {
            # Do not print request/credential details. The final error is intentionally generic.
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Authenticated database health check failed.'
}

# Complete preflight and checksum checks before touching a running service.
if (-not [IO.Path]::IsPathRooted($DeploymentRoot)) { throw 'DeploymentRoot must be absolute.' }
$root = [IO.Path]::GetFullPath($DeploymentRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
if ($root -eq [IO.Path]::GetPathRoot($root).TrimEnd([IO.Path]::DirectorySeparatorChar)) {
    throw 'DeploymentRoot must not be a drive/filesystem root.'
}
if ([string]::IsNullOrWhiteSpace($env:HEALTHCHECK_USERNAME) -or [string]::IsNullOrWhiteSpace($env:HEALTHCHECK_PASSWORD)) {
    throw 'HEALTHCHECK_USERNAME and HEALTHCHECK_PASSWORD are required.'
}
if ($HealthcheckUrl.Scheme -ne 'https' -and -not ($HealthcheckUrl.Scheme -eq 'http' -and $HealthcheckUrl.IsLoopback)) {
    throw 'HealthcheckUrl must use HTTPS or loopback HTTP.'
}
if ($HealthcheckUrl.AbsolutePath -ne '/api/database' -or $HealthcheckUrl.Query -or $HealthcheckUrl.UserInfo) {
    throw 'Use the direct /api/database health URL without query parameters or embedded credentials.'
}
$sourceJar = Join-Path $ArtifactDirectory 'regulus-api.jar'
$checksumFile = Join-Path $ArtifactDirectory 'regulus-api.jar.sha256'
if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) { throw 'Artifact JAR missing.' }
$checksumLine = (Get-Content -LiteralPath $checksumFile -Raw).Trim()
if ($checksumLine -notmatch '^([a-fA-F0-9]{64})\s+\*?regulus-api\.jar$') { throw 'Invalid checksum file.' }
$expectedHash = $Matches[1]
if ((Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Artifact checksum mismatch.' }
$config = Join-Path $root 'config/application.yml'
if (-not (Test-Path -LiteralPath $config -PathType Leaf)) { throw 'Provision external config/application.yml first.' }
$service = Get-Service -Name $ServiceName
if ($service.Status.ToString() -ne 'Running') { throw 'Existing service must be running before deployment.' }
$currentDirectory = Join-Path $root 'current'
$currentJar = Join-Path $currentDirectory 'regulus-api.jar'
if (-not (Test-Path -LiteralPath $currentJar -PathType Leaf)) {
    throw 'Provision the initial service/JAR first; automated deployment requires a rollback artifact.'
}
# Check the existing service/config/database before starting a release.
Test-Application

$releaseDirectory = Join-Path (Join-Path $root 'releases') $ReleaseId
if (Test-Path -LiteralPath $releaseDirectory) { throw 'Release directory already exists; use a new ReleaseId.' }
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$stagedJar = Join-Path $releaseDirectory 'regulus-api.jar'
$backupJar = Join-Path $releaseDirectory 'previous-regulus-api.jar'
Copy-Item -LiteralPath $sourceJar -Destination $stagedJar
Copy-Item -LiteralPath $checksumFile -Destination $releaseDirectory
if ((Get-FileHash -LiteralPath $stagedJar -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Staged artifact checksum mismatch.' }
$previousHash = (Get-FileHash -LiteralPath $currentJar -Algorithm SHA256).Hash
Copy-Item -LiteralPath $currentJar -Destination $backupJar
if ((Get-FileHash -LiteralPath $backupJar -Algorithm SHA256).Hash -ne $previousHash) { throw 'Rollback backup checksum mismatch.' }

$serviceTouched = $false
try {
    $serviceTouched = $true
    Stop-Service -Name $ServiceName -ErrorAction Stop
    Wait-ServiceState 'Stopped'
    Copy-Item -LiteralPath $stagedJar -Destination $currentJar -Force
    if ((Get-FileHash -LiteralPath $currentJar -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Installed JAR checksum mismatch.' }
    Start-Service -Name $ServiceName -ErrorAction Stop
    Wait-ServiceState 'Running'
    Test-Application
    $metadata = @{ release = $ReleaseId; sha256 = $expectedHash; deployedAtUtc = [DateTime]::UtcNow.ToString('o') }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $currentDirectory 'release.json') -Encoding UTF8
    Write-Output "Deployed release $ReleaseId successfully."
} catch {
    if ($serviceTouched) {
        try {
            if ((Get-Service -Name $ServiceName).Status.ToString() -ne 'Stopped') {
                Stop-Service -Name $ServiceName -ErrorAction Stop
                Wait-ServiceState 'Stopped'
            }
            Copy-Item -LiteralPath $backupJar -Destination $currentJar -Force
            Start-Service -Name $ServiceName -ErrorAction Stop
            Wait-ServiceState 'Running'
            Test-Application
            Write-Warning 'Deployment failed; the previous JAR was restored and passed the health check.'
        } catch {
            throw "Deployment and automatic rollback failed. Operator recovery required; backup is $backupJar."
        }
    }
    throw 'Deployment failed. See the service logs and deployment output; this run is not successful.'
}
