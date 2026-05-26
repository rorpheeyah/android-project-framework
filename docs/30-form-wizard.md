# 30 · Form Wizard (Multi-Step Apply Flows)

> **Use case:** The PRD has two long apply-loan flows — NON-MWL (~9 steps, items 15–20) and MWL (~18 steps, items 21–28). Per-screen MVI is the wrong granularity; the flow itself needs a state holder, draft persistence, and resume-on-return.
> **Where it lives:** `:core/wizard/` (contract types) + per-flow ViewModel scoped to the Navigation graph in `:features`.
> **Persistence:** Drafts auto-save to `drafts.db` ([29 — Local Database](29-local-database.md)) on every step transition.

---

## 1. Why a Separate Contract

The framework's per-screen MVI ([09](09-mvi-pattern.md)) is designed for self-contained screens: one screen, one ViewModel, one state. A multi-step apply form has **cross-step state** — choices on step 3 affect what's shown on step 7, and the entire form must be submittable as one atomic action at the end.

Three options were considered:

| Option | Reason rejected |
|---|---|
| One giant `LoanApplyState` in a single ViewModel for the whole flow | The ViewModel becomes a god object; tests are unwieldy; navigation back-stack semantics fight the state model |
| Per-step ViewModels with `SavedStateHandle` plumbing | Doable but requires manual cross-step coordination; easy to lose data on process death; resume is fragile |
| **Wizard contract + per-flow ViewModel scoped to a NavGraph** | Picked. Each step is a Composable that observes the wizard state; the wizard ViewModel survives back-stack pops within the graph; drafts persist outside the process |

---

## 2. The `:core/wizard/` Contract

```kotlin
// :core/wizard/WizardState.kt
interface WizardState<S : WizardStep> {
    val currentStep: S
    val completedSteps: Set<S>
    val data: WizardData                 // accumulator
    val validation: WizardValidation
    val isSubmitting: Boolean
}

interface WizardStep {
    val order: Int                        // numeric ordering; allows skipping steps
    val isOptional: Boolean
}

interface WizardData {
    fun merge(updates: Map<String, Any?>): WizardData    // immutable update returning new instance
    fun toJsonString(): String                            // for persistence
}

sealed interface WizardValidation {
    object Ok : WizardValidation
    data class StepInvalid(val errors: Map<String, String>) : WizardValidation     // field-id → error message
}

// :core/wizard/WizardEvent.kt
interface WizardEvent
sealed interface CommonWizardEvent : WizardEvent {
    object NextClicked : CommonWizardEvent
    object BackClicked : CommonWizardEvent
    data class StepData(val updates: Map<String, Any?>) : CommonWizardEvent
    object SubmitClicked : CommonWizardEvent
    object SaveDraftClicked : CommonWizardEvent
    object DiscardDraftClicked : CommonWizardEvent
}

// :core/wizard/WizardEffect.kt
interface WizardEffect
sealed interface CommonWizardEffect : WizardEffect {
    object NavigateToNextStep : CommonWizardEffect
    object NavigateToPreviousStep : CommonWizardEffect
    data class NavigateToStep(val step: WizardStep) : CommonWizardEffect
    object SubmissionSucceeded : CommonWizardEffect
    data class SubmissionFailed(val message: String) : CommonWizardEffect
    data class DraftSaved(val savedAt: Instant) : CommonWizardEffect
}
```

Each flow extends these interfaces with its own step enum, data class, and effects.

---

## 3. The `:features/loan/apply/` Example

