# 10 · Boot Phases

> **Architectural promise:** the variant *and* tenant are selected once, at login. Configuration is fetched once, from MgGate, at cold start. There is no in-session swap.
>
> This is the highest-leverage doc in the framework. Get the boot phases right and the rest of the architecture pays dividends.

---

## 1. The Phases

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. COLD START   App.onCreate(); SecurityProvider self-checks    │
│                  (root/jailbreak + mVaccine + RSLicense)        │
├─────────────────────────────────────────────────────────────────┤
│ 2. MG FETCH     BootCoordinator hits the hardcoded MgGate URL.  │
│                  Receives RuntimeConfig { urls, webRoutes,      │
│                  maintenance, forceUpdate }                     │
├─────────────────────────────────────────────────────────────────┤
│ 3. GATE         If maintenance.down or version < min →          │
│                  MaintenanceGate; HARD STOP (no login).         │
│                  Else: proceed.                                 │
├─────────────────────────────────────────────────────────────────┤
│ 4. LOGIN        AuthRepository.login(...) → LoginResponse        │
│                  containing { userSession, variantId, tenantId, │
│                  tenantFlags, tenantParams, accounts[] }        │
│                  Optional: institution picker if accounts.size>1│
├─────────────────────────────────────────────────────────────────┤
│ 5. BUILD        BootCoordinator builds LoggedInComponent with   │
│                  VariantContext from variantId, TenantContext   │
│                  from tenantId+flags+params, and the Session    │
│                  built from the LoginResponse                    │
├─────────────────────────────────────────────────────────────────┤
│ 6. ENTER MAIN   Navigate to MainScaffold. ViewModels resolve    │
│                  repositories (from :data) and policies (from   │
│                  the active :variants-{id} + tenant) via        │
│                  LoggedInEntryPt                                │
├─────────────────────────────────────────────────────────────────┤
│ 7. LOGOUT       LoggedInComponent dropped. All @LoggedInScoped  │
│                  instances become GC-eligible. RuntimeConfig    │
│                  stays (it's process-scoped). Navigate to login.│
└─────────────────────────────────────────────────────────────────┘
```

**Variant or tenant change requires logout.** There is no shortcut. Switching between *institutions the user already belongs to* (the `USE_INTT_ID` axis) is a separate, lighter mechanism — see [12 — Departments and Session](12-departments-and-session.md).

---

## 2. The Hardcoded MG URL (and only that)

The only network configuration baked into the binary is the MgGate URL:

```kotlin
// :app/build.gradle.kts (per build type)
buildConfigField("String", "MG_URL", "\"https://mg.bizplay.co.kr/MgGate\"")
// debug → "\"https://mg-dev.bizplay.co.kr/MgGate\""
```

`MgClient` reads `BuildConfig.MG_URL`. Everything else — main IPPP API base URL (today's `Conf.IPPP_SITE_URL`), approval-form URL (today's `Constant.MG.C_APPROVAL_URL`), member URL, logo URL, partner URLs, maintenance state, version floor — comes from MgGate's response. A backend URL change requires no new APK.

→ Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

### 2.1 `BootScreen` — the UI driver of the boot phases

`:features/boot/BootScreen` is the navigation entry point. It calls `BootCoordinator.runBoot()` via its ViewModel and routes to the next destination based on the result.

```kotlin
// :features/boot/BootScreen.kt
@Composable
internal fun BootScreen(
    viewModel: BootViewModel = hiltViewModel(),
    onReady: () -> Unit,
    onMaintenance: (MaintenanceState.Down) -> Unit,
    onForceUpdate: (ForceUpdate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BootEffect.Ready              -> onReady()
                is BootEffect.Maintenance     -> onMaintenance(effect.state)
                is BootEffect.ForceUpdate     -> onForceUpdate(effect.forceUpdate)
            }
        }
    }

    when (state.phase) {
        BootPhase.Loading -> BizLoadingScreen()
        BootPhase.Failed  -> BizRetryScreen(state.errorMessage) { viewModel.onEvent(BootEvent.Retry) }
    }
}

