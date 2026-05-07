# 10 · Contract → Implementation Walkthrough

> **Goal:** Trace a single transfer flow end-to-end through every layer.
> **Scenario:** A user in Cambodia scans a KHQR code and submits a transfer.
> **Cast:** `:features` ViewModel ↔ `:core` interface ↔ `:tenants:tenants-kh` implementation ↔ `:app` DI wiring.

This is the most concrete answer to the question "how does Nexus actually work?"

---

## 1. The Layers, Top-Down

```
USER ACTION (scan QR + tap Submit)
    │
    ▼
[ :features ]
    TransferInputScreen   (Compose)
        │
        ▼
    TransferInputViewModel  ──── consumes ────▶  TransferRepository (interface)
                                                  TransferAmountPolicy (interface)
                                                          ▲
                                                          │ provided at runtime by
[ :core ]                                                 │
    TransferRepository, TransferIntent, Money, …          │
                                                          │
[ :tenants:tenants-kh ]                                   │
    BakongTransferRepo   ─────── implements ──────────────┘
        │
        ▼
    BakongApi (Retrofit)  ─── HTTPS ───▶  api.staging.kh.nexus.bank/transfer
        │
        ▼
    DTO → domain mapper

[ :app ]
    KhTenantModule binds BakongTransferRepo as TransferRepository inside TenantComponent
    TenantSwitcher built TenantComponent at login (tenant = KH)
```

Walk it from top to bottom for execution order; from bottom to top for dependency order.

---

## 2. The Contract (`:core`)

```kotlin
// :core/src/main/kotlin/com/nexus/core/repository/TransferRepository.kt
package com.nexus.core.repository

interface TransferRepository {
    suspend fun resolveBeneficiary(qrPayload: String): Result<Beneficiary>
    suspend fun submit(intent: TransferIntent): Result<TransferReceipt>
}

// :core/src/main/kotlin/com/nexus/core/model/TransferIntent.kt
data class TransferIntent(
    val source: AccountId,
    val beneficiary: Beneficiary,
    val amount: Money,
    val narrative: String?,
)

// :core/src/main/kotlin/com/nexus/core/tenant/policy/TransferAmountPolicy.kt
interface TransferAmountPolicy {
    fun validate(amount: Money): ValidationResult
    val dailyLimit: Money
}
```

These are pure Kotlin — no Android, no Retrofit, no Bakong-specific types. **Both `:features` and `:tenants:tenants-kh` agree on this shape.**

---

## 3. The UI Layer (`:features`)

### 3.1 The Contract co-located with the screen

```kotlin
// :features/src/main/kotlin/com/nexus/features/transfer/input/TransferInputContract.kt
package com.nexus.features.transfer.input

import com.nexus.core.mvi.UiState
import com.nexus.core.mvi.UiEvent
import com.nexus.core.mvi.UiEffect

internal data class TransferInputState(
    val amount: String = "",
    val beneficiary: Beneficiary? = null,
    val validation: ValidationState = ValidationState.Idle,
    val isSubmitting: Boolean = false,
) : UiState

internal sealed interface TransferInputEvent : UiEvent {
    data class AmountChanged(val raw: String) : TransferInputEvent
    data class QrScanned(val payload: String) : TransferInputEvent
    object SubmitClicked : TransferInputEvent
}

internal sealed interface TransferInputEffect : UiEffect {
    data class NavigateToReview(val intent: TransferIntent) : TransferInputEffect
    data class ShowError(val message: String) : TransferInputEffect
}
```

### 3.2 The ViewModel

