# Specification: eiae-sdk Quota Refund / Compensation API (#2226)

- **Issue:** [Teclavya/teclavya#2226](https://github.com/Teclavya/teclavya/issues/2226) (split from `#2216`)
- **Repos Affected:** `eiae-sdk-java`, `eiae-platform-service`, consuming services (`learning-path`, `resumebuilderserv`)
- **Skills used:** `grill-me`, `ponytail`

## Requirements (`REQ-01`..`REQ-05`)
- **REQ-01 (#2226):** `eiae-platform-service` exposes `POST /api/v1/quota/refund` and `POST /api/v1/quota/refund-by-external-id` (protected by `X-Internal-Token`), atomically decrementing consumed quota in Redis (with PostgreSQL `UserQuotaCounter` fallback), clamped at `0`, and idempotent on `idempotencyKey` (so duplicate refund calls never double-credit).
- **REQ-02 (#2226):** `eiae-sdk-java` `EntitlementService` adds `QuotaResult refundQuota(String userId, String feature, int amount, String idempotencyKey)` and `EntitlementServiceImpl` implements it against `/api/v1/quota/refund-by-external-id`, invalidating the local Caffeine snapshot cache on success and honoring `localOverride`.
- **REQ-03 (#2226):** `RequiresQuotaAspect` catches provider-side / transient failures thrown by `pjp.proceed()` (e.g., `503 AI_PROVIDERS_UNAVAILABLE`, `QuotaRefundableException`, `HttpServerErrorException` / `ResponseStatusException` with 5xx) and invokes `refundQuota(userId, feature, amount, idempotencyKey)` best-effort.
- **REQ-04 (#2226):** Refund failures (e.g., network error to EIAE) are logged with `userId`, `feature`, `idempotencyKey` and **never** mask or replace the original business/provider exception.
- **REQ-05 (#2226):** Client validation errors (`4xx`, `IllegalArgumentException`) do NOT trigger automatic quota refunds unless explicitly annotated/marked as provider-side failures.