@HiltViewModel
internal class BootViewModel @Inject constructor(
    private val bootCoordinator: BootCoordinator,
) : MviViewModel<BootState, BootEvent, BootEffect>(initial = BootState(BootPhase.Loading)) {

    init { runBoot() }

    override fun onEvent(event: BootEvent) = when (event) {
        BootEvent.Retry -> { setState { copy(phase = BootPhase.Loading) }; runBoot() }
    }

    private fun runBoot() = viewModelScope.launch {
        runCatching { bootCoordinator.runBoot() }
            .onSuccess { result ->
                when (result) {
                    BootResult.Ready              -> emitEffect(BootEffect.Ready)
                    is BootResult.Maintenance     -> emitEffect(BootEffect.Maintenance(result.state))
                    is BootResult.ForceUpdate     -> emitEffect(BootEffect.ForceUpdate(result.forceUpdate))
                }
            }
            .onFailure { e ->
                setState { copy(phase = BootPhase.Failed, errorMessage = e.message ?: "Cannot reach servers") }
            }
    }
}
```

`MaintenanceGate` and `ForceUpdateGate` are sibling Composables in the same package, rendered when navigation lands on their routes. See [11 — MG and Runtime Config](11-mg-and-runtime-config.md) for their UX.

> **Replaces today's `IntroActivity` + `IntroViewModel.requestMG()`:** the existing Bizplay code does the MgGate fetch inside an Activity-scoped ViewModel and stores the response in `MemoryPreferenceDelegator`. The framework moves the fetch into `BootCoordinator` (singleton) and the typed payload into `RuntimeConfigStore` (also singleton).

---

## 3. The `@LoggedInScoped` Hilt Component

Hilt's standard scopes (`@Singleton`, `@ActivityRetainedScoped`, etc.) tie lifetimes to Android lifecycle objects. The framework needs a scope tied to **the logged-in session**, which has no Android lifecycle counterpart. We define a custom Hilt component:

```kotlin
// :core/scope/Scopes.kt
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped

// :app/di/LoggedInComponent.kt
@DefineComponent(parent = SingletonComponent::class)
@LoggedInScoped
interface LoggedInComponent {
    @DefineComponent.Builder
    interface Builder {
        fun bindVariantContext(@BindsInstance ctx: VariantContext): Builder
        fun bindTenantContext(@BindsInstance ctx: TenantContext): Builder
        fun bindSession(@BindsInstance session: Session): Builder
        fun build(): LoggedInComponent
    }
}

