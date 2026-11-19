# Design & Impact Analysis: eiae-sdk Quota Refund / Compensation API (#2226)

- **Issue:** [Teclavya/teclavya#2226](https://github.com/Teclavya/teclavya/issues/2226)
- **Skills used:** `ponytail`, `grill-me`

## 1. Architectural Alternatives (`ponytail`)
| Option | Approach | Verdict |
| :--- | :--- | :--- |
| **Option A (Selected)** | Synchronous best-effort compensation (`consumeQuota` before `proceed()`, `refundQuota` on provider-side `catch`) using existing `idempotencyKey` | **Selected** — zero new infra/queues, preserves pre-execution quota gate, idempotent against retries |
| **Option B** | Two-phase reserve/commit (`reserveQuota` -> `commitQuota` / `releaseQuota`) with TTL expiry | Over-engineered for current Redis/Postgres counter schema; requires changing every existing `consume` Lua script |
| **Option C** | Consume quota *after* `proceed()` succeeds | Vulnerable to concurrent stampede bypassing quota limits during slow LLM calls |

## 2. Contract & Impact Analysis
- **API (`eiae-platform-service`):** Additive endpoints `POST /api/v1/quota/refund` and `POST /api/v1/quota/refund-by-external-id` in `QuotaController.java`. Zero breaking changes to `/consume` or `/check`.
- **Database:** Uses existing `UserQuotaCounter` table (`consumed = GREATEST(0, consumed - amount)`). No Flyway migration required unless a persistent refund-idempotency table is desired for Postgres-fallback mode (Redis stores `refund:<idempotencyKey>` with period TTL).
- **SDK (`eiae-sdk-java`):** Additive method `refundQuota(...)` on `EntitlementService` (can provide a `default` method if backward compatibility with external mocks is needed, implemented in `EntitlementServiceImpl`).
- **Blast Radius:** `QuotaController.java`, `QuotaService.java`, `EntitlementService.java`, `EntitlementServiceImpl.java`, `RequiresQuotaAspect.java`, plus provider-exception marker in `learning-path` (`503 AI_PROVIDERS_UNAVAILABLE`).