```kotlin
// :features/loan/apply/non-mwl/NonMwlApplySteps.kt
internal enum class NonMwlStep(override val order: Int, override val isOptional: Boolean = false) : WizardStep {
    BorrowerInfo(0),
    Branch(1),
    Documents(2, isOptional = true),
    Referral(3, isOptional = true),
    Review(4),
}

// :features/loan/apply/non-mwl/NonMwlApplyData.kt
internal data class NonMwlApplyData(
    val borrowerName: String? = null,
    val borrowerPhone: String? = null,
    val borrowerProvince: String? = null,
    val requestedAmount: Money? = null,
    val purpose: String? = null,
    val selectedBranchId: BranchId? = null,
    val uploadedDocuments: List<KycSubmissionId> = emptyList(),
    val referralStaffId: String? = null,
) : WizardData {
    override fun merge(updates: Map<String, Any?>) = copy(/* … field-by-field via Kotlin reflection or manual */)
    override fun toJsonString() = /* … */
}

// :features/loan/apply/non-mwl/NonMwlApplyViewModel.kt
@HiltViewModel
internal class NonMwlApplyViewModel @Inject constructor(
    private val applicationRepo: LoanApplicationRepository,
    private val draftStore: DraftStore,                          // see §5
    private val eligibility: LoanEligibilityPolicy,
    @Assisted private val resumeDraftId: DraftId?,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(resumeDraftId))
    val state: StateFlow<NonMwlApplyState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CommonWizardEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<CommonWizardEffect> = _effects.asSharedFlow()

    fun onEvent(event: CommonWizardEvent) {
        when (event) {
            is CommonWizardEvent.StepData       -> applyStepData(event.updates)
            CommonWizardEvent.NextClicked       -> validateAndAdvance()
            CommonWizardEvent.BackClicked       -> navigateBack()
            CommonWizardEvent.SubmitClicked     -> submit()
            CommonWizardEvent.SaveDraftClicked  -> saveDraft()
            CommonWizardEvent.DiscardDraftClicked -> discardDraft()
        }
    }

    private fun applyStepData(updates: Map<String, Any?>) {
        _state.update { it.copy(data = it.data.merge(updates)) }
        viewModelScope.launch { draftStore.save(state.value.toDraft()) }      // auto-save on every change
    }

    private fun validateAndAdvance() {
        val result = validate(state.value.currentStep, state.value.data)
        if (result is WizardValidation.Ok) {
            _state.update { it.copy(completedSteps = it.completedSteps + it.currentStep) }
            viewModelScope.launch { _effects.emit(CommonWizardEffect.NavigateToNextStep) }
        } else {
            _state.update { it.copy(validation = result) }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val application = state.value.data.toLoanApplication()
            applicationRepo.submit(application).fold(
                onSuccess = { id ->
                    draftStore.delete(state.value.draftId)
                    _effects.emit(CommonWizardEffect.SubmissionSucceeded)
                },
                onFailure = { e -> _effects.emit(CommonWizardEffect.SubmissionFailed(e.userMessage())) },
            )
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}
```

---

## 4. NavGraph Scoping

The wizard ViewModel must survive back-stack pops between steps but be GC'd when the user exits the entire apply flow. The right scope is `@hiltViewModel` on a `NavBackStackEntry` corresponding to the **NavGraph's start destination**, not on individual screens:

```kotlin
// :features/loan/apply/non-mwl/NonMwlApplyNavGraph.kt
fun NavGraphBuilder.nonMwlApplyNavGraph(navController: NavHostController) {
    navigation(startDestination = "non-mwl/borrower", route = "non-mwl-apply") {

        composable("non-mwl/borrower") { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("non-mwl-apply") }
            val viewModel: NonMwlApplyViewModel = hiltViewModel(parentEntry)
            BorrowerInfoStep(viewModel)
        }

        composable("non-mwl/branch") { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("non-mwl-apply") }
            val viewModel: NonMwlApplyViewModel = hiltViewModel(parentEntry)
            BranchSelectionStep(viewModel)
        }

        composable("non-mwl/documents") { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("non-mwl-apply") }
            val viewModel: NonMwlApplyViewModel = hiltViewModel(parentEntry)
            DocumentsStep(viewModel)
        }

        // … referral, review
    }
}
```

The `navController.getBackStackEntry("non-mwl-apply")` retrieves the parent graph's entry; the ViewModel is scoped to it. All steps within the graph see the same `NonMwlApplyViewModel` instance. The instance is GC'd only when the `non-mwl-apply` graph is popped.

**This is the standard Compose Navigation + Hilt scoping pattern.** Each step Composable looks up the parent entry; no manual plumbing is needed.

---

## 5. Draft Persistence

Drafts persist to `drafts.db` ([29 — Local Database](29-local-database.md)) on every step transition. If the user backgrounds the app, kills the process, or kills the device, the draft survives.

```kotlin
// :data/drafts/DraftStore.kt
internal class DraftStore @Inject constructor(
    private val dao: DraftDao,
    private val session: Session,
) {
    suspend fun save(draft: Draft) {
        dao.upsert(DraftEntity(
            id = draft.id.value,
            userId = session.userSession.userId.value,
            flowType = draft.flowType.name,
            currentStep = draft.currentStep,
            dataJson = draft.dataJson,
            updatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    suspend fun load(id: DraftId): Draft? = dao.byId(id.value)?.toDomain()

    suspend fun listFor(flowType: DraftFlowType): List<Draft> =
        dao.observeByUser(session.userSession.userId.value, flowType.name)
            .map { it.map(DraftEntity::toDomain) }
            .first()

    suspend fun delete(id: DraftId) = dao.delete(id.value)
}

// :core/wizard/Draft.kt
data class Draft(
    val id: DraftId,
    val flowType: DraftFlowType,
    val currentStep: Int,
    val dataJson: String,
    val updatedAt: Instant,
)
@JvmInline value class DraftId(val value: String)
enum class DraftFlowType { NonMwlApply, MwlApply, Restructure }
```

Drafts are **per user** (the `userId` index ensures one user can't see another's draft on a shared device). On logout, all drafts are wiped (see [29 § 5](29-local-database.md)).

### 5.1 Resume UX

