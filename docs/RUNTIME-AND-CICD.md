# Embedded server, ports, release artifacts and CI/CD

## Does the current API still use embedded Tomcat?

**Yes.** `spring-boot-starter-web` in `build.gradle` brings in the embedded Tomcat server. `com.regulus.api.App` starts Spring Boot, which starts Tomcat inside the **same Java process** as the API. No separately installed Tomcat, WAR, `webapps` directory or external Tomcat `server.xml` is involved.

This also matches the supplied `current_deployment/app.jar`: its manifest uses Spring Boot's `JarLauncher`, and its nested dependencies include embedded Tomcat. The new deliverable follows that deployment model.

## How do I change the port?

The packaged `src/main/resources/application.yml` contains:

```yaml
server:
  port: ${PORT:8080}
```

The default is **8080**; `PORT` provides a project-specific override. Spring Boot also recognizes the standard `SERVER_PORT` environment variable. Changing the runtime port does **not** require recompilation or a new JAR.

### Command line: simplest explicit override

```sh
java -jar build/libs/regulus-api.jar --server.port=9090
```

For local development with the disposable H2 database:

```sh
java -jar build/libs/regulus-api.jar --spring.profiles.active=local --server.port=9090
./gradlew bootRun --args='--spring.profiles.active=local --server.port=9090'
```

On PowerShell, use `.\gradlew.bat` for Gradle. The Windows launcher forwards arguments:

```powershell
.\run-api.cmd --server.port=9090
```

Without `local`, provide the environment's database and API credentials. Changing the port does not select a database/profile.

### Environment variables

PowerShell, for the process launched from that session:

```powershell
$env:SERVER_PORT = '9090'
java -jar .\regulus-api.jar
```

POSIX shell:

```sh
SERVER_PORT=9090 java -jar regulus-api.jar
# This project's alternative:
PORT=9090 java -jar regulus-api.jar
```

For a Windows **service**, set the variable in the service wrapper's environment and restart the service. A variable set in an interactive terminal does not update an already-running service. Prefer one method rather than setting both `PORT` and `SERVER_PORT`.

### External environment configuration

Store this outside the JAR, for example `C:\regulus\config\application.yml`:

```yaml
server:
  port: 9090
```

Launch with the directory (including trailing slash):

```powershell
java -jar C:\regulus\current\regulus-api.jar --spring.config.additional-location=file:C:/regulus/config/
```

The supplied `deployment/windows/application.yml.example` explicitly sets **8089**. That overrides the packaged `PORT` placeholder, so setting only `PORT` will not override that template's literal `8089`. Change the external YAML, set **`SERVER_PORT`**, or pass **`--server.port`** instead.

