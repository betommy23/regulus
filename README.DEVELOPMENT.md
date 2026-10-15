# Regulus API — developer guide

This is the new guide for the organized project. [README.md](README.md) is the original historical description and is retained unchanged. See [assessment and Windows deployment](docs/ASSESSMENT-AND-DEPLOYMENT.md) for the inspected deployment and operational instructions.

For engineering standards and onboarding, see [Java development practices and the SDLC](docs/JAVA-DEVELOPMENT-AND-SDLC.md). It covers project structure, MVC, dependency management, testing, semantic versioning, versioned artifact examples and release practices, with current behavior distinguished from proposed improvements.

## Stack and artifact

**Java 8 · Gradle 8.14.2 · Spring Boot 2.7.18 · Spring MVC/JDBC/Security · SQL Server.**

Build an executable JAR and run it with `java -jar`. The supplied deployed `app.jar` confirms this architecture: it embeds Tomcat 9.0.83 and Spring Boot 2.7.18. There is **no requirement for a separate Tomcat installation** or WAR deployment. `env.bat` identifies Gradle 8.14.2; no original Gradle build file was supplied. Maven is not used in the reconstructed project.

All nine original HTTP operations are implemented, including the financial routes. The real vendor library was recovered from the executable deployment. Its provenance and checksum are documented in [libs/README.md](libs/README.md). It is different from the loose vendor JAR in the deployment folder. The included local `AccountAsset` is retained and tested, but it is not substituted for the vendor engine.

## Embedded server, port and CI/CD

The executable uses **embedded Tomcat**. Change its port without rebuilding:

```sh
java -jar build/libs/regulus-api.jar --server.port=9090
```

For local H2, also pass `--spring.profiles.active=local`. `SERVER_PORT=9090` or external `server.port` configuration are alternatives. Deploy the executable JAR, which bundles runtime dependencies and the selected vendor library; do not copy the entire old deployment folder.

[Runtime, port and CI/CD guide](docs/RUNTIME-AND-CICD.md) explains the exact artifact contents, configuration precedence, Docker ports, and GitHub Actions setup. The workflows build/test PRs and deploy merges into **dev → dev**, **qa → QA**, **main → production**, using environment-specific self-hosted Windows runners and an existing Windows service. Provision the GitHub environments, runner labels, variables and secrets described there before enabling deployment.

## Layout and MVC flow

```text
build.gradle / settings.gradle       Dependency management, JAR, tests, coverage gate
gradlew / gradlew.bat / gradle/        Gradle wrapper (requires JDK 8)
libs/                                Pinned, recovered vendor JAR and provenance
src/main/java/com/regulus/api/
  App.java                           Executable application entry point
  controller/                        HTTP routing and consistent errors
  service/                           Account rules and vendor financial orchestration
  repository/                        Parameterized SQL via JdbcTemplate
  model/                             Account DTOs and preserved local asset classes
  config/                            Stateless HTTP Basic authentication
  exception/                         Application HTTP errors
src/main/resources/
  application.yml                    External SQL Server/API credentials
  application-local.yml              Disposable H2 database/local credentials
  db/local-schema.sql                Inferred development schema, not production DDL
src/test/java/                       JUnit 5 unit/integration/contract tests
docs/openapi.yaml                    Complete HTTP contract and recovered vendor fields
docs/original/                       Unmodified downloaded Java files/unused npm manifest
examples/                            Legacy response and financial request examples
deployment/                          Windows startup script and configuration template
Dockerfile / compose*.yaml            Java 8 container build, local run and debugger
current_deployment/                  User-supplied evidence; ignored, not packaged
```

Request → security → controller → service → repository → database; Jackson or the vendor serializer provides the JSON view. There are no HTML views/JSPs. Services use constructor injection and transactions. `RegulusBoAssetService` uses the real vendor's calculations, rollover and JSON, with Spring-managed parameterized persistence instead of its unsafe static SQL helpers. Its writes use serializable isolation to prevent silent lost updates; concurrency conflicts fail and roll back. A JVM lock protects the vendor's static serializer state within this API. SQL Server concurrency/DDL must still be tested with the real schema.

The preserved local `AccountAsset` retains its original persistence helpers and is not an HTTP service. Actsum/Bal/User/Test repositories are preserved without new routes. See [financial integration notes](docs/LEGACY-INTEGRATION.md) for semantic changes and limits.

## Prerequisites, dependencies and IDE setup

Install a maintained **JDK 8**, set `JAVA_HOME` to it and add its `bin` directory to `PATH`. The build deliberately rejects newer JDKs, so compilation and tests run against the required runtime.

```sh
java -version
./gradlew --version
./gradlew downloadDependencies
./gradlew dependencies --configuration runtimeClasspath
```

Windows PowerShell uses `.\gradlew.bat` instead of `./gradlew`. The wrapper downloads Gradle 8.14.2 and verifies its distribution checksum. Dependencies are resolved from Maven Central into the Gradle user cache (`~/.gradle`); Maven is not required. After resolution, `./gradlew --offline build` works from cache. Corporate proxy/repository credentials belong in user Gradle settings, not source control.

