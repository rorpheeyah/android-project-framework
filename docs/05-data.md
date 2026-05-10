# 05 · `:data` — The Data Layer

> **Type:** Local Android library
> **Role:** The Retrofit interfaces, DTOs, and repository implementations. Variant-agnostic.
> **Constraint:** Implements `:core` repository interfaces. Does not implement policy interfaces.

---

## 1. Purpose

`:data` is where the wire meets the rest of the app. It owns:

- **`Fintech*Api` interfaces** — the team's unified backend, split by feature area (auth, transfer, account, …) for ergonomics. *One backend, many Retrofit interfaces — not one mega-interface.*
- **DTOs** — request/response data classes the API serializes to JSON, organized by feature.
- **Mappers** — extension functions that translate DTOs to `:core` domain models.
- **`Fintech*Repo` implementations** — classes that implement `:core` repository interfaces by calling the corresponding `Fintech*Api`.
- **`DataModule`** — the single Hilt module that exposes the repo bindings and provides each `*Api`.

The defining property: **everything in `:data` is the same regardless of which variant the user belongs to.** The server demuxes by user identity (auth token + active account); the Android side calls the same endpoints with the same DTOs, no per-bank routing on the client.

---

## 2. Module Layout

The internal structure is organized by **feature area** so that `:data` scales as the API surface grows. New endpoints land in the existing feature area, or a new feature area gets its own `*Api`/`*Repo` pair.

```
:data/
└── src/main/kotlin/com/<org>/data/
    ├── api/
    │   ├── FintechAuthApi.kt          # /v1/auth/...
    │   ├── FintechTransferApi.kt      # /v1/transfer/...
    │   ├── FintechAccountApi.kt       # /v1/accounts/...
    │   ├── FintechCardApi.kt          # /v1/cards/...
    │   └── dto/
    │       ├── auth/
    │       │   ├── LoginRequest.kt
    │       │   ├── LoginResponse.kt
    │       │   └── OtpHandleDto.kt
    │       ├── transfer/
    │       │   ├── TransferRequest.kt
    │       │   ├── TransferResponse.kt
    │       │   └── FeeQuoteDto.kt
    │       ├── account/
    │       │   ├── AccountBalanceDto.kt
    │       │   └── TransactionPageDto.kt
    │       └── shared/
    │           └── EmptyResponse.kt
    ├── repo/
    │   ├── FintechAuthRepo.kt         # implements AuthRepository
    │   ├── FintechTransferRepo.kt     # implements TransferRepository
    │   ├── FintechAccountRepo.kt      # implements AccountRepository
    │   ├── FintechCardRepo.kt         # implements CardRepository
    │   └── mapping/
    │       ├── AuthMapping.kt         # DTO → :core domain extensions
    │       ├── TransferMapping.kt
    │       └── AccountMapping.kt
    └── di/
        └── DataModule.kt              # @Module @InstallIn(LoggedInComponent::class)
```

| Layer | Purpose | Visibility |
|---|---|---|
| `api/` | Retrofit interfaces, one per feature area | `internal` |
| `api/dto/` | DTOs grouped by feature | `internal` |
| `repo/` | Repository impls — one per `:core` repository interface | `internal` |
| `repo/mapping/` | DTO → domain extensions, grouped by feature | `internal` |
| `di/` | The single public surface | `public` |

Only `DataModule` is visible outside `:data`. Everything else is `internal` so consumers can't accidentally bind to a concrete repo class.

---

## 3. The Retrofit Surface

The principle is *one server, one set of DTOs, no per-variant duplication* — **not** one Kotlin interface. Splitting Retrofit interfaces by feature area is purely an ergonomic choice; you get type-grouped imports, smaller per-file diffs, and tighter test scopes.

