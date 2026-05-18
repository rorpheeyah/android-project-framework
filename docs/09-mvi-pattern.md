# 09 · MVI Pattern

> **Pattern:** Model-View-Intent · strict unidirectional data flow
> **Scope:** Every screen in `:features`, `:features-scanner`, and any sibling `:features-{name}`
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

> **Replaces today's MVVM-with-`OnComTranListener`:** the existing Bizplay code wires Activities to ViewModels via Java callback interfaces (`OnComTranListener.onComTranComplete(...)`). The framework replaces that with `StateFlow` + `SharedFlow` + Compose-side `collectAsStateWithLifecycle()`.

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
// :features/receipt/detail/ReceiptDetailContract.kt

internal data class ReceiptDetailState(
    val receipt: Receipt? = null,
    val rendered: RenderedReceipt? = null,
    val showEmployeeIdRow: Boolean = true,           // gated on tenant.flags at init
    val showOcrButton: Boolean = false,              // gated on VariantCapabilities at init
    val showKakaoPayLink: Boolean = false,           // gated on VariantCapabilities at init
    val validation: ValidationState = ValidationState.Idle,
    val isSubmitting: Boolean = false,
) : UiState

internal sealed interface ReceiptDetailEvent : UiEvent {
    data class AmountChanged(val raw: String) : ReceiptDetailEvent
    data class EmployeeIdChanged(val raw: String) : ReceiptDetailEvent
    data class PhotoAdded(val ref: PhotoRef) : ReceiptDetailEvent
    object SubmitClicked : ReceiptDetailEvent
    object DismissError : ReceiptDetailEvent
}

