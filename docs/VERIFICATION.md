# Verification record

Verified in this workspace using **Azul Zulu OpenJDK 1.8.0_504** on macOS ARM64 and the **Gradle 8.14.2 wrapper**. No production server or database was contacted.

| Check | Result |
| --- | --- |
| `./gradlew --no-daemon --offline clean build` | Passed |
| JUnit Jupiter | **26 tests, 0 failures, 0 errors** |
| JaCoCo application line coverage | **58.92%** (690 / 1,171 lines) |
| JaCoCo branch coverage | **83.03%** (181 / 218 branches) |
| Build coverage gate | Minimum **51%** line coverage; no application exclusions |
| OpenAPI 3.0.3 validator | Passed, including resolved component references |
| Spring mapping vs OpenAPI test | All **9** HTTP operations match |
| Executable JAR manifest | Boot JarLauncher and `com.regulus.api.App` |
| Runtime class compatibility | 15,396 class files checked; no Java > 8 base classes (module descriptors/multi-release variants excluded) |
| Embedded vendor artifact | Exactly one copy, matching the checksum in `libs/README.md` |
| Standalone Java 8 HTTP smoke | Passed: 401 authentication check, database name, account CRUD, cash/trade/stock POSTs, asset GET, OpenAPI download |
| Real vendor tests | Cash/trade/stock execution, stored-book round trip, rollover, quoted values and backdate rejection passed on H2 |
| Transaction failure | Simulated failure after database write rolled back; checked outside a test transaction |
| Docker Compose run/debug configuration | Both parsed successfully with `docker compose config` |
| Docker image build/container execution | **Not run: Docker daemon is stopped** |
| Windows startup/service integration | Documented; **not executed on a Windows host** |
| SQL Server schema/TLS/concurrency acceptance | **Not verified: no approved SQL Server test instance supplied** |

Reports are generated in `build/reports/tests/test/index.html` and `build/reports/jacoco/test/html/index.html`. The verified executable is `build/libs/regulus-api.jar`; a checksum sidecar was written for this build. Gradle 8 reports deprecations from this legacy toolchain; do not upgrade to Gradle 9 without changing the Java runtime/build plugins.

This verifies application integration and basic vendor operations, not all financial accounting results. Boot 2.7 is an older compatibility baseline; see the deployment guide for maintenance and release acceptance requirements.

Verified artifact SHA-256: `7e807c5268ae56c7dc91adf6aaa30bda033f97a908c7319e639cd217834612db`.

## Runtime documentation and CI/CD follow-up (2026-10-09)

- Confirmed the executable contains `tomcat-embed-core-9.0.83.jar`, `regulus-bo18-0.0.0-dev.jar` and `mssql-jdbc-12.6.4.jre8.jar`.
- Launched the existing executable on Java 8 with local H2 and verified authenticated HTTP responses for each documented override: command-line `--server.port`, `SERVER_PORT`, external YAML and `PORT`. Their documented precedence was exercised. All temporary processes were stopped.
- Both new GitHub workflow files passed Actionlint 1.7.12; branch/environment routing and build/test deployment gates were checked.
- All six mocked Windows deployment/rollback scenarios passed locally on PowerShell 7.6.6/macOS. The workflow also runs them on a GitHub-hosted Windows runner using Windows PowerShell. That Windows Actions execution has not yet been run.
- No application Java source changed in this follow-up; the prior Java build/coverage results above remain the application verification baseline.
- No GitHub repository settings, runners, Windows services or live environments were modified. The workflow files require the provisioning steps in [runtime and CI/CD](RUNTIME-AND-CICD.md) before deployment can run.