```kotlin
// :features/src/main/kotlin/com/nexus/features/transfer/input/TransferInputViewModel.kt
@HiltViewModel
internal class TransferInputViewModel @Inject constructor(
    private val tenantEntryPoint: TenantEntryPointAccessor,
    private val tenantContext: StateFlow<TenantContext>,
) : MviViewModel<TransferInputState, TransferInputEvent, TransferInputEffect>(
    initial = TransferInputState()
) {

    private val transferRepo:  TransferRepository      get() = tenantEntryPoint.current().transferRepository()
    private val amountPolicy:  TransferAmountPolicy    get() = tenantEntryPoint.current().transferAmountPolicy()

    override fun onEvent(event: TransferInputEvent) {
        when (event) {
            is TransferInputEvent.AmountChanged -> onAmountChanged(event.raw)
            is TransferInputEvent.QrScanned     -> resolveBeneficiary(event.payload)
            TransferInputEvent.SubmitClicked    -> submit()
        }
    }

    private fun onAmountChanged(raw: String) {
        val parsed = Money.parseOrNull(raw, currency = tenantContext.value.defaultCurrency)
        val validation = parsed?.let(amountPolicy::validate) ?: ValidationState.Idle
        setState { copy(amount = raw, validation = validation) }
    }

    private fun resolveBeneficiary(payload: String) = viewModelScope.launch {
        transferRepo.resolveBeneficiary(payload)
            .onSuccess { setState { copy(beneficiary = it) } }
            .onFailure { emitEffect(TransferInputEffect.ShowError(it.userMessage())) }
    }

    private fun submit() = viewModelScope.launch {
        val intent = currentIntent() ?: return@launch
        setState { copy(isSubmitting = true) }
        transferRepo.submit(intent)
            .onSuccess { emitEffect(TransferInputEffect.NavigateToReview(intent)) }
            .onFailure { emitEffect(TransferInputEffect.ShowError(it.userMessage())) }
        setState { copy(isSubmitting = false) }
    }

    private fun currentIntent(): TransferIntent? = with(state.value) {
        TransferIntent(
            source       = currentAccountId(),
            beneficiary  = beneficiary ?: return null,
            amount       = Money.parseOrNull(amount, tenantContext.value.defaultCurrency) ?: return null,
            narrative    = null,
        )
    }
}
```

**Note what is and isn't here:**

- ✅ Imports `:core` types only: `TransferRepository`, `TransferIntent`, `TransferAmountPolicy`, `Money`.
- ✅ Resolves the repository through `TenantEntryPointAccessor` so it picks up the **active** tenant's binding.
- ❌ No import of `BakongTransferRepo`, `CambodiaApi`, or anything starting with `com.nexus.tenants.*`.
- ❌ No `if (tenantContext.id == TenantId.KH)`. The tenant ID is read only for `defaultCurrency` (a display concern).

### 3.3 The Screen

```kotlin
@Composable
internal fun TransferInputScreen(
    viewModel: TransferInputViewModel = hiltViewModel(),
    onNavigateToReview: (TransferIntent) -> Unit,
    onShowError: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransferInputEffect.NavigateToReview -> onNavigateToReview(effect.intent)
                is TransferInputEffect.ShowError        -> onShowError(effect.message)
            }
        }
    }

    TransferInputContent(state = state, onEvent = viewModel::onEvent)
}
```

The screen knows nothing about tenants. It would render identically for KH, VN, or PPCBank — the only differences (currency formatting, copy strings) are reads off `TenantContext`.

---

## 4. The Implementation (`:tenants:tenants-kh`)

### 4.1 The Retrofit interface

```kotlin
// :tenants:tenants-kh/src/main/kotlin/com/nexus/tenants/kh/api/BakongApi.kt
internal interface BakongApi {
    @POST("v3/qr/resolve")
    suspend fun resolveQr(@Body req: BakongQrRequest): BakongQrResponse

    @POST("v3/transfer/submit")
    suspend fun submit(@Body req: BakongTransferRequest): BakongTransferResponse
}
```

### 4.2 The repository implementation

```kotlin
// :tenants:tenants-kh/src/main/kotlin/com/nexus/tenants/kh/repo/BakongTransferRepo.kt
internal class BakongTransferRepo @Inject constructor(
    private val api: BakongApi,
    private val tenantContext: TenantContext,
) : TransferRepository {

    override suspend fun resolveBeneficiary(qrPayload: String): Result<Beneficiary> = runCatching {
        val response = api.resolveQr(BakongQrRequest(qrPayload, tenantContext.marketCode))
        response.toBeneficiary()
    }

    override suspend fun submit(intent: TransferIntent): Result<TransferReceipt> = runCatching {
        val request = BakongTransferRequest.from(intent)
        val response = api.submit(request)
        response.toReceipt()
    }
}
```

The repo is `internal` — `:app` cannot reference its concrete name. It is wired only via the `:core` interface.

### 4.3 The tenant policy

```kotlin
// :tenants:tenants-kh/src/main/kotlin/com/nexus/tenants/kh/policy/KhTransferAmountPolicy.kt
internal class KhTransferAmountPolicy @Inject constructor() : TransferAmountPolicy {

    override val dailyLimit: Money = Money(amount = BigDecimal("40000000.00"), Currency.KHR)

    override fun validate(amount: Money): ValidationResult = when {
        amount.value <= BigDecimal.ZERO        -> ValidationResult.Invalid("Amount must be positive")
        amount > Money(BigDecimal("4000.00"), Currency.USD) -> ValidationResult.Invalid("Above NBC threshold")
        else                                   -> ValidationResult.Valid
    }
}
```