@EntryPoint
@InstallIn(LoggedInComponent::class)
interface LoggedInEntryPoint {
    fun authRepository(): AuthRepository                  // from :data
    fun receiptRepository(): ReceiptRepository            // from :data
    fun approvalRepository(): ApprovalRepository          // from :data
    fun cardRepository(): CardRepository                  // from :data
    fun expenseAmountPolicy(): ExpenseAmountPolicy        // from :variants-{id}
    fun feeCalculator(): FeeCalculator                    // from :variants-{id}
    fun receiptRenderer(): ReceiptRenderer                // from :variants-{id}
    fun approvalLineRenderer(): ApprovalLineRenderer      // from :variants-{region}/tenants/{id} (with default fallback)
    fun capabilities(): VariantCapabilities               // from :variants-{id}
    fun session(): Session
    fun variantContext(): VariantContext
    fun tenantContext(): TenantContext
}
```

Both `:data` and *every* `:variants-{id}` install into `LoggedInComponent::class`:

```kotlin
@Module @InstallIn(LoggedInComponent::class) abstract class DataModule { … }          // shared repos
@Module @InstallIn(LoggedInComponent::class) abstract class KrVariantModule { … }     // @IntoMap @VariantKey("kr")
@Module @InstallIn(LoggedInComponent::class) abstract class KhVariantModule { … }     // @IntoMap @VariantKey("kh")
@Module @InstallIn(LoggedInComponent::class) abstract class KrTenantModule { … }      // @IntoMap @TenantKey("nia"|"shinsegae"|"default")
@Module @InstallIn(LoggedInComponent::class) object VariantResolverModule { … }       // picks active by VariantContext.id
@Module @InstallIn(LoggedInComponent::class) object TenantResolverModule { … }        // picks active by TenantContext.id, falls back to "default"
```

Variant bindings use Dagger multibindings with a `@VariantKey("<id>")` map key; tenant structural bindings use `@TenantKey("<id>")`. All variants compile in without a duplicate-binding error. The resolver modules (in `:app`) look up the active variant/tenant's entry from the maps at injection time. See [07 — `:variants-*` § 6](07-variants.md) and [19 — Tenants and Variants § 8](19-tenants-and-variants.md) for the full pattern.

When the component is dropped (on logout), **every `@LoggedInScoped` instance becomes GC-eligible**. This is the structural mechanism that prevents one user's data from leaking into another user's session after logout.

---

## 4. The `LoggedInComponentManager`

The component is built once at login and dropped at logout:

```kotlin
@Singleton
class LoggedInComponentManager @Inject constructor(
    @LoggedInBuilder private val builderProvider: Provider<LoggedInComponent.Builder>,
    private val variantContextResolver: VariantContextResolver,
    private val tenantContextResolver: TenantContextResolver,
) {
    private var current: LoggedInComponent? = null
    private val mutex = Mutex()

    suspend fun build(
        variantId: VariantId,
        tenantId: TenantId,
        session: Session,
    ): LoggedInComponent =
        mutex.withLock {
            check(current == null) { "LoggedInComponent already built — call drop() first" }
            val variant = variantContextResolver.resolve(variantId)
            val tenant  = tenantContextResolver.resolve(variantId, tenantId, session.userSession.tenantFlags, session.userSession.tenantParams)
            val fresh = builderProvider.get()
                .bindVariantContext(variant)
                .bindTenantContext(tenant)
                .bindSession(session)
                .build()
            current = fresh
            fresh
        }

    fun current(): LoggedInComponent =
        checkNotNull(current) { "LoggedInComponent not built — login first" }

    suspend fun drop() = mutex.withLock {
        current = null  // dropping the only reference makes everything inside GC-eligible
    }
}
```

`build()` is called from `BootCoordinator.onLoginSuccess(...)`. `drop()` is called from `LogoutHandler`. There are no other callers.

> **Why no rebuild?** Variant or tenant change in production is logout. The user-visible operation is the same; the implementation is one Hilt component drop instead of an in-session swap with purge choreography. The existing `SelectUserInttIdActivity` flow is a *different* axis — switching between institutions the user already has access to — handled by `Session.activeAccountId` without rebuilding the component.

---

## 5. Resolving Repositories and Policies at Call Site

`:features` ViewModels inject `:core` interfaces directly via Hilt's normal injection path:

```kotlin
@HiltViewModel
internal class ReceiptDetailViewModel @Inject constructor(
    private val receiptRepo: ReceiptRepository,                  // resolved from :data via LoggedInComponent
    private val amountPolicy: ExpenseAmountPolicy,               // resolved from :variants-{active} via LoggedInComponent
    private val receiptRenderer: ReceiptRenderer,                // ditto
    private val approvalLineRenderer: ApprovalLineRenderer,      // resolved from :variants-{region}/tenants/{active} (or default)
    capabilities: VariantCapabilities,
    tenant: TenantContext,
) : MviViewModel<…>(…) { … }
```

For Hilt to find these inside the ViewModel scope (which is a child of `SingletonComponent`, not `LoggedInComponent`), the `LoggedInEntryPoint` is wired into a `SingletonComponent`-scoped façade:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LoggedInBindingsModule {

    @Provides
    fun receiptRepository(manager: LoggedInComponentManager): ReceiptRepository =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).receiptRepository()

    @Provides
    fun expenseAmountPolicy(manager: LoggedInComponentManager): ExpenseAmountPolicy =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).expenseAmountPolicy()

    @Provides
    fun tenantContext(manager: LoggedInComponentManager): TenantContext =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).tenantContext()

    // … one provider per :core interface ViewModels consume
}
```

