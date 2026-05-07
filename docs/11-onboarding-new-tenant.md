# 11 · Onboarding a New Tenant

> **Promise:** Adding a new tenant requires **zero changes to `:features`**.
> **Scope of changes:** one new tenant module + one DI registration line + per-environment manifest entries.
> **Worked example below:** onboarding **`tenants-my`** (Malaysia · DuitNow rail).

This document is the proof that the framework's architecture survives contact with growth. If a step in this checklist starts requiring `:features` edits, the framework has regressed — open an architecture issue.

---

## 1. Pre-Flight

Before writing code, gather:

| Artifact | Source | Why |
|---|---|---|
| API spec for the tenant's banking backend | Tenant integration team | Defines the Retrofit interface |
| Tenant-specific business rules | Compliance / product | Drives the `*Policy` implementations |
| Per-environment base URLs | Backend team | Goes into environment manifests |
| Certificate pinning fingerprints | Security team | Goes into `:aos-core` pinning registry |
| Display metadata | Brand team | Bank logo, market name, default currency |

If any of these is unavailable, do not begin onboarding. Stub data leaks into production.

---

## 2. The Onboarding Checklist

### Step 1 — Create the module

```bash
mkdir -p tenants/tenants-my/src/main/kotlin/com/nexus/tenants/my/{api,repo,policy,di}
mkdir -p tenants/tenants-my/src/main/kotlin/com/nexus/tenants/my/api/dto
```

Create `tenants/tenants-my/build.gradle.kts` mirroring an existing tenant (e.g., `tenants-kh`). Required dependencies:

- `:core` (contracts)
- `:aos-core` (RetrofitFactory, EncryptedPrefs)
- Hilt + KSP

> **Forbidden** in tenant build files: dependencies on `:features`, `:features-chatbot`, or sibling tenant modules. CI must reject these.

### Step 2 — Register in `settings.gradle.kts`

Add **one line**:

```kotlin
include(":tenants:tenants-my")
```

This is the only edit to the root build configuration.

### Step 3 — Define the Retrofit API

```kotlin
// :tenants:tenants-my/src/main/kotlin/com/nexus/tenants/my/api/DuitNowApi.kt
internal interface DuitNowApi {
    @POST("v1/qr/resolve") suspend fun resolveQr(@Body req: DuitNowQrRequest): DuitNowQrResponse
    @POST("v1/transfer")    suspend fun submit(@Body req: DuitNowTransferRequest): DuitNowTransferResponse
    // …
}
```

DTOs go under `api/dto/` — `internal` visibility, never imported outside this module.

### Step 4 — Implement repositories

```kotlin
internal class DuitNowTransferRepo @Inject constructor(
    private val api: DuitNowApi,
    private val tenantContext: TenantContext,
) : TransferRepository {
    override suspend fun submit(intent: TransferIntent): Result<TransferReceipt> = runCatching {
        api.submit(DuitNowTransferRequest.from(intent)).toReceipt()
    }
    // …
}

internal class MyAuthRepo : AuthRepository { … }
internal class MyAccountRepo : AccountRepository { … }
```

Each repository implements one or more `:core` interfaces. Mappers between DTOs and `:core` domain models live alongside the repo (or in `api/dto/` if reused).

### Step 5 — Implement tenant policies

```kotlin
internal class MyTransferAmountPolicy : TransferAmountPolicy {
    override val dailyLimit: Money = Money(BigDecimal("50000.00"), Currency.MYR)
    override fun validate(amount: Money): ValidationResult = when {
        amount.value <= BigDecimal.ZERO -> ValidationResult.Invalid("Must be positive")
        amount > dailyLimit             -> ValidationResult.Invalid("Above daily limit")
        else                            -> ValidationResult.Valid
    }
}

internal class MyFeeCalculator : FeeCalculator { … }
```

This is where regulatory and product rules live. **Do not put these in `:features`** — they're tenant-specific.

### Step 6 — Wire the Hilt module

```kotlin
@Module
@InstallIn(TenantComponent::class)
abstract class MyTenantModule {
    @Binds abstract fun transferRepo(impl: DuitNowTransferRepo): TransferRepository
    @Binds abstract fun authRepo(impl: MyAuthRepo): AuthRepository
    @Binds abstract fun accountRepo(impl: MyAccountRepo): AccountRepository
    @Binds abstract fun amountPolicy(impl: MyTransferAmountPolicy): TransferAmountPolicy
    @Binds abstract fun feeCalc(impl: MyFeeCalculator): FeeCalculator

    companion object {
        @Provides
        fun duitNowApi(
            retrofitFactory: RetrofitFactory,
            tenantContext: TenantContext,
        ): DuitNowApi = retrofitFactory
            .builderForTenant(tenantContext)
            .build()
            .create(DuitNowApi::class.java)
    }
}
```

The annotation `@InstallIn(TenantComponent::class)` is what makes Hilt pick up these bindings during `TenantComponent` rebuild. **No edit to `:app` is required to register the module** — Hilt's annotation processor finds it automatically.

### Step 7 — Register the tenant in the catalogue