The dashboard (or My Loans screen) shows a "Resume application" banner if `DraftStore.listFor(NonMwlApply).isNotEmpty()`:

```kotlin
// :features/dashboard/DashboardViewModel.kt
class DashboardViewModel @Inject constructor(
    private val drafts: DraftStore,
) : MviViewModel<…>() {

    init {
        viewModelScope.launch {
            val pendingDrafts = drafts.listFor(DraftFlowType.NonMwlApply) +
                                drafts.listFor(DraftFlowType.MwlApply)
            setState { copy(resumableDrafts = pendingDrafts) }
        }
    }
}
```

Tapping "Resume" navigates to the apply graph with `?draftId=<id>` query parameter; the ViewModel loads from the draft instead of starting fresh.

---

## 6. Validation Per Step

Each step has its own validation rule, gating "Next":

```kotlin
private fun validate(step: NonMwlStep, data: NonMwlApplyData): WizardValidation = when (step) {
    NonMwlStep.BorrowerInfo -> validateBorrower(data)
    NonMwlStep.Branch       -> if (data.selectedBranchId != null) WizardValidation.Ok
                               else WizardValidation.StepInvalid(mapOf("branch" to "Required"))
    NonMwlStep.Documents    -> WizardValidation.Ok                   // optional step
    NonMwlStep.Referral     -> validateReferral(data.referralStaffId)
    NonMwlStep.Review       -> validateAll(data)
}

private fun validateBorrower(data: NonMwlApplyData): WizardValidation {
    val errors = mutableMapOf<String, String>()
    if (data.borrowerName.isNullOrBlank()) errors["name"] = "Required"
    if (data.borrowerPhone.isNullOrBlank()) errors["phone"] = "Required"
    if (data.requestedAmount == null) errors["amount"] = "Required"
    else if (eligibility.validateRequestedAmount(data.requestedAmount) is ValidationResult.Invalid) {
        errors["amount"] = "Above eligibility limit"
    }
    return if (errors.isEmpty()) WizardValidation.Ok else WizardValidation.StepInvalid(errors)
}
```

The eligibility policy comes from the active tenant (`:tenants:{region}:base/policy/`). Same Logic-Blind rule: the wizard reads the policy via interface, never names the implementing class.

---

## 7. The MWL Flow (18 Steps)

The MWL flow (PRD items 21–28) is structurally identical to NON-MWL — just more steps and an extra "Add Guarantor" sub-flow that launches `:features-kyc` ([23 — KYC Capture](23-kyc-capture.md)) via deeplink ([22 — Deeplinks § 6](22-deeplinks.md)).

```kotlin
internal enum class MwlStep(override val order: Int, override val isOptional: Boolean = false) : WizardStep {
    BorrowerQuickInfo(0),
    Branch(1),
    EmploymentInfo(2),
    Referral(3, isOptional = true),
    MwlAgencyInfo(4),
    LoanRequestInfo(5),
    BankAccount(6),
    ConfirmApplicationForm(7),
    AddGuarantor(8),                // triggers guarantor invite → SMS link → :features-kyc verify flow
    AwaitingGuarantorConfirmation(9),
    ConfirmGuarantorReview(10),
    Submit(11),
    // (later steps handled in :features/loan/my-loan/ — assignment, assessment, approval, accept, disbursement)
}
```

The "Awaiting" step is special — it's a polling state, not a user-input form. The ViewModel observes `guarantorRepo.verificationStatus(guarantorId)` and emits `NavigateToNextStep` when the guarantor completes their flow.

---

## 8. What Does NOT Belong Here

| ❌ Wrong | ✅ Right |
|---|---|
| One giant ViewModel for all 18 steps that does everything | The flow ViewModel coordinates state + persistence; per-step logic lives in step Composables |
| `SavedStateHandle` plumbing for cross-step coordination | NavGraph-scoped ViewModel is the right scope |
| Drafts in SharedPreferences | `drafts.db` (Room) — see [29] |
| Cross-user draft visibility | DAO query MUST filter by `userId`; logout wipes drafts |
| Validation rules baked into the wizard ViewModel | Defer to `LoanEligibilityPolicy` and tenant policies; the wizard only orchestrates |
| Submitting partial drafts to the server "for safekeeping" | The server expects whole applications; partial uploads are not supported in the contract |

---

## 9. Cross-references

- The MVI base pattern this builds on: [09 — MVI Pattern](09-mvi-pattern.md)
- The draft database: [29 — Local Database](29-local-database.md)
- The KYC capture launched from the guarantor sub-flow: [23 — KYC Capture](23-kyc-capture.md)
- The guarantor SMS deeplink: [22 — Deeplinks § 6](22-deeplinks.md)
- The tenant eligibility policy consumed for validation: [07 — `:tenants:*`](07-variants.md), [19 — Tenants and Regions](19-tenants-and-variants.md)
- The repository that finally submits: [05 — `:data`](05-data.md) (`LoanApplicationRepo`)