Import the project as **Gradle**, use the wrapper, and choose JDK 8 for the project and Gradle JVM. Boot's dependency BOM aligns Spring, Jackson, logging, HikariCP, H2 and JUnit/Mockito versions. Explicit dependencies include the pinned vendor JAR, Apache Commons IO (referenced by vendor bytecode), and Microsoft's **12.6.4.jre8** JDBC driver. The deployed driver was 8.2.2.jre8; the newer driver requires SQL Server/TLS regression testing. Neither the inspected source nor vendor bytecode requires the old deployment's Guava, so it is not carried forward.

There is no JPA/Hibernate, Lombok, Node, Gradle global install, or separately installed servlet-container dependency.

## Build, test and coverage

```sh
./gradlew clean build
```

This compiles, runs **JUnit Jupiter** tests, creates `build/libs/regulus-api.jar`, and enforces **at least 51% line coverage**. All compiled application classes are counted, with no exclusions; third-party vendor bytecode is a dependency, not application source.

- Test report: `build/reports/tests/test/index.html`
- Coverage HTML: `build/reports/jacoco/test/html/index.html`
- Coverage XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- `./gradlew test`: tests/report only; use `build` or `check` for the coverage gate.
- `./gradlew bootJar`: packaging only; use `build` for a validated release.
- `./gradlew generateVendorSchemas`: regenerates vendor public-field schemas to `build/vendor-schemas.json` for contract maintenance.

Tests cover CRUD/firm isolation, PATCH/null/false handling, authentication/errors, parameterized persistence, local asset rules, real vendor cash/trade/stock processing and rollover, and API-route/OpenAPI consistency. H2 runs in SQL Server compatibility mode. It cannot certify the production database, all vendor financial business rules, or Windows service behavior. See [verification record](docs/VERIFICATION.md).

## Run and debug locally

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
# Or after build:
java -jar build/libs/regulus-api.jar --spring.profiles.active=local
```

Local H2 data disappears when the process stops. No profile is selected automatically: a normal server launch requires DB and API credentials. The local-only login is `developer` / `local-only-password`.

```sh
curl -u developer:local-only-password http://localhost:8080/api/database
curl -u developer:local-only-password -H 'Content-Type: application/json' \
  -d '{"firmId":1,"accountId":"ABC123","accountName":"Example Account"}' \
  http://localhost:8080/api/account/edit
curl -u developer:local-only-password 'http://localhost:8080/api/accounts?firmId=1'
curl -u developer:local-only-password -X PATCH -H 'Content-Type: application/json' \
  -d '{"accountName":"Updated","marginable":false}' \
  'http://localhost:8080/api/account/edit?firmId=1&accountId=ABC123'
curl -u developer:local-only-password -H 'Content-Type: application/json' \
  --data-binary @examples/cash-request.json http://localhost:8080/api/cash
curl -u developer:local-only-password 'http://localhost:8080/api/asset?firmId=1&accountId=ABC123'
```

Use `curl.exe` explicitly on Windows and a JSON file (`--data-binary @file.json`) to avoid shell quoting differences. Run `com.regulus.api.App` from your IDE with profile `local`, or attach a debugger:

```sh
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar build/libs/regulus-api.jar --spring.profiles.active=local
```

JDWP is unauthenticated; expose it only on your development machine.

## Docker testing/debugging

Requires a running Docker daemon in **Linux container mode**. The Windows bare-metal server does not need Docker.

```sh
docker compose up --build
docker compose -f compose.yaml -f compose.debug.yaml up --build
# API localhost:8080; attach Java debugger to localhost:5005.
docker compose down
```

The Docker build runs Gradle tests and the coverage gate on Java 8. The runtime uses a non-root UID. Compose publishes ports to host loopback and uses disposable H2. No SQL Server container is provisioned. For real SQL Server testing, supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `API_USERNAME`, `API_PASSWORD` to the image and omit profile `local`. Database hostnames must be reachable from the container. Pin image digests in your release pipeline; the supplied Java 8 tags receive updates.

## API/authentication

[OpenAPI 3.0.3](docs/openapi.yaml) includes all nine operations, JSON fields, query parameters, status codes, media types and authentication. Download it with Basic authentication at `http://localhost:8080/openapi.yaml`, or import the file into an OpenAPI viewer/client. No Swagger UI is installed.

Every request needs `Authorization: Basic …`; use HTTPS outside local testing. Writes require `Content-Type: application/json`; responses are JSON except `/api/database` (`text/plain`). There is no login/token endpoint, session cookie, form login or API-key header. The configured caller has full API access, including financial writes; per-firm authorization is not implemented.

Basic authentication intentionally replaces the original public API. PATCH applies only non-null editable fields, including false booleans, and ignores body identity fields. Errors use `{"error":"message"}`. Missing/bad credentials: 401; missing role: 403; invalid inputs or vendor rejection: 400; missing accounts/books: 404; duplicates/constraints/backdated transactions: 409; unexpected failures: sanitized 500.

Financial schemas come from the actual deployed vendor classes, not the unrelated local models. Field types are known; undocumented business enums and accounting conventions still require the team's confirmation. No automatic retry/idempotency contract is implemented for financial POSTs.
