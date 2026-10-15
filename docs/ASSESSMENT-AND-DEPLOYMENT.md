# Assessment and Windows deployment

See [runtime, ports and CI/CD](RUNTIME-AND-CICD.md) for port overrides, the exact release artifact, and automated `dev`/`qa`/`main` deployment using self-hosted Windows runners.

## Confirmed findings

**This is a Spring Boot REST API, originally deployed as an executable JAR built with Gradle.**

Evidence from the supplied material:

| Evidence | Finding |
| --- | --- |
| Original `App.java` | `@SpringBootApplication` and `SpringApplication.run` |
| Original `ApiController.java` | `@RestController`, `/api` mapping and nine HTTP operations |
| Original repositories | Spring `JdbcTemplate`, SQL Server `dbo` tables, `TOP 1` and `DB_NAME()` |
| `current_deployment/app.jar` manifest | `JarLauncher`, `Start-Class: com.example.App`, Spring Boot **2.7.18** |
| Nested dependencies | Embedded Tomcat **9.0.83**, SQL Server JDBC **8.2.2.jre8**, Spring Security/JDBC/Jackson |
| `env.bat` | `GRADLE_HOME` points to **Gradle 8.14.2** |
| Application/vendor class files | Java **8**, class-file major version **52** |
| `app-plain.jar` | Plain application JAR without bundled dependencies; not the deployable executable |
| Two vendor JAR copies | Same filename but different checksums; the new build pins the copy nested in `app.jar` |

The original flat source download lacked a Gradle/Maven build, resource configuration, tests and the vendor library. Deployment evidence supplied later resolved the dependency and launch architecture. The reconstructed build uses **Gradle**, not Maven, and creates `build/libs/regulus-api.jar`. It includes the server, JDBC driver and vendor library; deploy just this executable plus external configuration/startup/service integration.

There is **no separate Tomcat installation requirement**. Tomcat is the embedded HTTP server started by Spring Boot. Do not copy this JAR into an external Tomcat `webapps` directory. Do not use `app-plain.jar` as the runnable deliverable. The uploaded deployment contains no Windows service definition, so the exact existing service manager, startup arguments and reverse proxy remain unverified.

