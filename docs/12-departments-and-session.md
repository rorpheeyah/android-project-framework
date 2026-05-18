# 12 · Departments and Session

> One logged-in user, multiple institution memberships. Switching the active institution (today's `USE_INTT_ID` flip via `SelectUserInttIdActivity`) is a session-level state change — not a variant change, not a tenant change, not a DI rebuild.

---

## 1. The Concept

After a successful login, the server returns:

```kotlin
data class LoginResponse(
    val userSession: UserSession,
    val variantId: VariantId,
    val tenantId: TenantId,
    val tenantFlags: TenantFlags,
    val tenantParams: TenantParams,
    val accounts: List<DepartmentAccount>,
)

data class DepartmentAccount(
    val id: AccountId,              // wraps the USE_INTT_ID string
    val displayName: String,        // "POSCO ICT — Seoul HQ", "Lotte E&C — Procurement"
    val companyCode: String,        // today's COMPANY_CD
    val divisionCode: String?,      // today's DVSN_CD
    val divisionName: String?,      // today's DVSN_NM
    val businessName: String,       // today's BSNN_NM
    val businessRegNo: String,      // today's BSNN_NO
    val currency: Currency,
)
```

The user picks (or defaults to) one of these institution memberships — that flow is what today's `SelectUserInttIdActivity` does, and is rebuilt as `InstitutionPickerScreen` in `:features/auth/institutionpicker/`. That choice becomes `Session.activeAccountId`. Every authenticated request stamps the corresponding `USE_INTT_ID` + `COMPANY_CD` via an OkHttp interceptor.

This is **not** multi-variant and **not** multi-tenant:

- All institution memberships belong to one logged-in user, under one variant *and* one tenant. (A user who legitimately belongs to two tenants — e.g. a consultant working with POSCO ICT *and* Lotte simultaneously — logs in twice with separate credentials. That's a tenant change, which is a logout-login.)
- The same `LoggedInComponent` serves them all — no rebuild, no purge.
- The repos in `:data` already know how to scope by institution; they read `session.activeAccountId.value` per call, and the interceptor stamps the headers.

---

## 2. The `Session` Type

```kotlin
// :core/session/Session.kt
class Session(
    val userSession: UserSession,
    val variantContext: VariantContext,
    val tenantContext: TenantContext,
    val accounts: List<DepartmentAccount>,
    initialActiveAccount: AccountId,
) {
    private val _activeAccountId = MutableStateFlow(initialActiveAccount)
    val activeAccountId: StateFlow<AccountId> = _activeAccountId.asStateFlow()

    fun activeAccount(): DepartmentAccount =
        accounts.first { it.id == _activeAccountId.value }

    fun switchAccount(target: AccountId) {
        require(accounts.any { it.id == target }) { "Account $target not in user's accounts" }
        _activeAccountId.value = target
    }
}
```

`Session` is `@LoggedInScoped` — built by `SessionFactory` in `:app` once at login, dropped at logout.

### 2.1 `SessionFactory`

```kotlin
// :app/session/SessionFactory.kt
@Singleton
class SessionFactory @Inject constructor(
    private val variantContextResolver: VariantContextResolver,
    private val tenantContextResolver: TenantContextResolver,
    private val encryptedPrefs: EncryptedPrefs,
) {
    fun build(login: LoginResponse): Session {
        val variant = variantContextResolver.resolve(login.variantId)
        val tenant  = tenantContextResolver.resolve(login.variantId, login.tenantId, login.tenantFlags, login.tenantParams)
        // last-used account if persisted, otherwise the first one (server may also dictate)
        val initial = encryptedPrefs.getString(Keys.LAST_ACTIVE_ACCOUNT)
            ?.let(::AccountId)
            ?.takeIf { id -> login.accounts.any { it.id == id } }
            ?: login.accounts.first().id

        return Session(
            userSession          = login.userSession,
            variantContext       = variant,
            tenantContext        = tenant,
            accounts             = login.accounts,
            initialActiveAccount = initial,
        )
    }
}
```

`SessionFactory.build()` is called from `BootCoordinator.onLoginSuccess(...)`. The default initial account is "last-used" persisted to `EncryptedPrefs`; falls back to `accounts.first()`.

---

## 3. The Account-ID Interceptor

Every authenticated request must carry the active institution membership. An OkHttp interceptor reads it from `Session` at call time and stamps both `USE_INTT_ID` and `COMPANY_CD` headers — matching the existing IPPP backend contract:

```kotlin
// :app/session/AccountIdInterceptor.kt
@Singleton
class AccountIdInterceptor @Inject constructor(
    private val componentManager: LoggedInComponentManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val sessionOrNull = runCatching {
            EntryPoints.get(componentManager.current(), LoggedInEntryPoint::class.java).session()
        }.getOrNull()

        val request = if (sessionOrNull != null) {
            val active = sessionOrNull.activeAccount()
            chain.request().newBuilder()
                .header("X-Use-Intt-Id", active.id.value)
                .header("X-Company-Cd",  active.companyCode)
                .apply { active.divisionCode?.let { header("X-Dvsn-Cd", it) } }
                .build()
        } else {
            chain.request()  // pre-login (e.g., the login call itself)
        }
        return chain.proceed(request)
    }
}
```

Reading the StateFlow's `.value` at call time means the next request always sees the latest active institution, with no need to recreate the OkHttp client when the user switches.

> **Header names are chosen to match today's IPPP backend.** If the backend later renames the contract to e.g. `X-Account-Id`, only this interceptor changes — no feature code is affected.

---

## 4. The Institution Switcher (UI)

A dropdown / bottom sheet in `:features/account/switcher/`. This is the Compose successor to today's `SelectUserInttIdActivity`, but with the key difference that it operates **inside** a logged-in session (changing the active account) — `SelectUserInttIdActivity` today *also* runs at login. Both flows use the same data, just at different lifecycle points.

```kotlin
internal data class InstitutionSwitcherState(
    val accounts: List<DepartmentAccount> = emptyList(),
    val activeId: AccountId? = null,
) : UiState

internal sealed interface InstitutionSwitcherEvent : UiEvent {
    data class SelectAccount(val id: AccountId) : InstitutionSwitcherEvent
}

@HiltViewModel
internal class InstitutionSwitcherViewModel @Inject constructor(
    private val session: Session,   // resolved from LoggedInComponent
    private val encryptedPrefs: EncryptedPrefs,
) : MviViewModel<InstitutionSwitcherState, InstitutionSwitcherEvent, Nothing>(
    initial = InstitutionSwitcherState(session.accounts, session.activeAccountId.value)
) {

    init {
        viewModelScope.launch {
            session.activeAccountId.collect { active ->
                setState { copy(activeId = active) }
                encryptedPrefs.put(Keys.LAST_ACTIVE_ACCOUNT, active.value)
            }
        }
    }

    override fun onEvent(event: InstitutionSwitcherEvent) = when (event) {
        is InstitutionSwitcherEvent.SelectAccount -> session.switchAccount(event.id)
    }
}
```

Switching is instantaneous from the user's perspective — there is no DI rebuild, no network call (until the next data refresh, which now stamps the new institution).

---

## 5. What Switching an Account Does NOT Do

| Concern | Behavior |
|---|---|
| The `LoggedInComponent` | Stays. No rebuild. |
| Repositories (in `:data`) | Same instances. They re-read `session.activeAccountId` per call. |
| Variant policies | Stay. Variant doesn't change with the active account. |
| Tenant context | Stays. Tenant doesn't change with the active account either — both POSCO ICT memberships and a hypothetical other-tenant membership would have triggered a *separate login* in the first place. |
| OkHttp client / cache | Stays. The interceptor stamps the new headers. |
| ViewModels | Stay. They observe `session.activeAccountId` if relevant. |
| EncryptedPrefs | Stay. Tokens are user-scoped, not account-scoped. |
| Cached account-scoped data (receipt list, approval inbox) | Each ViewModel must invalidate its own state on `activeAccountId` change. The framework does not auto-clear screens. |

That last point is the only foot-gun: a ViewModel that displays the receipt list for the active institution must collect `activeAccountId` and re-fetch when it changes. Convention: any feature that's account-scoped owns this responsibility in its `init { … }`.

```kotlin
// :features/receipt/list/ReceiptListViewModel.kt — example of correct invalidation
init {
    viewModelScope.launch {
        session.activeAccountId.collect {
            setState { copy(items = emptyList(), loading = true) }
            refresh()
        }
    }
}
```

---

## 6. Why Departments Are Not Sub-Variants (or Sub-Tenants)

A reasonable-looking alternative is to treat each institution membership as a sub-variant or sub-tenant with its own DI graph. We rejected this because:

- All institution memberships under a login share **the same backend**, **the same auth token**, **the same variant rules**, **the same tenant flags / params**.
- The only request-level difference is the institution headers — parameters, not a DI graph.
- A nested DI structure pays a runtime-rebuild cost (component lifecycle, instance churn) for what is structurally a single value flip.

The principle: **scope a value, not a graph, when the only difference is a parameter on requests.**

> **What if a user genuinely belongs to two different tenants?** They log in twice with separate credentials. That triggers two logout-login cycles and two distinct `LoggedInComponent`s — exactly the *right* behavior, because the variant/tenant policies, approval-line shapes, and field visibility could be completely different.

---

## 7. Cross-references

- The repositories that read `session.activeAccountId`: [05 — `:data`](05-data.md)
- The variant policies that may also read `Session` indirectly (e.g. for currency context): [07 — `:variants-*`](07-variants.md)
- The `LoggedInComponent` that holds `Session`: [10 — Boot Phases](10-boot-phases.md)
- The `:core` types referenced here: [03 — `:core`](03-core.md)
- The tenant axis (which institution membership is a *third* concept beneath): [19 — Tenants and Variants](19-tenants-and-variants.md)
