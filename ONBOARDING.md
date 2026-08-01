# Onboarding — `eiae-sdk-java`

> **Goal of this doc:** get you from `git clone` to a **reviewed pull request on your first day**. Read it top-to-bottom once (~15 min), do the "Day-1 first PR" at the end, and you'll understand how this library works.
>
> **This is a LIBRARY, not a service** — no server to run, no port. It's the **EIAE Java SDK**: a Spring Boot auto-configuring JAR that other backend services depend on to enforce **entitlements** (does this user have feature X?) and **quotas** (consume N units of feature X) via two annotations — `@RequiresEntitlement` and `@RequiresQuota`. It calls the EIAE platform service over HTTP, caches with Caffeine, and invalidates on Redis plan-change events.

**Stack in one line:** Java 17, Spring Boot 3.5 (all Spring deps `optional` — the consumer brings its own), AOP aspects, Caffeine cache, RestClient, JUnit 5 + JaCoCo 85% gate. Built via Maven → published to GitHub Packages.

---

## 1. The setup (there's nothing to "run")

**Prerequisites:** JDK **17** + a system **Maven** (there is **no `mvnw` wrapper**), and — because the pom's `<repositories>` points at GitHub Packages — a `~/.m2/settings.xml` with a `<server id="github-teclavya">` PAT that has `read:packages`, or even a plain build fails resolving deps.

**Build + test it (the real gate — note `verify`, not `test`):**
```bash
mvn verify        # surefire tests + JaCoCo 85% line-coverage check (bound to the verify phase)
mvn install       # put the jar in your local ~/.m2 so consumer builds resolve it
```
> `mvn test` runs the unit tests but **skips the 85% coverage gate** — always use `mvn verify` as the real gate.

**Publishing** is CI-driven, not manual: `.github/workflows/publish.yml` runs `mvn -B deploy` on a git tag `v*.*.*`. A hand publish would need a `write:packages` PAT as `<server id="github">` in `~/.m2/settings.xml`. **GitHub Packages is immutable per version — you cannot re-deploy `1.1.0`.**

---

## 2. How to think about this library (mental model)

```
A consumer service annotates a method:
   @RequiresQuota(value = "mock_interviews", amount = 1)   // or @RequiresEntitlement("mock_interviews")
        │  (AOP @Around)
   RequiresQuotaAspect → EiaeUserIdResolver (who is the caller?) → EntitlementService.consumeQuota(...)
        │                                                              │ Caffeine cache (TTL 60s) + RestClient
        │                                                              ▼
        │                                        POST {eiae.api-base}/api/v1/quota/consume-by-external-id
        │                                                (X-Internal-Token header)
        ▼
   result.isAllowed()? proceed  :  throw QuotaExceededException  → consumer maps to HTTP 429
   (@RequiresEntitlement denial → EntitlementMissingException → HTTP 403)
```

Three things to internalize:
1. **It's auto-configured, and only in a web app with AspectJ.** `EiaeAutoConfiguration` (`@ConditionalOnWebApplication`) registers the service, the two aspects (need AspectJ on the classpath), and the Redis plan-change subscriber (needs spring-data-redis). A non-web consumer, or one missing AOP, silently gets **no enforcement**.
2. **Identity is resolved as a per-user UUID.** `EiaeUserIdResolver` looks for (1) an HTTP request attribute `userId` (the platform UUID your auth filter stashes), else (2) the Security principal *only if it parses as a UUID*, else (3) the literal `"anonymous"`. EIAE keys everything on that UUID.
3. **It fails OPEN by default.** Any EIAE outage/5xx/timeout → the call is ALLOWED (`fail-open-on-error=true`). Great for availability, but it means a broken EIAE = unlimited quota, and — critically — a **service-to-service call with no end-user UUID resolves to `"anonymous"`, `UUID.fromString` throws, and quota is silently NOT enforced** (see §8). `@RequiresEntitlement` is the asymmetric exception: `"anonymous"` fails *closed* (403).

---

## 3. The public API (consumer-facing surface)

