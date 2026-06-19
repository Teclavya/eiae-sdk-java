# eiae-sdk-java

## Purpose
Published Java client library for the EIAE platform service (`eiae-platform-service`). Consuming Spring Boot services depend on this JAR to enforce entitlements and quotas: the `@RequiresEntitlement` / `@RequiresQuota` AOP annotations and the `EntitlementService` API check a Caffeine-cached entitlement snapshot, fetching it over HTTP (`RestClient`) from EIAE when not cached. Ships as a Spring Boot auto-configuration (`EiaeAutoConfiguration`) that wires beans automatically when the JAR is on the classpath — no main class, this is a library, not a runnable app.

## Stack
Java 17 + Spring Boot 3.5 auto-configuration (Maven, packaged as a JAR). Caffeine cache, optional Spring Web/AOP/Security/Data-Redis/WebSocket, Lombok. Published to GitHub Packages as `com.teclavya:eiae-sdk-java`.

## Commands
- **Build / install to local repo:** `mvn -q clean install`
- **Build (skip tests):** `mvn -q clean install -DskipTests`
- **Test:** `mvn test`
- **Verify (runs JaCoCo 85% line-coverage gate):** `mvn verify`
- **Publish (CI only, on `v*.*.*` tag):** `mvn -B deploy` — see `.github/workflows/publish.yml`

> This is a library with no entry point — there is no `run` target. Consumers add it as a dependency and rely on auto-configuration.

## Conventions
- Public API = everything in package `com.teclavya.eiae.sdk` that is `public`: the `EntitlementService` interface, the `@RequiresEntitlement` / `@RequiresQuota` annotations, the `EiaeProperties` keys, and the `QuotaResult` / `EntitlementsSnapshot` records / exception types. Treat these as a stable contract for downstream services.
- Configuration is property-driven, not builder-driven: all knobs live in `EiaeProperties` bound under the `eiae.*` prefix (`eiae.api-base`, `eiae.cache-ttl-seconds`, `eiae.internal-token`, `eiae.local-override` / `eiae.enforce-entitlements`). Add new config as a documented field here, keep backward-compatible aliases (see how `enforceEntitlements` inverts `localOverride`).
- Auto-wiring is conditional: register beans in `EiaeAutoConfiguration` with `@ConditionalOnMissingBean` (so consumers can override) and `@ConditionalOnClass` for anything needing an optional dependency (AOP, Redis). New optional integrations must follow this opt-in pattern, never hard-require the dependency.
- DTOs crossing the wire to EIAE are immutable Java `record`s (`QuotaResult`, `EntitlementsSnapshot`, `QuotaInfo`); keep request/response shapes aligned with `eiae-platform-service` endpoints (`GET /v1/entitlements/{userId}`, `POST /v1/entitlements/consume-quota`).
- Error handling is resilient by design: fetch failures return an empty snapshot (fail-safe, no features granted), quota failures return `DENIED` (fail-open guard against over-consumption). Log via SLF4J with the `[EIAE SDK]` prefix; do not let SDK exceptions propagate into consumer request threads.
- `local-override` / `enforce-entitlements=false` short-circuits all checks to allow — preserve this dev escape hatch when changing `EntitlementServiceImpl`.
- Tests are Mockito-based unit tests (`*Test.java` under `src/test`), each covering one component (service, each aspect, the Redis subscriber); the build enforces 85% line coverage via JaCoCo, so new public behavior needs tests.

## Boundaries / Do-not-touch
- The public API is a published contract consumed by other services. Any breaking change (method signature, annotation attribute, `eiae.*` property rename, DTO field) requires a version bump in `pom.xml` per semver — do not silently alter or remove public members.
- Do not leak internals into the public surface: `EntitlementServiceImpl`, the package-private test constructor, the `ConsumeQuotaRequest` record, and helper methods are implementation details. Keep new internals package-private/private; only widen visibility deliberately.
- Keep the dependency footprint light. Every Spring Boot integration (web, aop, security, redis, websocket) is declared `optional`/`provided` so consumers bring their own — do not add heavy or non-optional transitive dependencies that would be forced onto every downstream service.
- Do not change the wire contract unilaterally (endpoint paths, headers like `X-Internal-Token` / `X-Idempotency-Key`, DTO shapes) — these are shared with `eiae-platform-service` and must change in lockstep with it.
- Do not edit `pom.xml` coordinates (`groupId`/`artifactId`), the `distributionManagement` / GitHub Packages config, or `.github/workflows/publish.yml` without coordinating the release process; bump only `version` for releases.

## Pointers
- Auto-config registration: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Entry-point types: `EntitlementService`, `RequiresEntitlement`, `RequiresQuota`, `EiaeProperties`
- _no README yet_

<!-- sdlc-global-standard -->
## SDLC Standard (global)
This project follows the machine-wide SDLC standard in `~/.claude/commands/sdlc.md` and the templates in `~/.claude/templates/` (auto-synced into `specs/_templates/` by the SessionStart hook). See `~/.claude/CLAUDE.md` §7 — global is the hub: learnings proven here are promoted to global and adopted by all projects. Reference the standard; don't fork it.
