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
// :features/transfer/input/TransferInputContract.kt

internal data class TransferInputState(
    val amount: String = "",
    val beneficiaryName: String? = null,
    val feeQuote: FeeQuote? = null,
    val validation: ValidationState = ValidationState.Idle,
    val showQrScanner: Boolean = false,        // gated on VariantCapabilities at init
    val isSubmitting: Boolean = false,
) : UiState

internal sealed interface TransferInputEvent : UiEvent {
    data class AmountChanged(val raw: String) : TransferInputEvent
    data class BeneficiaryScanned(val payload: String) : TransferInputEvent
    object SubmitClicked : TransferInputEvent
    object DismissError : TransferInputEvent
}

internal sealed interface TransferInputEffect : UiEffect {
    data class NavigateToReview(val intent: TransferIntent) : TransferInputEffect
    data class ShowError(val message: String) : TransferInputEffect
}
```

---

## 4. ViewModel Template

```kotlin
@HiltViewModel
internal class TransferInputViewModel @Inject constructor(
    private val transferRepo: TransferRepository,        // :core interface, impl from :data
    private val amountPolicy: TransferAmountPolicy,      // :core interface, impl from :variants-*
    capabilities: VariantCapabilities,                   // :core interface, impl from :variants-*
) : MviViewModel<TransferInputState, TransferInputEvent, TransferInputEffect>(
    initial = TransferInputState(showQrScanner = capabilities.supportsKhqrScan()),
) {

    override fun onEvent(event: TransferInputEvent) = when (event) {
        is TransferInputEvent.AmountChanged    -> onAmountChanged(event.raw)
        is TransferInputEvent.BeneficiaryScanned -> resolveBeneficiary(event.payload)
        TransferInputEvent.SubmitClicked       -> submit()
        TransferInputEvent.DismissError        -> setState { copy(validation = ValidationState.Idle) }
    }

    private fun onAmountChanged(raw: String) {
        val parsed = Money.parseOrNull(raw)
        val validation = parsed?.let(amountPolicy::validate) ?: ValidationState.Idle
        setState { copy(amount = raw, validation = validation) }
    }

    private fun submit() = viewModelScope.launch {
        setState { copy(isSubmitting = true) }
        val intent = currentIntentOrNull() ?: return@launch
        transferRepo.submit(intent)
            .onSuccess { emitEffect(TransferInputEffect.NavigateToReview(intent)) }
            .onFailure { emitEffect(TransferInputEffect.ShowError(it.userMessage())) }
        setState { copy(isSubmitting = false) }
    }
}
```

The ViewModel:

- Holds **only `:core` interfaces** as dependencies. Never a concrete `:data` repo or `:variants-*` policy class.
- Uses `setState { … }` for state changes; `emitEffect(…)` for one-shot side effects.
- Returns `Unit` from `onEvent` via the exhaustive `when` — each event maps to exactly one branch.
- Reads variant capabilities at construction and stores derived booleans in `UiState` — the rendered Composable never sees `VariantCapabilities` directly.

---

## 5. Composable Screen Template

```kotlin
@Composable
internal fun TransferInputScreen(
    viewModel: TransferInputViewModel = hiltViewModel(),
    navigateToReview: (TransferIntent) -> Unit,
    showError: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransferInputEffect.NavigateToReview -> navigateToReview(effect.intent)
                is TransferInputEffect.ShowError        -> showError(effect.message)
            }
        }
    }

    TransferInputContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun TransferInputContent(
    state: TransferInputState,
    onEvent: (TransferInputEvent) -> Unit,
) {
    // Pure rendering of state. Composable-preview-friendly.
    if (state.showQrScanner) QrScannerButton(onClick = { /* ... */ })
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
| Capability-derived booleans (e.g. `showQrScanner`) | Concrete `:variants-*` types |
| Selected items, expanded sections | The `VariantId` (UI never branches on it) |
| Cached data being displayed | Live `Flow`s — collect them into state instead |

If state would change "the same way" twice in a row and you'd want both events delivered, it's an **effect**, not state. Errors are the canonical example: showing the same error twice should produce two snackbars, not one suppressed change.

---

## 7. Why MVI Specifically

Banking flows have two properties that MVI handles cleanly and other patterns don't:

1. **Multi-step transfer flows have implicit state machines** (Input → Review → Authorize → Result). MVI's single-state-per-screen forces these transitions to be explicit and reviewable.
2. **One-shot effects must not be replayed on rotation** (a navigation command shouldn't re-fire when the user rotates the device). MVI's effect channel handles this naturally; a state-only model has to special-case it.

The cost — boilerplate per screen — is mitigated by the `MviViewModel` base class.

---

## 8. Cross-references

- Where the base contracts live: [03 — `:core`](03-core.md) (`mvi/` package)
- How state is cleared on logout: [10 — Boot Phases](10-boot-phases.md)
- The `:core` policy interfaces ViewModels inject: [03](03-core.md) and [07](07-variants.md)
