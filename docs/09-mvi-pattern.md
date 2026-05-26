# 09 · MVI Pattern

> **Pattern:** Model-View-Intent · strict unidirectional data flow
> **Scope:** Every screen in `:features` and `:features-chatbot`
> **Base contracts:** Defined in `:core/mvi/`

---

## 1. The Three Type Roles

Each screen defines three sealed types. Together they describe the screen's entire behaviour.

| Role | Direction | Cardinality | Lifetime | Mechanism |
|---|---|---|---|---|
| **`UiState`** | ViewModel → View | Always one current value | Cold-start to screen disposal | `StateFlow<S>` |
| **`UiEvent`** | View → ViewModel | Many over time | Each user interaction | Function call: `onEvent(...)` |
| **`UiEffect`** | ViewModel → View | Many one-shot deliveries | Per emission | `SharedFlow<F>` |

The three rules that make MVI strict:

1. **State is immutable.** A new state replaces the old; no mutation in place.
2. **State is the only thing rendered.** The UI is `@Composable fun Screen(state: UiState, …)` — pure function of state.
3. **Effects are one-shot.** Navigation, toasts, and snackbars are emitted as `UiEffect`s, not stashed in state.

---

## 2. Base Contracts (`:core/mvi/`)

```kotlin
interface UiState
interface UiEvent
interface UiEffect

abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(
    initial: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<F>(extraBufferCapacity = 16)
    val effects: SharedFlow<F> = _effects.asSharedFlow()

    abstract fun onEvent(event: E)

    protected fun setState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    protected suspend fun emitEffect(effect: F) {
        _effects.emit(effect)
    }
}
```

Notes:

- `extraBufferCapacity = 16` ensures effects emitted while no collector is attached aren't dropped during configuration changes.
- `SharedFlow` (not `Channel`) is chosen so that effects integrate with `LaunchedEffect` and replay semantics are explicit (default: `replay = 0`).

---

## 3. Per-Screen Contract Pattern

Each screen owns a `*Contract.kt` defining its three sealed types co-located with the screen:

```kotlin
// :features/loan/apply/LoanApplyContract.kt

internal data class LoanApplyState(
    val amount: String = "",
    val termMonths: Int = 12,
    val purpose: String = "",
    val estimatedInstallment: Installment? = null,
    val validation: ValidationState = ValidationState.Idle,
    val showGuarantorSection: Boolean = false,   // gated on TenantCapabilities at init
    val isSubmitting: Boolean = false,
) : UiState

internal sealed interface LoanApplyEvent : UiEvent {
    data class AmountChanged(val raw: String) : LoanApplyEvent
    data class TermChanged(val months: Int) : LoanApplyEvent
    data class PurposeChanged(val text: String) : LoanApplyEvent
    object SubmitClicked : LoanApplyEvent
    object DismissError : LoanApplyEvent
}

internal sealed interface LoanApplyEffect : UiEffect {
    data class NavigateToReview(val application: LoanApplication) : LoanApplyEffect
    data class ShowError(val message: String) : LoanApplyEffect
}
```

---

## 4. ViewModel Template