This indirection is small (one provider per interface) and pays for itself: ViewModels stay test-friendly with explicit interface dependencies, and the active variant + tenant + data bindings still resolve through `LoggedInComponent`.

> **Trade-off:** every `:core` interface that ViewModels consume needs a one-line provider entry. Acceptable given the surface (~20 interfaces) and stability.

---

## 6. Memory at Logout

Logout drops three categories of state:

### 6.1 Session state (high sensitivity)

- **Tokens** held in `EncryptedPrefs` (today's `PreferenceDelegator` for sensitive keys): cleared by `LogoutHandler.purge()`.
- **In-memory `Session`** held inside `LoggedInComponent`: dies with the component.
- **`UserSession`** held inside `Session`: same.
- **Repository instances** in `:data` (`@LoggedInScoped`): same.
- **Policy instances** from the active variant + tenant (`@LoggedInScoped`): same.

### 6.2 ViewModel state (medium sensitivity)

ViewModels are scoped to navigation back-stack entries. Logout **navigates to the login route** with `popUpToRoot = true`, which pops every back-stack entry. ViewModels are then GC'd along with their `StateFlow`s.

### 6.3 Caches (low sensitivity but mandatory)

| Cache | Cleared by |
|---|---|
| OkHttp response cache | `OkHttpClient.cache?.evictAll()` in `LogoutHandler` |
| Image loader cache | `Coil`/`Glide` API, called in `LogoutHandler` |
| SQLCipher session-scoped tables | `EncryptedDatabase.clearSessionScope()` in `LogoutHandler` (matches today's session-scoped purge) |

Caches are *not* `@LoggedInScoped` (they are process-lifetime) — `LogoutHandler` has to clear them explicitly.

```kotlin
@Singleton
class LogoutHandler @Inject constructor(
    private val componentManager: LoggedInComponentManager,
    private val httpClient: OkHttpClient,
    private val encryptedPrefs: EncryptedPrefs,
    private val encryptedDatabase: EncryptedDatabase,
    private val navigator: GlobalNavigator,
) {
    suspend fun logout() {
        encryptedPrefs.clearSessionScope()
        encryptedDatabase.clearSessionScope()
        httpClient.cache?.evictAll()
        componentManager.drop()
        navigator.navigate(Route.Login, popUpToRoot = true)
    }
}
```

### 6.4 Variant / tenant change happens through logout-login

The boot mechanism naturally handles variant or tenant changes — there is no special "switch variant" or "switch tenant" code path.

Concrete flow when a user signed in as a POSCO ICT employee (KR / posco_ict) wants to log in to their Lotte E&C account instead:

1. User taps **Logout**. `LogoutHandler.logout()` runs: `EncryptedPrefs` cleared, OkHttp cache evicted, SQLCipher session-scoped tables cleared, `LoggedInComponent` dropped, navigation popped to `Route.Login`.
2. User enters Lotte credentials. `AuthRepository.login(...)` returns `LoginResponse(userSession, variantId = "kr", tenantId = "lotte", tenantFlags, tenantParams, accounts)`.
3. `BootCoordinator.onLoginSuccess(...)` builds a fresh `LoggedInComponent` with `VariantContext(id = "kr", …)` and `TenantContext(id = "lotte", flags = …, params = …)`, plus Lotte-specific structural policy bindings if any.
4. Navigation enters `MainScaffold`. ViewModels resolve `ApprovalLineRenderer` to `DefaultApprovalLineRenderer` (Lotte uses the default shape), `Ippp*Repo` to the same shared instances (variant- and tenant-agnostic).

**Navigation safety** is automatic:

- `popUpToRoot = true` on logout pops every back-stack entry — no stale ViewModel can leak into the next session.
- The `LoggedInComponent` drop makes every `@LoggedInScoped` instance GC-eligible — no stale repo, policy, or session value can be reached.
- `EncryptedPrefs.clearSessionScope()` and `EncryptedDatabase.clearSessionScope()` remove tokens and locally-cached card / receipt data — a request issued during the brief gap before re-login wouldn't authenticate.

The system handles "switch tenant" as the same operation as "different user logs in" — because architecturally it is.

**Proof — instrumentation test that exercises the switch:**

```kotlin
// :app/src/androidTest/.../TenantSwitchTest.kt
@HiltAndroidTest
class TenantSwitchTest {

    @Inject lateinit var bootCoordinator: BootCoordinator
    @Inject lateinit var componentManager: LoggedInComponentManager
    @Inject lateinit var logoutHandler: LogoutHandler

    @Test fun `switching from NIA to default tenant swaps active ApprovalLineRenderer`() = runTest {
        // 1. Log in as NIA (KR / nia).
        bootCoordinator.onLoginSuccess(loginResponseFor(VariantId("kr"), TenantId("nia")))
        val niaRenderer = activeRenderer<ApprovalLineRenderer>()
        // NIA uses the default approval-line shape — so it should resolve to the default impl.
        assertTrue(niaRenderer is DefaultApprovalLineRenderer)

        // 2. Log out — drops the LoggedInComponent.
        logoutHandler.logout()
        assertFailsWith<IllegalStateException> { componentManager.current() }   // no active session

        // 3. Log in as Shinsegae (KR / shinsegae) — has its own structural renderer.
        bootCoordinator.onLoginSuccess(loginResponseFor(VariantId("kr"), TenantId("shinsegae")))
        val shinsegaeRenderer = activeRenderer<ApprovalLineRenderer>()
        assertTrue(shinsegaeRenderer is ShinsegaeApprovalLineRenderer)

        // 4. The NIA-bound instance from step 1 is GC-eligible.
        assertNotSame(niaRenderer, shinsegaeRenderer)
    }

    private inline fun <reified T> activeRenderer(): T =
        EntryPoints.get(componentManager.current(), LoggedInEntryPoint::class.java)
            .let { entry ->
                when (T::class) {
                    ApprovalLineRenderer::class -> entry.approvalLineRenderer() as T
                    else -> error("Add accessor")
                }
            }
}
```

The test confirms: after logout-login with a different `tenantId`, the active `ApprovalLineRenderer` is a different concrete class, with different rules, resolved by the same Hilt entry point — without any code change.

---

## 7. What This Mechanism Does NOT Solve

- **Multiple institution memberships inside one session** — that's a `Session` concern (the `USE_INTT_ID` flip). See [12 — Departments and Session](12-departments-and-session.md).
- **A/B testing of two tenants simultaneously** — each `Application` instance can host one logged-in session. Side-by-side comparison requires two devices or two app installs.
- **Background workers (`WorkManager`) launched during one session and completing after logout** — solve by tagging work requests with `userId` and rejecting on mismatch in the worker's first step.
- **Variant or tenant change without logout** — explicitly out of scope. The user-visible operation is logout-then-login.

---

## 8. Cross-references

- Where the boot is invoked from: [08 — `:app`](08-app-orchestrator.md)
- The `RuntimeConfig` that drives the gate: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` and institution switching: [12 — Departments and Session](12-departments-and-session.md)
- The tenant resolution path: [19 — Tenants and Variants](19-tenants-and-variants.md)