```kotlin
// :app/src/main/kotlin/com/nexus/app/tenant/TenantCatalogue.kt
object TenantCatalogue {
    val all: List<TenantContext> = listOf(
        TenantContext(id = TenantId("kh"),      displayName = "PPC Bank Cambodia", marketCode = "KH", defaultCurrency = Currency.KHR),
        TenantContext(id = TenantId("vn"),      displayName = "PPC Bank Vietnam",   marketCode = "VN", defaultCurrency = Currency.VND),
        TenantContext(id = TenantId("ppcbank"), displayName = "PPCBank Legacy",     marketCode = "KH", defaultCurrency = Currency.USD),
        TenantContext(id = TenantId("my"),      displayName = "PPC Bank Malaysia",  marketCode = "MY", defaultCurrency = Currency.MYR),  // ← added
    )
}
```

This is the **one place** that must learn about the new tenant. The catalogue feeds the tenant picker UI in `:features` (which renders names from this list, not hardcoded entries).

### Step 8 — Add environment manifest entries

For **every** environment manifest in `assets/environments/`, add the new tenant:

```diff
 // assets/environments/staging.json
 {
   "environment": "Staging",
   "tenantBaseUrls": {
     "kh":       "https://api.staging.kh.nexus.bank/",
     "vn":       "https://api.staging.vn.nexus.bank/",
     "ppcbank":  "https://api.staging.ppc.nexus.bank/",
+    "my":       "https://api.staging.my.nexus.bank/"
   }
 }
```

Repeat for `production.json`, `uat.json`, `sandbox.json`.

> The framework's manifest validator rejects cold start if any registered tenant is missing a base URL in the active environment. This is intentional — silent fallbacks are dangerous in fintech.

### Step 9 — Add certificate pins

Update `assets/pinning/<env>.json` with the new tenant's per-environment pins. Coordinate the fingerprints with the security team.

### Step 10 — Add tests

Each new tenant module ships with:

- **Unit tests** for repositories (mock `BakongApi`-equivalent, assert mapping)
- **Unit tests** for each `*Policy` (golden cases for amount validation)
- **Integration tests** for `:app` running with the new tenant active (one happy-path transfer flow)

---

## 3. What You DO NOT Touch

The whole point. Verify after onboarding:

| Module | Diff line count expected |
|---|---|
| `:aos-core` | 0 (unless adding a new pinning rule mechanism) |
| `:core` | 0 (the contracts are already sufficient) |
| `:features` | **0** |
| `:features-chatbot` | 0 |
| Other `:tenants:*` | 0 |
| `:app` | ~3 lines: `TenantCatalogue` entry only |
| `settings.gradle.kts` | 1 line: `include(":tenants:tenants-my")` |
| `assets/environments/*.json` | 1 line per env |

If a PR onboarding a tenant has more than that, **review it** — something is leaking into the wrong layer.

---

## 4. Onboarding Verification Checklist

Run before merging:

```
[ ] :tenants:tenants-my compiles standalone (./gradlew :tenants:tenants-my:assembleDebug)
[ ] :features compiles without :tenants:tenants-my as a dependency (verify build.gradle.kts)
[ ] No reference to "tenants_my" or "TenantId.MY" exists in :features source set
[ ] Selecting MY in the debug picker results in DuitNowTransferRepo handling submit()
    (verifiable via TenantSwitcher.selfTest() — see [08])
[ ] Selecting MY then immediately switching to KH results in BakongTransferRepo handling submit()
[ ] All four environment manifests contain a "my" entry
[ ] All applicable pinning files contain "my" entries (or a documented exception)
[ ] Repository tests cover happy-path + at least one failure mode per method
[ ] :app size delta (release APK) reasonable (typically <500 KB for a new tenant)
```

---

## 5. When Something Doesn't Fit

Some scenarios require thought:

### "The new tenant has a feature no other tenant has."

Two options:

1. **It's a UI difference** → the `:features` package can render conditionally on **a `:core` capability flag**, not on tenant ID. Add a method to `:core`'s `TenantCapabilities` interface; provide it from each tenant module.

   ```kotlin
   // :core
   interface TenantCapabilities {
       fun supportsCardlessAtm(): Boolean
       // …
   }
   ```

2. **It's an entirely new feature** → add a new package to `:features`, gate its visibility on a capability flag, and let only the relevant tenants set the flag to `true`.

### "The new tenant uses a totally different auth mechanism."

`AuthRepository` is the contract. The interface should be flexible enough that the new tenant's flow fits. If it genuinely doesn't (e.g., needs a 4-step OAuth dance the interface doesn't model), **extend `:core`** — that's a contract evolution, and other tenants will inherit the broader surface area. Coordinate with the team.

### "The new tenant is a temporary / staging-only deployment."

Same module shape, but only the staging/UAT manifests get entries. Production manifest omits the tenant — and the catalogue gates visibility on environment.

---

## 6. Cross-references

- The runtime mechanism that makes hot-swap possible: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- The contract interfaces being implemented: [03 — `:core`](03-core.md)
- A walked-through example of an existing tenant: [10 — Contract Walkthrough](10-contract-implementation-example.md)
- Build performance implications of new modules: [12 — Build Performance](12-build-performance.md)