**This is where the bank-specific business rule lives.** A different bank with a different limit drops a different `TransferAmountPolicy` impl in its own tenant module — the ViewModel never changes.

### 4.4 The DI module (the only public symbol)

```kotlin
// :tenants:tenants-kh/src/main/kotlin/com/nexus/tenants/kh/di/KhTenantModule.kt
@Module
@InstallIn(TenantComponent::class)
abstract class KhTenantModule {

    @Binds
    abstract fun bindTransferRepo(impl: BakongTransferRepo): TransferRepository

    @Binds
    abstract fun bindAmountPolicy(impl: KhTransferAmountPolicy): TransferAmountPolicy

    companion object {
        @Provides
        fun bakongApi(
            retrofitFactory: RetrofitFactory,
            tenantContext: TenantContext,
        ): BakongApi = retrofitFactory
            .builderForTenant(tenantContext)
            .build()
            .create(BakongApi::class.java)
    }
}
```

The module's annotations declare the contract: *"these bindings are valid as long as the active tenant is KH."* When the tenant switches, this whole module's contributions evaporate with the destroyed `TenantComponent`.

---

## 5. The Wiring (`:app`)

`:app` does two things to make the example work:

### 5.1 Register the tenant module with the component manager

The Hilt `@InstallIn(TenantComponent::class)` annotation is the only registration needed — Hilt's annotation processor discovers all such modules at compile time. **No central registry is needed in `:app`.**

### 5.2 Build the component when the user picks a tenant

```kotlin
// At login (or tenant switch):
suspend fun onUserSelectedTenant(target: TenantId) {
    tenantSwitcher.switchTo(target)
    // After this returns: TenantComponent contains KhTenantModule's bindings,
    // and TenantEntryPointAccessor.current() resolves them.
}
```

That's it. No `if (target == KH)`, no per-tenant code paths in `:app`.

---

## 6. The Request Flow

Tracing one `submit()` call from tap to network:

```
1. User taps Submit                                              (UI thread)
   └─▶ TransferInputContent calls onEvent(TransferInputEvent.SubmitClicked)

2. ViewModel.submit() launches a coroutine                        (viewModelScope)
   └─▶ tenantEntryPoint.current() returns the live TenantComponent

3. .transferRepository() returns BakongTransferRepo                (via :core interface)
   └─▶ ViewModel calls repo.submit(intent)

4. BakongTransferRepo translates TransferIntent → BakongTransferRequest
   └─▶ Calls api.submit(request)

5. Retrofit serializes & dispatches                                 (OkHttp)
   └─▶ EnvironmentInterceptor rewrites URL to current env's KH base URL
   └─▶ AuthHeaderInterceptor attaches the active session token from EncryptedPrefs
   └─▶ TLS handshake using the pinned cert for (env, KH)

6. Server responds → BakongTransferResponse                         (network)
   └─▶ Repo maps response.toReceipt() back to :core's TransferReceipt

7. ViewModel emits TransferInputEffect.NavigateToReview              (effect channel)
   └─▶ Screen's LaunchedEffect picks it up → onNavigateToReview(intent)

8. AppNavigation pushes the review route                            (UI thread)
```

Every step is testable in isolation. Steps 4-5 swap entirely when the tenant is VN (NapasTransferRepo, VietnamApi). Steps 1-3 and 7-8 don't care.

---

## 7. The Same Walkthrough for `tenants-vn`

Adding VN means writing exactly:

```kotlin
internal class NapasTransferRepo @Inject constructor(
    private val api: VietnamApi,
    private val tenantContext: TenantContext,
) : TransferRepository {
    override suspend fun submit(intent: TransferIntent): Result<TransferReceipt> = runCatching {
        api.submit(NapasRequest.from(intent)).toReceipt()
    }
    // …
}

internal class VnTransferAmountPolicy : TransferAmountPolicy { … }

@Module @InstallIn(TenantComponent::class)
abstract class VnTenantModule {
    @Binds abstract fun bindTransfer(impl: NapasTransferRepo): TransferRepository
    @Binds abstract fun bindPolicy(impl: VnTransferAmountPolicy): TransferAmountPolicy
    companion object { @Provides fun api(...) = ... }
}
```

`:features.TransferInputViewModel` — and every other ViewModel — is **not modified**. That is the architectural promise made operational.

---

## 8. Cross-references

- The runtime mechanism that makes `tenantEntryPoint.current()` correct: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- How a new tenant onboards: [11 — Onboarding a New Tenant](11-onboarding-new-tenant.md)
- The MVI conventions used above: [07 — MVI Pattern](07-mvi-pattern.md)
