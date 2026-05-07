# 05 · `:tenants:*` — Logic Silos

> **Type:** One Local Android library per tenant (`:tenants:tenants-kh`, `:tenants:tenants-vn`, `:tenants:tenants-ppcbank`, …)
> **Role:** Concrete implementations of `:core` repositories + tenant-specific business rules
> **Isolation guarantee:** No tenant module may depend on another tenant module.

---

## 1. Purpose

A tenant module is a **logic silo** — it contains everything a specific bank or market needs that differs from other tenants. By housing these differences in dedicated modules, we eliminate cross-tenant entanglement: a regression in `:tenants:tenants-vn` cannot reach `:tenants:tenants-kh`.

A tenant module owns three things:

1. **Data layer** — Retrofit APIs and repository implementations
2. **Domain layer** — tenant-specific business rules (fees, limits, validation)
3. **DI module** — the Hilt module that exposes the tenant's bindings to `:app`

It does **not** own UI. UI lives in `:features` and is identical across tenants.

---

## 2. Standard Tenant Module Shape

Every tenant module follows the same internal layout. Onboarding a new tenant means copying this shape:

```
:tenants:tenants-kh/
└── src/main/kotlin/com/nexus/tenants/kh/
    ├── api/
    │   ├── CambodiaApi.kt              # Retrofit interface
    │   ├── BakongApi.kt                # Sub-API for KHQR rail
    │   └── dto/
    │       ├── BakongTransferRequest.kt
    │       └── BakongTransferResponse.kt
    ├── repo/
    │   ├── BakongTransferRepo.kt       # implements TransferRepository
    │   ├── KhAuthRepo.kt               # implements AuthRepository
    │   └── KhAccountRepo.kt            # implements AccountRepository
    ├── policy/
    │   ├── KhTransferAmountPolicy.kt   # implements TransferAmountPolicy
    │   └── KhFeeCalculator.kt          # implements FeeCalculator
    └── di/
        └── KhTenantModule.kt           # @Module @InstallIn(TenantComponent::class)
```

| Layer | Purpose | Visibility |
|---|---|---|
| `api/` | Retrofit + DTOs unique to this tenant's backend | `internal` |
| `repo/` | Repository impls — translate DTOs to `:core` domain models | `internal` |
| `policy/` | Pluggable per-tenant rules | `internal` |
| `di/` | The single public surface — Hilt bindings exposed to `:app` | `public` |

**Only the DI module should be visible outside the tenant module.** Everything else is `internal`. This prevents `:app` from accidentally referencing a concrete repo class — it should only ever reference the `:core` interface.

---

## 3. The Tenant Catalogue

| Module | Market | Distinctive responsibilities |
|---|---|---|
| `:tenants:tenants-kh` | Cambodia | Bakong / KHQR integration, NBC compliance rules, KHR + USD dual-currency display |
| `:tenants:tenants-vn` | Vietnam | Napas / VietQR rail, VND amount formatting, SBV-aligned daily limits |
| `:tenants:tenants-ppcbank` | PPCBank legacy | Pre-existing PPC backend with non-standard envelope; bridges legacy responses to current `:core` models |

Future tenants land here as siblings: `:tenants:tenants-my`, `:tenants:tenants-th`, `:tenants:tenants-id`, etc. See [11 — Onboarding a New Tenant](11-onboarding-new-tenant.md).

---

## 4. Repository Implementation Pattern

Every tenant repo class:

1. Implements one or more `:core` interfaces.
2. Holds an injected Retrofit interface (its own private `*Api`).
3. Maps DTOs to domain models in a dedicated mapper function.
4. Wraps results in `Result<T>` — never throws across the module boundary.

```kotlin
internal class BakongTransferRepo @Inject constructor(
    private val api: BakongApi,
    private val tenantContext: TenantContext,
) : TransferRepository {

    override suspend fun submit(intent: TransferIntent): Result<TransferReceipt> = runCatching {
        val request = BakongTransferRequest.from(intent, tenantContext)
        val response = api.submitTransfer(request)
        response.toDomain()
    }

    // …
}
```

Mapping is per-tenant because DTOs differ; the resulting domain model is standardized by `:core`.

---

## 5. The Hilt Binding Module

Each tenant exposes **exactly one** Hilt module — the bridge from `:tenants:[id]` to `:app`.

```kotlin
@Module
@InstallIn(TenantComponent::class)
abstract class KhTenantModule {
    @Binds abstract fun bindTransferRepo(impl: BakongTransferRepo): TransferRepository
    @Binds abstract fun bindAuthRepo(impl: KhAuthRepo): AuthRepository
    @Binds abstract fun bindAccountRepo(impl: KhAccountRepo): AccountRepository
    @Binds abstract fun bindAmountPolicy(impl: KhTransferAmountPolicy): TransferAmountPolicy
    @Binds abstract fun bindFeeCalc(impl: KhFeeCalculator): FeeCalculator

    companion object {
        @Provides fun cambodiaApi(retrofitFactory: RetrofitFactory, tenantContext: TenantContext): CambodiaApi =
            retrofitFactory.create(tenantContext.baseUrl, CambodiaApi::class.java)

        @Provides fun bakongApi(...): BakongApi = ...
    }
}
```

`TenantComponent` is the custom `@TenantScoped` Hilt component defined in `:app`. See [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md) for the swap mechanics.

---

## 6. Isolation Guarantee

The build graph enforces:

- ❌ `:tenants:tenants-kh` cannot import `:tenants:tenants-vn` (no shared symbols across tenants)
- ❌ `:tenants:*` cannot import `:features` (logic must not reach into UI)
- ✅ `:tenants:*` may import `:core` (contracts) and `:aos-core` (infrastructure)

A bug in `BakongTransferRepo` cannot, by construction, reach `:tenants:tenants-vn`. CI verifies this by failing any PR whose tenant module declares a forbidden dependency.

---

## 7. What Does NOT Go In `:tenants:*`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Repository **interfaces** | `:core` |
| Domain models shared across tenants | `:core` |
| `OkHttpClient` configuration | `:aos-core` |
| References to other tenant modules | nowhere |

If two tenants need the same logic, **promote it to `:core`** (if it's a contract) or duplicate it deliberately (if it's an implementation detail that might diverge later). Premature consolidation across tenants is how isolation erodes.

---

## 8. The `:features-chatbot` Sibling

`:features-chatbot` is **not a tenant** — it's an isolated UI feature, sibling to `:features`. It exists because heavy SDKs (chat NLP, voice) penalize incremental builds of unrelated features. Its dependency rules:

- Depends on: `:core`, `:aos-core`
- Does **not** depend on: `:features` (no cross-imports)
- Depended on by: `:app` (via navigation entry point)

Treat `:features-chatbot` as the prototype for **"isolate when SDK weight justifies it"**. New isolated features (e.g., `:features-kyc-livecheck`, `:features-card-3ds`) follow the same pattern.

---

## 9. Cross-references

- The interfaces tenants implement: [03 — `:core`](03-core.md)
- How `:app` wires them at runtime: [06 — `:app`](06-app-orchestrator.md)
- The runtime swap mechanism: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- Onboarding a new tenant: [11 — Onboarding a New Tenant](11-onboarding-new-tenant.md)