```kotlin
// :data/api/FintechAuthApi.kt
internal interface FintechAuthApi {
    @POST("v1/auth/login")     suspend fun login(@Body req: LoginRequest): LoginResponse
    @POST("v1/auth/logout")    suspend fun logout(): EmptyResponse
    @POST("v1/auth/otp")       suspend fun requestOtp(@Body req: OtpRequest): OtpHandleDto
    @POST("v1/auth/otp/verify")suspend fun verifyOtp(@Body req: VerifyOtpRequest): UserSessionDto
    @POST("v1/auth/refresh")   suspend fun refresh(@Body req: RefreshRequest): UserSessionDto
}

// :data/api/FintechTransferApi.kt
internal interface FintechTransferApi {
    @POST("v1/transfer/resolve-qr")  suspend fun resolveQr(@Body req: ResolveQrRequest): BeneficiaryDto
    @POST("v1/transfer/submit")      suspend fun submit(@Body req: TransferRequest): TransferResponse
    @GET("v1/transfer/fee-quote")    suspend fun feeQuote(
        @Query("amount") amount: String, @Query("currency") currency: String,
    ): FeeQuoteDto
    @GET("v1/transfer/history")      suspend fun history(@Query("page") page: Int): TransferHistoryDto
}

// :data/api/FintechAccountApi.kt
internal interface FintechAccountApi {
    @GET("v1/accounts/balances")            suspend fun balances(): List<AccountBalanceDto>
    @GET("v1/accounts/{id}/history")        suspend fun history(
        @Path("id") accountId: String, @Query("page") page: Int,
    ): TransactionPageDto
    @GET("v1/accounts/{id}/statement")      suspend fun statement(
        @Path("id") accountId: String, @Query("from") fromIso: String, @Query("to") toIso: String,
    ): StatementDto
}
```

What's **not** here: no `KhBakong*` methods, no `VnNapas*` methods, no per-bank routing on the client. Every endpoint is `/v1/...`; the server reads the auth token + `X-Account-Id` header (stamped by `AccountIdInterceptor` — see [12 — Departments and Session](12-departments-and-session.md)) and dispatches to the right corporate / rail itself.

### When to add a new `*Api` interface

| Situation | Action |
|---|---|
| New endpoint within an existing area (auth, transfer, account, …) | Add a method to the existing `Fintech<Area>Api` |
| New feature area (e.g., cards, statements, payee management) | New `Fintech<Area>Api.kt` |
| Endpoint shared by multiple repos (rare, e.g. an internal `/auth/refresh`) | Stays in its natural area; multiple repos can inject the same `*Api` |

Avoid creating an interface per endpoint — that's the opposite over-correction.

---

## 4. Repository Implementation Pattern

Every `Fintech*Repo` class:

1. Implements one or more `:core` interfaces.
2. Holds an injected `Fintech*Api` for its feature area (and possibly more if it crosses boundaries).
3. Reads `Session.activeAccountId` for account-scoped requests.
4. Maps DTOs to domain models in dedicated extension functions.
5. Wraps results in `Result<T>` — never throws across the module boundary.

```kotlin
// :data/repo/FintechTransferRepo.kt
internal class FintechTransferRepo @Inject constructor(
    private val api: FintechTransferApi,
    private val session: Session,
) : TransferRepository {

    override suspend fun resolveBeneficiary(qrPayload: String): Result<Beneficiary> = runCatching {
        api.resolveQr(ResolveQrRequest(qrPayload)).toDomain()
    }

    override suspend fun submit(intent: TransferIntent): Result<TransferReceipt> = runCatching {
        val request = TransferRequest.from(intent, session.activeAccountId.value)
        api.submit(request).toDomain()
    }

    override fun feeQuote(amount: Money): Flow<FeeQuote> = flow {
        emit(api.feeQuote(amount.value.toPlainString(), amount.currency.code).toDomain())
    }
}

// :data/repo/FintechAuthRepo.kt
internal class FintechAuthRepo @Inject constructor(
    private val api: FintechAuthApi,
    private val encryptedPrefs: EncryptedPrefs,
) : AuthRepository {

    override suspend fun login(credential: LoginCredential): Result<LoginResponse> = runCatching {
        api.login(LoginRequest.from(credential)).also { encryptedPrefs.put(Keys.AUTH_TOKEN, it.token) }.toDomain()
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        api.logout()
        encryptedPrefs.clearSessionScope()
    }
    // … requestOtp, verifyOtp …
}
```

The repo does **not** know about variants. The active `VariantContext` is available via Hilt (bound into `LoggedInComponent`) if a request needs it for the request body, but typically the server derives the variant from auth alone.

---

## 5. The Hilt Binding Module

`DataModule` is the only public surface of `:data`. It both binds repository implementations to their `:core` interfaces and provides each `Fintech*Api` instance.