Package `com.teclavya.eiae.sdk`:
- **`@RequiresQuota`** — method annotation: `value()` (feature key), `amount() default 1`, `idempotencyKeyHeader() default "X-Idempotency-Key"`. Consumes quota.
- **`@RequiresEntitlement`** — method annotation: `value()` (feature key). Boolean has-feature check, no consumption.
- **`RequiresQuotaAspect` / `RequiresEntitlementAspect`** — the AOP enforcement; throw `QuotaExceededException` / `EntitlementMissingException` on denial.
- **`EntitlementService`** (interface) + `EntitlementServiceImpl` — `has`, `consumeQuota`, `getSnapshot`, `invalidateCache`; Caffeine cache + RestClient; hits `POST /api/v1/quota/consume-by-external-id` and `GET /api/v1/entitlements/by-external-id/{externalId}` with an `X-Internal-Token` header.
- **`EiaeProperties`** (`eiae.*`): `api-base`, `internal-token` (`EIAE_INTERNAL_TOKEN`), `cache-ttl-seconds` (60), `local-override` (`EIAE_LOCAL_OVERRIDE=true` → always allow, no HTTP — for local dev), `enforce-entitlements` (alias that *wins* and inverts `local-override`), `user-id-request-attribute` (`userId`), `fail-open-on-error` (default **true**).
- **Registration:** Spring Boot 3 style — `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (no legacy `spring.factories`).

---

## 4. Directory layout (`src/main/java/com/teclavya/eiae/sdk/`)

| File(s) | What it is |
|---|---|
| `RequiresQuota.java`, `RequiresEntitlement.java` | The two public method annotations |
| `RequiresQuotaAspect.java`, `RequiresEntitlementAspect.java` | AOP enforcement — throw on denial |
| `EntitlementService.java` + `EntitlementServiceImpl.java` | Service interface + RestClient/Caffeine impl (fail-open/closed logic) |
| `EiaeUserIdResolver.java` | Caller-UUID resolution (request-attr → UUID principal → "anonymous") |
| `EiaeAutoConfiguration.java`, `EiaeProperties.java` | Spring Boot auto-config + `eiae.*` config |
| `EntitlementsSnapshot.java`, `QuotaResult.java`, exceptions, wire DTOs, `PlanChangeSubscriber.java` | Records, `QuotaExceeded`/`EntitlementMissing` exceptions, EIAE wire DTOs, Redis cache-invalidation listener |

Tests mirror these under `src/test/java/...`.

---

## 5. How a consumer uses it end-to-end

1. Add `com.teclavya:eiae-sdk-java:1.1.0` + the GitHub Packages `<repository>` + a `read:packages` PAT in `settings.xml`.
2. Set `eiae.api-base` and `eiae.internal-token` (env `EIAE_INTERNAL_TOKEN`) in `application.yml`; locally set `EIAE_LOCAL_OVERRIDE=true`.
3. Ensure the app is a **web app with AspectJ**, and the auth filter stashes the platform UUID as request attribute `userId`.
4. Annotate a method: `@RequiresQuota(value = "mock_interviews", amount = 1)`.
5. **Runtime:** on denial the aspect throws `QuotaExceededException` — the consumer maps it via `@ExceptionHandler` to **429** (with `Retry-After` from `periodResetsAt`); a missing entitlement → **403**. An `IDEMPOTENT` replay maps to ALLOWED (do not throw).

---

## 6. Testing & the real gate

- **JUnit 5 + Mockito + Spring `MockRestServiceServer`.** The standout is `EntitlementServiceImplConsumeQuotaContractTest` — a real wire-contract test binding `MockRestServiceServer` to the *same* `RestClient.Builder` production uses (URL/headers/body/status, fail-open vs fail-closed, IDEMPOTENT→ALLOWED).
- **The real gate is `mvn verify`** (JaCoCo ≥ **85%** line at the `verify` phase; `mvn test` skips it).
- ⚠️ **False-green trap:** the PR template says `mvn verify -Dgroups="p0,p1"`, but **no test here uses `@Tag`** and the pom has no `groups` config — so `-Dgroups` matches **zero tests** and reports success. Use **plain `mvn verify`**. (There are no `*IT`/failsafe tests, so nothing else is silently skipped.)
- **Target branch: `development`** (publishing is tag-driven, independent of branch).

---

## 7. Your Day-1 first PR (pick one)

All add tests/docs only — **do not touch the annotation contract or the aspect logic** (any change is breaking for every consumer):

1. **Add a `PlanChangeSubscriber` test** for an empty/whitespace-only Redis message (assert an all-commas/blank body invalidates zero users) — covers the `filter(s -> !s.isEmpty())` path and helps the 85% gate.
2. **Add a test + `@DisplayName` for the `enforce-entitlements` inversion** in `EiaeProperties.isLocalOverride()` — assert `enforceEntitlements=true ⇒ isLocalOverride()==false`, and that it overrides an explicit `localOverride=true`. Config clarification, no behavior change.
3. **A doc note / README stub** documenting `mvn verify` as the real command (the PR template's `-Dgroups` form doesn't apply here).

### The workflow
```bash
git checkout development && git pull
git checkout -b feature/<your-short-name>
# ...add your test...
mvn verify        # the real gate (not -Dgroups; coverage must stay >= 0.85)
git add -p && git commit -m "test: cover PlanChangeSubscriber empty-message path"
git push -u origin feature/<your-short-name>
# open a PR into `development`, request review
```
**Definition of done:** `mvn verify` green (coverage ≥ 0.85).

---

## 8. Landmines (things that will waste your afternoon)

| Landmine | What happens | What to do |
|---|---|---|
| **s2s quota fails OPEN** | No end-user UUID → resolver returns `"anonymous"` → `UUID.fromString` throws → fail-open ALLOWS → quota silently unenforced | Inject a real (synthetic per-service) UUID for s2s callers, or accept unmetered quota. `@RequiresEntitlement` instead fails CLOSED (403) |
| **Fail-open by default** | EIAE outage → unlimited quota | Set `eiae.fail-open-on-error=false` where strict enforcement matters |
| **Auto-config conditions** | Non-web app, or missing AspectJ/Redis → beans silently don't wire → no enforcement | Ensure web + AspectJ (+ spring-data-redis for plan-change) |
| **`mvn test` skips coverage** | Green locally, red at `verify` | Always `mvn verify` |
| **`-Dgroups="p0,p1"` runs 0 tests** | Green with nothing executed (no `@Tag`) | Plain `mvn verify` |
| **Immutable published versions** | Can't re-deploy a version; every change needs a new version + coordinated consumer bump | Bump deliberately; API/aspect changes are breaking for all consumers |
| **GitHub Packages auth for BUILD** | Plain `mvn install` 401s resolving deps without a `read:packages` PAT | Configure `settings.xml` first |
| **Idempotency key in scheduled tasks** | No servlet request → a random key per call → retries don't dedupe | Provide a stable key for non-HTTP callers |

---

## 9. Where to go next
- **The code** (there's no README — ground onboarding in the classes + javadocs).
- The workspace **`../CLAUDE.md`** governance; see also the sibling **`teclavya-platform-security`** SDK (SecretGuard/MeshHmacKeys) many of the same consumers use.
- **Ask.** Your onboarding buddy covers the last 20%.

---

_Found something wrong or out of date here? Fix it in the same PR as the code that changed it — this doc is only useful if it stays true._