```kotlin
@HiltViewModel
internal class LoanApplyViewModel @Inject constructor(
    private val applicationRepo: LoanApplicationRepository,    // :core interface, impl from :data
    private val eligibility: LoanEligibilityPolicy,            // :core interface, impl from :tenants:*:*
    private val emiCalculator: EmiCalculator,                  // :core interface, impl from :tenants:*:*
    capabilities: TenantCapabilities,                          // :core interface, impl from :tenants:*:*
) : MviViewModel<LoanApplyState, LoanApplyEvent, LoanApplyEffect>(
    initial = LoanApplyState(showGuarantorSection = capabilities.requiresGuarantor()),
) {

    override fun onEvent(event: LoanApplyEvent) = when (event) {
        is LoanApplyEvent.AmountChanged    -> onAmountChanged(event.raw)
        is LoanApplyEvent.TermChanged      -> onTermChanged(event.months)
        is LoanApplyEvent.PurposeChanged   -> setState { copy(purpose = event.text) }
        LoanApplyEvent.SubmitClicked       -> submit()
        LoanApplyEvent.DismissError        -> setState { copy(validation = ValidationState.Idle) }
    }

    private fun onAmountChanged(raw: String) {
        val parsed = Money.parseOrNull(raw)
        val validation = parsed?.let { eligibility.validateRequestedAmount(it) } ?: ValidationState.Idle
        val emi = parsed?.let { emiCalculator.compute(it, state.value.termMonths, state.value.annualRate) }
        setState { copy(amount = raw, validation = validation, estimatedInstallment = emi) }
    }

    private fun onTermChanged(months: Int) {
        val parsed = Money.parseOrNull(state.value.amount)
        val emi = parsed?.let { emiCalculator.compute(it, months, state.value.annualRate) }
        setState { copy(termMonths = months, estimatedInstallment = emi) }
    }

    private fun submit() = viewModelScope.launch {
        setState { copy(isSubmitting = true) }
        val application = currentApplicationOrNull() ?: return@launch
        applicationRepo.submit(application)
            .onSuccess { emitEffect(LoanApplyEffect.NavigateToReview(application)) }
            .onFailure { emitEffect(LoanApplyEffect.ShowError(it.userMessage())) }
        setState { copy(isSubmitting = false) }
    }
}
```

The ViewModel:

- Holds **only `:core` interfaces** as dependencies. Never a concrete `:data` repo or `:tenants:*:*` policy class.
- Uses `setState { … }` for state changes; `emitEffect(…)` for one-shot side effects.
- Returns `Unit` from `onEvent` via the exhaustive `when` — each event maps to exactly one branch.
- Reads tenant capabilities at construction and stores derived booleans in `UiState` — the rendered Composable never sees `TenantCapabilities` directly.

---

## 5. Composable Screen Template

```kotlin
@Composable
internal fun LoanApplyScreen(
    viewModel: LoanApplyViewModel = hiltViewModel(),
    navigateToReview: (LoanApplication) -> Unit,
    showError: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoanApplyEffect.NavigateToReview -> navigateToReview(effect.application)
                is LoanApplyEffect.ShowError        -> showError(effect.message)
            }
        }
    }

    LoanApplyContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun LoanApplyContent(
    state: LoanApplyState,
    onEvent: (LoanApplyEvent) -> Unit,
) {
    // Pure rendering of state. Composable-preview-friendly.
    if (state.showGuarantorSection) GuarantorSection(onClick = { /* ... */ })
}
```

Splitting `Screen` (stateful) from `Content` (stateless) gives:

- Composable previews of every state without injecting a ViewModel.
- Easy testing: `Content` is a pure function.
- Clean separation between effect collection and rendering.

---

## 6. State-Holding Discipline

| ✅ Belongs in `UiState` | ❌ Does not belong in `UiState` |
|---|---|
| What's currently shown on screen | One-shot navigation commands |
| Form input values | Toast/snackbar messages already shown |
| Loading/empty/error indicators | `Throwable` instances |
| Capability-derived booleans (e.g. `showGuarantorSection`) | Concrete `:tenants:*:*` types |
| Selected items, expanded sections | The `TenantId` (UI never branches on it) |
| Cached data being displayed | Live `Flow`s — collect them into state instead |

If state would change "the same way" twice in a row and you'd want both events delivered, it's an **effect**, not state. Errors are the canonical example: showing the same error twice should produce two snackbars, not one suppressed change.

---

## 7. Why MVI Specifically

Lending flows have two properties that MVI handles cleanly and other patterns don't:

1. **Multi-step loan-application flows have implicit state machines** (Quick info → Branch → Documents → Guarantor → Review → Submit). MVI's single-state-per-screen forces these transitions to be explicit and reviewable. Same applies to repayment and payoff flows.
2. **One-shot effects must not be replayed on rotation** (a navigation command shouldn't re-fire when the user rotates the device). MVI's effect channel handles this naturally; a state-only model has to special-case it.

The cost — boilerplate per screen — is mitigated by the `MviViewModel` base class.

---

## 8. Cross-references

- Where the base contracts live: [03 — `:core`](03-core.md) (`mvi/` package)
- How state is cleared on logout: [10 — Boot Phases](10-boot-phases.md)
- The `:core` policy interfaces ViewModels inject: [03](03-core.md) and [07](07-variants.md) (tenant policies)