For these sources the effective precedence is **command-line `--server.port` → `SERVER_PORT` environment variable → external YAML `server.port` → packaged YAML (`PORT` or 8080)**. See [Spring Boot external configuration](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/features.html#features.external-config).

Update the reverse proxy, firewall and CI health-check URL when changing the listening port. A proxy can expose HTTPS on 443 while embedded Tomcat listens internally on another port.

### Docker: host port versus container port

The checked-in Compose mapping is `127.0.0.1:8080:8080`: host port first, container port second. To expose the existing container on host port 9090, change it to:

```yaml
ports:
  - "127.0.0.1:9090:8080"
```

Tomcat still listens on **8080 inside the container**. If you instead set container `SERVER_PORT: 9090`, also change the mapping's right side to 9090. Dockerfile `EXPOSE 8080` documents the default; it does not control Tomcat's actual port.

## What artifact do we deploy?

Build on JDK 8:

```sh
./gradlew clean build
```

Deploy **`build/libs/regulus-api.jar`**, an executable Spring Boot JAR. It contains:

- Compiled application classes under `BOOT-INF/classes`.
- Embedded Tomcat, Spring, Jackson, JDBC/pool libraries and all declared runtime dependencies under `BOOT-INF/lib`.
- The selected `regulus-bo18-0.0.0-dev.jar` from `libs`, originally extracted from the deployed executable; the build verifies its checksum.
- The declared SQL Server JDBC **12.6.4.jre8** driver and Apache Commons IO.
- Packaged application defaults, local-development resources and the OpenAPI document.

It **does not copy the whole `current_deployment` folder** into the new artifact:

| Supplied deployment item | New deployment treatment |
| --- | --- |
| `app.jar` | Replaced by the newly built `regulus-api.jar` |
| `app-plain.jar` | Not needed; plain JAR packaging is disabled |
| Vendor JAR nested inside old `app.jar` | Recovered, pinned and bundled in the new executable |
| Loose vendor JAR with the same filename | Different checksum; deliberately not selected |
| Loose `mssql-jdbc-8.2.2.jre8.jar` | Replaced by the declared Java 8 JDBC driver bundled in the executable |
| `mssql-jdbc_auth-8.2.2.x86.dll` | Not needed for the configured SQL-password authentication; not bundled |
| `env.bat`, example source, old packaged credentials | Not bundled; replaced by external environment configuration |

A Java 8 runtime, SQL Server/database/schema, credentials, certificates, OS service wrapper and filesystem/log permissions remain **external prerequisites**. The JAR does not install Java, create a Windows service or provision the database. Integrated Windows SQL authentication would require a separately configured driver/JVM-compatible native DLL or Kerberos setup. See [vendor provenance](../libs/README.md) and [Windows deployment](ASSESSMENT-AND-DEPLOYMENT.md).

The Actions release bundle contains the JAR, SHA-256 sidecar, commit ID, deployment/startup scripts and a configuration **template**. Database credentials and actual server configuration are never uploaded in that bundle.

## GitHub Actions branch and environment flow

The implementation is in [ci-cd.yml](../.github/workflows/ci-cd.yml), with the reusable [deploy-windows.yml](../.github/workflows/deploy-windows.yml).

| Event | Validation | Deployment |
| --- | --- | --- |
| Pull request targeting `dev`, `qa` or `main` | Java 8 build, JUnit, coverage gate and Windows deployment-script tests | None |
| Merge/push to `dev` | Same checks and release bundle | `dev` environment; runner label `regulus-dev` |
| Merge/push to `qa` | Same checks and release bundle | `qa` environment; runner label `regulus-qa` |
| Merge/push to `main` | Same checks and release bundle | `prod` environment; runner label `regulus-prod` |

Normal promotion is **feature branch → PR into dev → PR dev into qa → PR qa into main**. Deployment uses `push`, which is emitted after a merge; direct pushes would also deploy. Protect all three branches and require PRs/checks to make this a merge-only process. The workflow maps target branches to environments; it does not itself enforce the PR source branch or prove that QA approved a particular source revision.

Each branch merge builds its own commit. Within one run, deployment downloads the **same validated artifact by immutable artifact ID**, without rebuilding on the server. Re-running only failed deployment jobs can reuse the successful build's artifact ID. This is branch-based promotion, not promotion of one identical binary across all three environments. If identical-byte promotion is later required, use a release/artifact promotion workflow instead of rebuilding each merge.

Build/test jobs use GitHub-hosted Linux/Windows runners. Deployment jobs use the requested **self-hosted Windows X64 runners**, with one environment-specific runner on each target host. There is no SSH/WinRM step. Pull-request code is not executed on deployment runners by these workflows.

## One-time GitHub and server configuration

Workflow files are created locally; they cannot create/configure repository environments, register runners or install Windows services by themselves.

1. Commit the project, wrapper JAR and pinned `libs` vendor JAR into the team's authorized repository. Keep `current_deployment` ignored. If distributing the vendor binary through source control is not permitted, provision it from your private artifact store in CI before Gradle runs.
2. Create GitHub environments named **`dev`**, **`qa`**, **`prod`**. Restrict their allowed branches to `dev`, `qa`, `main` respectively. Configure required reviewers for production if your repository plan supports them; that protection is a GitHub setting, not an approval rule declared in YAML. [GitHub environment setup and plan limitations](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
3. Register a current, supported GitHub Actions runner on each Windows host with default labels `self-hosted`, `Windows`, `X64`, plus **only its own** label `regulus-dev`, `regulus-qa` or `regulus-prod`. Restrict runner-group access to this trusted repository/workflow. These action versions target GitHub.com and require current runners; confirm the real Windows Server version supports the runner runtime. The runner's Node runtime is separate from the application's Java 8 runtime.
4. Provision the existing Java-aware Windows service wrapper (for example your team's WinSW/NSSM setup) with a stable executable path. For each environment, use this layout:

```text
C:\regulus\
  current\regulus-api.jar       <- service always runs this path
  config\application.yml       <- persistent environment configuration
  logs\                        <- service/application logs
  releases\<release-id>\       <- staged JAR, checksum and previous JAR backup
```

For example, configure the wrapper to execute `C:\Java\jdk8\bin\java.exe` with:

```text
-jar C:\regulus\current\regulus-api.jar --spring.config.additional-location=file:C:/regulus/config/
```

The foreground `run-api.cmd` is not a Windows service installer and defaults to a `config` directory beside itself. Use the explicit configuration path above for the automated `current` layout. Provision the initial JAR manually, start the service and verify it once. Automated updates intentionally require a working existing service and previous JAR for rollback.

5. Give the runner account permission to stop/start **that service** and write to the deployment directories. Give the application service account read access to its JAR/config and write access to logs. Configure JDK/JRE 8 and DB/API credentials for the **application service**, not just the runner process. Keep profile `local` disabled on all deployed environments, including dev and QA when using their real databases.
6. Set the following **environment variables in GitHub Settings → Environments**, separately for each environment:

| GitHub variable | Example | Meaning |
| --- | --- | --- |
| `DEPLOYMENT_ROOT` | `C:\regulus` | Absolute application root on that runner's machine |
| `SERVICE_NAME` | `RegulusApi` | Existing Windows service name, not display name |
| `HEALTHCHECK_URL` | `http://127.0.0.1:8089/api/database` | Direct authenticated endpoint on that instance's configured port |

Set environment **secrets** `API_USERNAME` and `API_PASSWORD` to credentials accepted by that deployed API. CI uses these for health checks only; it does not rewrite the service's credentials or configuration. Keep the DB secrets in the service's protected external configuration/secret store. Each environment has its own DB/API configuration and can use a different port.

7. Require checks **Java 8 build and tests** and **Deployment script tests** before merging. Protect workflow/deployment-script edits through review. Enable Actions and allow the pinned official action repositories. The environments and runner labels must exist before expecting deployment jobs to run; otherwise jobs wait for a runner or fail configuration checks.

## Deployment, health checks and rollback

`deployment/windows/deploy.ps1` performs these steps:

1. Verify the JAR/checksum, environment configuration, running service, previous JAR and baseline authenticated `/api/database` response before stopping anything.
2. Stage the validated artifact and checksum under a unique release ID and save/checksum the previous JAR.
3. Stop the service, replace only `current/regulus-api.jar`, and start the service.
4. Wait for the running state and an HTTP 200/nonempty database-name response using the configured credentials. HTTP is allowed only on loopback; use HTTPS for other hostnames. The health check verifies API authentication and DB connectivity, not every financial operation.
5. Record `current/release.json` after success. On failure after service changes, stop the failed release, restore/start the previous JAR and health-check it. The workflow still fails even if rollback succeeds. If rollback also fails, an operator must recover from the retained backup and service logs.

Updates cause a short service interruption; this is not blue/green or zero-downtime deployment. External config is never overwritten. Database migrations and financial-data rollback are not performed. Retained release directories/backups need an operations retention policy. Do not cancel a deployment while it is replacing/restarting the service; hard process termination/runner loss may bypass script rollback.

Deployment concurrency is one run per environment with cancellation disabled. GitHub may supersede pending runs and does not guarantee queue order: avoid overlapping promotion approvals, and verify the commit/release metadata before approving an older queued run. The pipeline does not auto-retry financial requests.

Reports and bundles appear under each Actions run's Artifacts for 30 days. The workflow pins action commits; update those pins deliberately when maintaining the pipeline.

## Local verification

Run Java verification with `./gradlew clean build`. The six deployment script scenarios can run without a real Windows service:

```powershell
.\deployment\windows\deploy.Tests.ps1
```

They mock service and HTTP commands and cover successful installation, checksum rejection, start failure rollback, health failure rollback, missing configuration and rejection of remote plaintext health checks. They do not replace a staged Windows deployment test. The actual GitHub environment/runner/service wiring must be exercised after provisioning.