“Windows Server 2018” is not a confirmed OS edition. Check the actual name/build with `Get-ComputerInfo | Select WindowsProductName, WindowsVersion, OsBuildNumber`. Microsoft lists Windows Server **2019**, released in 2018; see [Windows Server release information](https://learn.microsoft.com/en-us/windows/release-health/windows-server-release-info). The Java service deployment below is independent of that naming ambiguity, but confirm the OS/JDK support combination with operations.

## Runtime and support constraints

[Spring Boot 2.7.18 supports Java 8 and Tomcat 9](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html). The new build follows the deployed Boot version and the required Java 8 runtime. Gradle 8.14.2 was selected from deployment evidence and exercised on JDK 8. Java 8 runtime libraries and application bytecode are verified during the build/testing work.

[Boot 2.7.18 ended open-source support for Boot 2.x](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/). This is a compatibility reconstruction, not a claim that the 2023 dependency baseline is fully patched for current production use. Obtain supported maintenance versions/security patches and test them before release. Moving to newer Boot major versions requires a newer Java runtime and is outside the requested Java 8 constraint.

The application uses SQL username/password authentication by default. The supplied `mssql-jdbc_auth-8.2.2.x86.dll` is unnecessary for that mode and is **not** copied into the new distribution. If operations requires Windows integrated authentication, configure it deliberately with a native DLL matching **both** the new JDBC version and JVM architecture, or the supported Kerberos mode. A 32-bit DLL cannot be used by a 64-bit JVM. See [Microsoft JDBC connection properties](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties).

## Database and functional readiness

The database is existing infrastructure, not provisioned by this project. Confirm its SQL Server version, schema, credentials/permissions, backup and test data with the team. The active repository expects:

- `dbo.accounts`: `firmId`, `accountId`, `correspondent`, `branch`, `rep`, `accountName`, `accountCat`, `marginable`, `aggregate`, `taxlotMethod`.
- `dbo.accountBook`: `accountBookID`, `firmId`, `accountId`, `processDate`, `bookJson`.

The local schema is inferred for tests; **do not run it on production**. Review the production composite keys/indexes and uniqueness for account keys and book dates. Existing duplicate book rows, SQL types, column lengths, triggers and foreign keys are not known. The API does not run schema migrations on server startup. Its H2 local mode is for development, not an alternative production database.

The financial engine's calculations and serializer are the recovered vendor implementation. Persistence now uses bound SQL parameters and Spring transactions because inspection showed unsafe string-built SQL and swallowed failures in vendor SQL helpers. Review the changes in [financial integration notes](LEGACY-INTEGRATION.md), then compare known cash/trade/stock cases and process-date rollover on a restored SQL Server test database. The request-schema tests are not a financial accounting certification.

The deployment evidence contains database credentials in source/packaged configuration. It remains untouched and is excluded by `.gitignore`/`.dockerignore`; the new JAR does not include it. Treat those credentials as exposed and rotate them. New configuration uses environment variables and a restricted external configuration directory.

## Build on a developer or CI machine

Install JDK 8 and set `JAVA_HOME`. From PowerShell at the project root:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat clean build
Get-FileHash .\build\libs\regulus-api.jar -Algorithm SHA256
```

Keep the JAR, checksum, Gradle dependency report and test/coverage reports with the release. A JDK is required to build; an appropriate Java 8 JRE is sufficient to run. No Gradle installation or dependency download is needed on the server because the executable bundles its runtime libraries.

## Stage the Windows release

1. Create a release directory, for example `C:\regulus\releases\2026-10-08`, plus a protected configuration directory and writable logs directory.
2. Copy `build\libs\regulus-api.jar` and `deployment\windows\run-api.cmd` into the release directory. Compare the server-side SHA-256 to the CI checksum.
3. Create `config\application.yml` beside the JAR using `deployment\windows\application.yml.example`. Ensure the service account can read configuration and write logs. Restrict secret-bearing configuration to that account and administrators.
4. Configure these variables for the **service process**, using your existing secret-management method:

```text
JAVA_HOME=C:\Java\jdk8
DB_URL=jdbc:sqlserver://sql-host:1433;databaseName=regulus;encrypt=true;trustServerCertificate=false
DB_USERNAME=regulus_app
DB_PASSWORD=<database secret>
API_USERNAME=regulus_client
API_PASSWORD=<long random API secret>
```

Use a fixed SQL Server TCP port or a confirmed instance configuration. Keep encryption enabled and install a trusted SQL Server certificate/CA chain. Do not copy the old development `trustServerCertificate=true` setting as the production default. Firewall rules must permit the service host to reach SQL Server.

5. Confirm `SPRING_PROFILES_ACTIVE` is **not** `local`. The local profile uses disposable H2 and known development credentials. Default server configuration requires real credentials; it does not silently start in local mode.
6. Start a foreground smoke check with `run-api.cmd`. The template listens on port 8089 to accommodate the original README's port; `PORT` controls the packaged default when no external port is set. Select the actual port used by the existing service/proxy. Startup logs should report Java 8, the intended server port and a started application.

The equivalent direct command is:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar C:\regulus\releases\2026-10-08\regulus-api.jar `
  --spring.config.additional-location=file:C:/regulus/config/ `
  --server.port=8089
```

The trailing slash in an external configuration **directory** matters. `run-api.cmd` defaults to the `config` directory beside the script. The `.env.example` file is only a reference; Spring Boot does not automatically load `.env` files.

## Windows service and network setup

Reuse the organization's existing Java-aware Windows service wrapper, scheduled-task convention or service manager. Discover it before replacing the deployed files: the download does not establish whether it is WinSW, NSSM, a scheduled task or another mechanism. A plain `java.exe -jar` command is not itself a Windows Service Control Manager service; do not register it directly using `sc.exe create` and expect service lifecycle support.

Configure that manager to run Java 8 with `-jar` and the external configuration arguments above, under a dedicated account. Set the working directory, process environment, log retention, restart-on-failure and graceful stop policy there. A running Windows service does not necessarily inherit variables changed in your PowerShell session; restart/reconfigure it through the service manager. The startup script is a foreground launcher, not an installer.

Use the existing TLS reverse proxy or configure Spring Boot TLS with a managed keystore. Basic credentials must travel over HTTPS outside local testing. Permit only the intended clients/proxy through Windows Firewall, and limit database permissions to required tables/operations. All API users currently share full access; add firm/role authorization if the business requires it.

## Verify, release and roll back

Against the staged host, check:

```powershell
curl.exe -i http://localhost:8089/api/accounts
# Expect 401, proving authentication is enabled.
curl.exe -u regulus_client http://localhost:8089/api/database
# curl prompts for the password; verify the intended database name.
curl.exe -u regulus_client "http://localhost:8089/api/accounts?firmId=1"
curl.exe -u regulus_client http://localhost:8089/openapi.yaml -o openapi.yaml
```

Perform write smoke tests only against an approved test database/account. Validate financial responses against known vendor expectations, especially rollover, existing JSON books and quotes in descriptions. Exercise SQL Server transaction conflicts and rollback; do not blindly retry financial POSTs.

For release, retain the prior JAR/config/checksum, stop the existing service, switch to the validated release and restart through the service manager. Avoid overwriting a running JAR. If health checks fail, stop, restore the prior release/config and restart. This project does not alter production DDL automatically; nevertheless, validate any book-format/business changes against rollback expectations before real financial writes. No deployment to the actual Windows host was performed in this workspace.