internal sealed interface ReceiptDetailEffect : UiEffect {
    data class NavigateToApprovalLineSetup(val receiptId: ReceiptId) : ReceiptDetailEffect
    data class ShowError(val message: String) : ReceiptDetailEffect
}
```

---

## 4. ViewModel Template

```kotlin
@HiltViewModel
internal class ReceiptDetailViewModel @Inject constructor(
    private val receiptRepo: ReceiptRepository,            // :core interface, impl from :data
    private val amountPolicy: ExpenseAmountPolicy,         // :core interface, impl from :variants-*
    private val employeeIdValidator: EmployeeIdValidator,  // :core interface, impl from :variants-*
    private val receiptRenderer: ReceiptRenderer,          // :core interface, impl from :variants-*
    capabilities: VariantCapabilities,                     // :core interface, impl from :variants-*
    tenant: TenantContext,                                 // :core type, immutable for session
    private val savedStateHandle: SavedStateHandle,
) : MviViewModel<ReceiptDetailState, ReceiptDetailEvent, ReceiptDetailEffect>(
    initial = ReceiptDetailState(
        showEmployeeIdRow = !tenant.flags.hidesEmployeeId,
        showOcrButton     = capabilities.supportsOcrTicketScan(),
        showKakaoPayLink  = capabilities.supportsKakaoPayLink(),
    ),
) {

    init {
        val receiptId = savedStateHandle.get<String>("receiptId")?.let(::ReceiptId)
            ?: error("receiptId required")
        loadReceipt(receiptId)
    }

    override fun onEvent(event: ReceiptDetailEvent) = when (event) {
        is ReceiptDetailEvent.AmountChanged      -> onAmountChanged(event.raw)
        is ReceiptDetailEvent.EmployeeIdChanged  -> onEmployeeIdChanged(event.raw)
        is ReceiptDetailEvent.PhotoAdded         -> onPhotoAdded(event.ref)
        ReceiptDetailEvent.SubmitClicked         -> submit()
        ReceiptDetailEvent.DismissError          -> setState { copy(validation = ValidationState.Idle) }
    }

    private fun onAmountChanged(raw: String) {
        val parsed = Money.parseOrNull(raw)
        val validation = parsed?.let(amountPolicy::validate) ?: ValidationState.Idle
        setState { copy(validation = validation) }
    }

    private fun submit() = viewModelScope.launch {
        setState { copy(isSubmitting = true) }
        val draft = currentDraftOrNull() ?: return@launch
        receiptRepo.create(draft)
            .onSuccess { receipt ->
                emitEffect(ReceiptDetailEffect.NavigateToApprovalLineSetup(receipt.id))
            }
            .onFailure { emitEffect(ReceiptDetailEffect.ShowError(it.userMessage())) }
        setState { copy(isSubmitting = false) }
    }

    private fun loadReceipt(id: ReceiptId) = viewModelScope.launch {
        receiptRepo.detail(id).onSuccess { receipt ->
            val rendered = receiptRenderer.render(receipt, primaryLanguage = "ko")
            setState { copy(receipt = receipt, rendered = rendered) }
        }
    }
}
```

The ViewModel:

- Holds **only `:core` interfaces** as dependencies. Never a concrete `:data` repo or `:variants-*` policy class.
- Uses `setState { … }` for state changes; `emitEffect(…)` for one-shot side effects.
- Returns `Unit` from `onEvent` via the exhaustive `when` — each event maps to exactly one branch.
- Reads variant capabilities and tenant flags at construction and stores derived booleans in `UiState` — the rendered Composable never sees `VariantCapabilities` or `TenantContext` directly.

---

## 5. Composable Screen Template

```kotlin
@Composable
internal fun ReceiptDetailScreen(
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
    navigateToApprovalLineSetup: (ReceiptId) -> Unit,
    showError: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReceiptDetailEffect.NavigateToApprovalLineSetup -> navigateToApprovalLineSetup(effect.receiptId)
                is ReceiptDetailEffect.ShowError                   -> showError(effect.message)
            }
        }
    }

    ReceiptDetailContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun ReceiptDetailContent(
    state: ReceiptDetailState,
    onEvent: (ReceiptDetailEvent) -> Unit,
) {
    // Pure rendering of state. Composable-preview-friendly.
    state.rendered?.let { rendered ->
        Column {
            BizReceiptHeader(rendered.title)
            rendered.lines.forEach { BizReceiptRow(it.label, it.value) }
            rendered.footer?.let { BizReceiptFooter(it) }
            rendered.regulatoryDisclosure?.let { BizDisclosure(it) }
        }
    }
    if (state.showOcrButton) {
        BizButton(onClick = { /* navigate to :features-scanner OCR entry */ }) { Text("Scan receipt") }
    }
    if (state.showKakaoPayLink) {
        BizButton(onClick = { /* … */ }) { Text("Link KakaoPay") }
    }
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
| Capability-derived booleans (e.g. `showOcrButton`) | Concrete `:variants-*` types |
| Tenant-flag-derived booleans (e.g. `showEmployeeIdRow`) | The `TenantContext` itself (just read fields once at init) |
| Selected items, expanded sections | The `VariantId` or `TenantId` (UI never branches on them) |
| Cached data being displayed | Live `Flow`s — collect them into state instead |

If state would change "the same way" twice in a row and you'd want both events delivered, it's an **effect**, not state. Errors are the canonical example: showing the same error twice should produce two snackbars, not one suppressed change.

---

## 7. Why MVI Specifically

Corporate-expense flows have two properties that MVI handles cleanly and other patterns don't:

1. **Multi-step submission flows have implicit state machines** (Capture → Detail → Categorise → Attach approval line → Review → Submit). MVI's single-state-per-screen forces these transitions to be explicit and reviewable.
2. **One-shot effects must not be replayed on rotation** (a navigation command shouldn't re-fire when the user rotates the device). MVI's effect channel handles this naturally; a state-only model has to special-case it.

The cost — boilerplate per screen — is mitigated by the `MviViewModel` base class.

---

## 8. Cross-references

- Where the base contracts live: [03 — `:core`](03-core.md) (`mvi/` package)
- How state is cleared on logout: [10 — Boot Phases](10-boot-phases.md)
- The `:core` policy interfaces ViewModels inject: [03](03-core.md) and [07](07-variants.md)
- The `TenantContext` that ViewModels read tenant flags from: [19 — Tenants and Variants](19-tenants-and-variants.md)