```kotlin
@Module
@InstallIn(LoggedInComponent::class)
abstract class DataModule {

    @Binds @LoggedInScoped abstract fun authRepo(impl: FintechAuthRepo): AuthRepository
    @Binds @LoggedInScoped abstract fun transferRepo(impl: FintechTransferRepo): TransferRepository
    @Binds @LoggedInScoped abstract fun accountRepo(impl: FintechAccountRepo): AccountRepository
    @Binds @LoggedInScoped abstract fun cardRepo(impl: FintechCardRepo): CardRepository

    companion object {
        // One Retrofit instance, shared across all *Api interfaces (same base URL).
        @Provides @LoggedInScoped
        fun fintechRetrofit(
            retrofitFactory: RetrofitFactory,
            runtimeConfig: RuntimeConfig,
        ): Retrofit = retrofitFactory.builder(runtimeConfig.urls.main).build()

        @Provides @LoggedInScoped
        fun authApi(retrofit: Retrofit): FintechAuthApi = retrofit.create(FintechAuthApi::class.java)

        @Provides @LoggedInScoped
        fun transferApi(retrofit: Retrofit): FintechTransferApi = retrofit.create(FintechTransferApi::class.java)

        @Provides @LoggedInScoped
        fun accountApi(retrofit: Retrofit): FintechAccountApi = retrofit.create(FintechAccountApi::class.java)

        @Provides @LoggedInScoped
        fun cardApi(retrofit: Retrofit): FintechCardApi = retrofit.create(FintechCardApi::class.java)
    }
}
```

Why one `Retrofit` instance for all `*Api`s? They share the same base URL (`RuntimeConfig.urls.main`) and the same OkHttp client (cert pinning, interceptors). Splitting Retrofit instances would gain nothing and cost slightly more memory. If a future endpoint needs a different base URL (e.g., `RuntimeConfig.urls.auxiliary`), it gets its own provider.

`@InstallIn(LoggedInComponent::class)` puts every binding inside the session-scoped graph — they live as long as the user is logged in and become GC-eligible at logout.

---

## 6. Why `:data` Does Not Live Inside `:core`

`:core` is the contract layer — interfaces and immutable models. Every product module recompiles when `:core` changes. If repository impls and DTOs lived in `:core`, every API tweak (which happens often) would force a recompile of `:features`, every `:variants-*`, and `:app`.

By keeping `:data` separate:

- A DTO field rename recompiles `:data` and `:app` only.
- A new endpoint method recompiles `:data` and `:app` only.
- `:core` stays small, stable, and grep-able as "interfaces and types only."

---

## 7. Why `:data` Does Not Live Inside `:variants-*`

The unified server makes the API identical for every variant. Putting the repo impls in (say) `:variants-kh` would either:

- Require copying the same code into every variant module (duplicated maintenance), or
- Make `:variants-kh` the *only* repo provider, breaking when other variants log in.

`:data` providing the repo binding once for everyone is the only sensible shape.

---

## 8. What Does NOT Go In `:data`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Repository **interfaces** | `:core` |
| Domain models | `:core` |
| Variant-specific validation / fee rules | `:variants-{id}/policy/` |
| `OkHttpClient` configuration | `:aos-core` |
| `if (variantId == "kh") api.x() else api.y()` | nowhere — there is one server, server-side demux |
| MG endpoint URL | `:app` (build-time) |
| Variant-only Retrofit endpoints | `:features-{variant-feature}` (e.g. `:features-bakong-disputes`) — see [07](07-variants.md) §9 |

If a repo implementation needs a branch on variant identity, **the server is doing the wrong thing** — escalate to backend before adding the branch on the client.

---

## 9. Testing

`:data` is highly testable in isolation:

- **Unit tests** mock the relevant `Fintech*Api` (e.g. with MockK) and assert that `*Repo` classes map DTOs to domain models correctly.
- **Mapper tests** are pure JVM — given a DTO, return the expected domain model.
- **No Hilt graph required** for `:data` tests; instances are constructed directly.

```kotlin
class FintechTransferRepoTest {
    private val api = mockk<FintechTransferApi>()
    private val session = fakeSession(activeAccountId = AccountId("acc-001"))
    private val repo = FintechTransferRepo(api, session)

    @Test fun `submit maps response to TransferReceipt`() = runTest {
        coEvery { api.submit(any()) } returns transferResponseFixture()
        val result = repo.submit(transferIntentFixture())
        assertTrue(result.isSuccess)
        assertEquals("rcpt-123", result.getOrNull()!!.id)
    }
}
```

Run with `./gradlew :data:test`.

---

## 10. Cross-references

- The interfaces `:data` implements: [03 — `:core`](03-core.md)
- The infrastructure `:data` builds on: [02 — `:aos-core`](02-aos-core.md)
- The `Session` and account interceptor that `:data` reads: [12 — Departments and Session](12-departments-and-session.md)
- The MG-derived URL that the Retrofit instance is bound against: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- Variant-unique endpoints (don't go here): [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md)
