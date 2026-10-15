# Financial engine integration and behavior changes

The initial source-only download was incomplete. The later `current_deployment/app.jar` contains the required vendor library. The application now compiles and packages the exact nested JAR, recorded in [vendor provenance](../libs/README.md). There is no missing-engine fallback or optional Gradle/runtime profile. All financial routes use the recovered engine in both local and server configurations.

## What was preserved

- The original route names and nested `account` financial request structure.
- Vendor `cash`, `trade`, `stock`, `accountAsset`, process-date rollover, `addCash`/`addTrade`/`addStock`, and `outDemoJson()` behavior.
- The vendor `jsonUtil` format for stored books, including internal structures such as `acctProfile`; the demo response is not persisted as the complete book.
- The original local `AccountAsset`, Cash/Trade/Stock and other DTOs, without switching financial routes to those incompatible models.

The loose `current_deployment/regulus-bo18-0.0.0-dev.jar` differs from the nested deployed artifact. `libs` contains the nested version because the executable's `JarLauncher` uses that classpath. Do not replace it merely because the names match. The build verifies its checksum; coordinate deliberate vendor upgrades with the owning team.

## What changed and why

The vendor SQL methods were inspected in bytecode. They build queries from concatenated account IDs and JSON strings; some exceptions are printed and suppressed. This could corrupt quoted descriptions, allow SQL injection and return success after a failed write. `RegulusBoAssetService` instead loads/saves through `AccountBookRepository`, which uses JDBC bound parameters. It still serializes and calculates with the real vendor classes. Tests include apostrophes in account IDs and descriptions.

Spring owns the connection lifecycle and transaction. Financial service operations run at serializable isolation and roll back on exceptions; a JVM-wide lock guards the vendor static serialization state used by this service. This is not an idempotency mechanism. Concurrent database conflicts/deadlocks may return an error, and SQL Server-specific locking/index behavior still requires testing against production DDL. Multiple applications writing the same book must participate in equivalent concurrency controls.

The original missing-book path depended on constructor dates and could report an empty default identity. New books initialize at the supplied transaction date; a missing GET returns 404. Backdated financial POSTs return 409 instead of mutating the latest book at an older date. A forward date calls the vendor's existing end/start methods and saves the new dated snapshot. Stored malformed or mismatched books fail rather than being silently replaced. These are deliberate behavior changes requiring acceptance testing.

All routes now require HTTP Basic credentials. Malformed JSON/key/date input returns 400, vendor `BOException` returns a sanitized 400, expected conflicts 409 and unexpected errors 500. SQL/internal details are logged server-side rather than returned to clients. Authentication errors are 401/403. No automatic retries or duplicate-request guarantee is provided. Account master creation remains separate from financial books, consistent with the original financial route behavior.

## Contract and acceptance limits

OpenAPI includes public/inherited fields from the pinned vendor `cash`, `trade`, `stock` and `demoAsset` classes. `./gradlew generateVendorSchemas` can regenerate the field/type definitions to `build/vendor-schemas.json`. Required `account.firmId`, `account.accountId` and `processDate` come from API validation. Other omitted fields use vendor defaults; business enums, signs, rounding, tax/margin conventions and control-ID semantics are not invented from names.

The tests prove the recovered binary executes basic cash/trade/stock operations, round-trips full book JSON and performs date rollover using H2. They do not certify all financial results or the live SQL Server schema. The existing `examples/legacy-asset.json` is a supplied demo response, not a full persistence fixture. Compare representative real books, negative/short trades, cash directions, taxlots, margin, settlement dates, control IDs and concurrent writes with the owner's acceptance cases before release.

Neither the original Gradle source nor a vendor dependency POM/license was supplied. The required local binary is pinned; obtain authoritative source/dependency metadata and a private artifact repository from the team for long-term maintenance.
