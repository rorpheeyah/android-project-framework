# 12 · Departments and Session

> One logged-in user, multiple accounts. Switching accounts is a session-level state change — not a variant change, not a DI rebuild.

---

## 1. The Concept

After a successful login, the server returns:

```kotlin
data class LoginResponse(
    val userSession: UserSession,
    val accounts: List<DepartmentAccount>,
    val variantId: VariantId,
)

data class DepartmentAccount(
    val id: AccountId,
    val displayName: String,        // "Personal", "Acme Corp", "Joint Account"
    val accountType: AccountType,   // Personal | Corporate | Joint
    val currency: Currency,
)
```

The user picks (or defaults to) one of these accounts; that choice becomes `Session.activeAccountId`. Every authenticated request stamps that account ID via an OkHttp interceptor.

This is **not** multi-variant:

- All accounts belong to one logged-in user under one variant.
- The same `LoggedInComponent` serves them all — no rebuild, no purge.
- The repos in `:data` already know how to scope by account ID; they read `session.activeAccountId.value` per call.

---

## 2. The `Session` Type

```kotlin
// :core/session/Session.kt
class Session(
    val userSession: UserSession,
    val variantContext: VariantContext,
    val accounts: List<DepartmentAccount>,
    initialActiveAccount: AccountId,
) {
    private val _activeAccountId = MutableStateFlow(initialActiveAccount)
    val activeAccountId: StateFlow<AccountId> = _activeAccountId.asStateFlow()

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
) {
    fun build(login: LoginResponse): Session = Session(
        userSession          = login.userSession,
        variantContext       = variantContextResolver.resolve(login.variantId),
        accounts             = login.accounts,
        initialActiveAccount = login.accounts.first().id,   // server may dictate; this is the fallback
    )
}
```

`SessionFactory.build()` is called from `BootCoordinator.onLoginSuccess(...)`. The default initial account is the first in the list; product can override (e.g., "last-used account" persisted to `EncryptedPrefs`) by reading prefs before `Session` construction.

---

## 3. The Account-ID Interceptor

Every authenticated request must carry the active account ID. An OkHttp interceptor reads it from `Session` at call time:

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
            chain.request().newBuilder()
                .header("X-Account-Id", sessionOrNull.activeAccountId.value.value)
                .build()
        } else {
            chain.request()  // pre-login (e.g., the login call itself)
        }
        return chain.proceed(request)
    }
}
```

Reading the StateFlow's `.value` at call time means the next request always sees the latest active account, with no need to recreate the OkHttp client when the user switches.

---

## 4. The Account Switcher (UI)

A dropdown / bottom sheet in `:features/account/`:

```kotlin
internal data class AccountSwitcherState(
    val accounts: List<DepartmentAccount> = emptyList(),
    val activeId: AccountId? = null,
) : UiState

internal sealed interface AccountSwitcherEvent : UiEvent {
    data class SelectAccount(val id: AccountId) : AccountSwitcherEvent
}

@HiltViewModel
internal class AccountSwitcherViewModel @Inject constructor(
    private val session: Session,   // resolved from LoggedInComponent
) : MviViewModel<AccountSwitcherState, AccountSwitcherEvent, Nothing>(
    initial = AccountSwitcherState(session.accounts, session.activeAccountId.value)
) {

    init {
        viewModelScope.launch {
            session.activeAccountId.collect { active ->
                setState { copy(activeId = active) }
            }
        }
    }

    override fun onEvent(event: AccountSwitcherEvent) = when (event) {
        is AccountSwitcherEvent.SelectAccount -> session.switchAccount(event.id)
    }
}
```

Switching is instantaneous from the user's perspective — there is no DI rebuild, no network call (until the next data refresh, which now stamps the new account ID).

---

## 5. What Switching an Account Does NOT Do

| Concern | Behavior |
|---|---|
| The `LoggedInComponent` | Stays. No rebuild. |
| Repositories (in `:data`) | Same instances. They re-read `session.activeAccountId` per call. |
| Variant policies | Stay. Variant doesn't change with the account. |
| OkHttp client / cache | Stays. The interceptor stamps the new ID. |
| ViewModels | Stay. They observe `session.activeAccountId` if relevant. |
| EncryptedPrefs | Stay. Tokens are user-scoped, not account-scoped. |
| Cached account-scoped data | Each ViewModel must invalidate its own state on `activeAccountId` change. The framework does not auto-clear screens. |

That last point is the only foot-gun: a ViewModel that displays balance for the active account must collect `activeAccountId` and re-fetch when it changes. Convention: any feature that's account-scoped owns this responsibility in its `init { … }`.

---

## 6. Why Departments Are Not Sub-Variants

A reasonable-looking alternative is to treat each department as a sub-variant with its own DI graph. We rejected this because:

- All accounts under a login share **the same backend**, **the same auth token**, **the same variant rules**.
- The only request-level difference is the account ID header — a parameter, not a DI graph.
- A nested DI structure pays a runtime-rebuild cost (component lifecycle, instance churn) for what is structurally a single value flip.

The principle: **scope a value, not a graph, when the only difference is a parameter on requests.**

---

## 7. Cross-references

- The repositories that read `session.activeAccountId`: [05 — `:data`](05-data.md)
- The variant policies that may also read `Session` indirectly: [07 — `:variants-*`](07-variants.md)
- The `LoggedInComponent` that holds `Session`: [10 — Boot Phases](10-boot-phases.md)
- The `:core` types referenced here: [03 — `:core`](03-core.md)
