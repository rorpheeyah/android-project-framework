# 05 · `:data` — The Data Layer

> **Type:** Local Android library
> **Role:** The Retrofit interfaces, DTOs, and repository implementations. Tenant-agnostic.
> **Constraint:** Implements `:core` repository interfaces. Does not implement policy interfaces.

---

## 1. Purpose

`:data` is where the wire meets the rest of the app. It owns:

- **`Fintech*Api` interfaces** — the team's unified backend, split by feature area (auth, loan, repayment, guarantor, kyc, referral, …) for ergonomics. *One backend, many Retrofit interfaces — not one mega-interface.*
- **`:data/external/`** — typed clients for **third-party backends** that are not the primary fintech server: credit bureau (CBC), bank-statement analyzers, MWL agency systems. Same shape as `Fintech*Api` (Retrofit + DTOs + mapping), but talking to a different host.
- **`:data/chat/`** — the chat repository implementation, which is provider-bound (Sendbird) and therefore lives separately from the unified fintech surface. Still tenant-agnostic — every tenant uses the same chat backend.
- **DTOs** — request/response data classes the API serializes to JSON, organized by feature.
- **Mappers** — extension functions that translate DTOs to `:core` domain models.
- **`*Repo` implementations** — classes that implement `:core` repository interfaces by calling the corresponding `*Api`.
- **`DataModule`** — the single Hilt module that exposes the repo bindings and provides each `*Api`.

The defining property: **everything in `:data` is the same regardless of which tenant the user belongs to.** The server demuxes by user identity (auth token + active account); the Android side calls the same endpoints with the same DTOs, no per-tenant routing on the client.

---

## 2. Module Layout

The internal structure is organized by **feature area** so that `:data` scales as the API surface grows. New endpoints land in the existing feature area, or a new feature area gets its own `*Api`/`*Repo` pair.

```
:data/
└── src/main/kotlin/com/<org>/data/
    ├── api/
    │   ├── FintechAuthApi.kt           # /v1/auth/...
    │   ├── FintechLoanApi.kt           # /v1/loans/...               (products, my loans)
    │   ├── FintechLoanApplicationApi.kt# /v1/loan-applications/...   (apply, submit, status)
    │   ├── FintechRepaymentApi.kt      # /v1/repayments/...
    │   ├── FintechGuarantorApi.kt      # /v1/guarantors/...
    │   ├── FintechKycApi.kt            # /v1/kyc/...                 (upload, status)
    │   ├── FintechReferralApi.kt       # /v1/referrals/...
    │   ├── FintechConsultationApi.kt   # /v1/consultations/...
    │   ├── FintechBranchApi.kt         # /v1/branches/...            (locator data)
    │   └── dto/
    │       ├── auth/
    │       │   ├── LoginRequest.kt
    │       │   ├── LoginResponse.kt
    │       │   └── OtpHandleDto.kt
    │       ├── loan/
    │       │   ├── LoanProductDto.kt
    │       │   ├── LoanDto.kt
    │       │   └── LoanStatusDto.kt
    │       ├── application/
    │       │   ├── LoanApplicationRequest.kt
    │       │   ├── LoanApplicationResponse.kt
    │       │   └── BorrowerInfoDto.kt
    │       ├── repayment/
    │       │   ├── RepaymentScheduleDto.kt
    │       │   ├── InstallmentDto.kt
    │       │   └── PaymentReceiptDto.kt
    │       ├── guarantor/
    │       │   ├── GuarantorInviteRequest.kt
    │       │   └── GuarantorVerificationDto.kt
    │       ├── kyc/
    │       │   ├── KycUploadRequest.kt        # multipart wrapper
    │       │   └── KycSubmissionDto.kt
    │       └── shared/
    │           └── EmptyResponse.kt
    ├── external/                              # NOT the primary fintech backend
    │   ├── CbcApi.kt                          # Credit Bureau Cambodia
    │   ├── BankStatementAnalyzerApi.kt
    │   ├── MwlAgencyApi.kt                    # migrant-worker-loan agency systems
    │   └── dto/
    │       ├── cbc/
    │       ├── statement/
    │       └── mwl/
    ├── chat/
    │   └── SendbirdChatRepo.kt                # implements ChatRepository (provider-bound)
    ├── repo/
    │   ├── FintechAuthRepo.kt                 # implements AuthRepository
    │   ├── LoanRepo.kt                        # implements LoanRepository
    │   ├── LoanApplicationRepo.kt             # implements LoanApplicationRepository
    │   ├── RepaymentRepo.kt                   # implements RepaymentRepository
    │   ├── GuarantorRepo.kt                   # implements GuarantorRepository
    │   ├── KycRepo.kt                         # implements KycRepository
    │   ├── ReferralRepo.kt                    # implements ReferralRepository
    │   ├── ConsultationRepo.kt                # implements ConsultationRepository
    │   ├── BranchRepo.kt                      # implements BranchRepository
    │   ├── CbcRepo.kt                         # implements CbcRepository (calls :data/external/CbcApi)
    │   └── mapping/
    │       ├── AuthMapping.kt                 # DTO → :core domain extensions
    │       ├── LoanMapping.kt
    │       ├── ApplicationMapping.kt
    │       ├── RepaymentMapping.kt
    │       ├── GuarantorMapping.kt
    │       ├── KycMapping.kt
    │       └── CbcMapping.kt
    └── di/
        └── DataModule.kt                       # @Module @InstallIn(LoggedInComponent::class)
```

| Layer | Purpose | Visibility |
|---|---|---|
| `api/` | Retrofit interfaces for the unified fintech backend, one per feature area | `internal` |
| `api/dto/` | DTOs grouped by feature | `internal` |
| `external/` | Third-party backend clients (CBC, statement analyzer, MWL agency) | `internal` |
| `chat/` | Provider-bound chat repository (Sendbird) | `internal` |
| `repo/` | Repository impls — one per `:core` repository interface | `internal` |
| `repo/mapping/` | DTO → domain extensions, grouped by feature | `internal` |
| `di/` | The single public surface | `public` |

Only `DataModule` is visible outside `:data`. Everything else is `internal` so consumers can't accidentally bind to a concrete repo class.

---

## 3. The Retrofit Surface

The principle is *one primary fintech backend per logical capability, no per-tenant duplication* — **not** one Kotlin interface. Splitting Retrofit interfaces by feature area is purely an ergonomic choice; you get type-grouped imports, smaller per-file diffs, and tighter test scopes.

```kotlin
// :data/api/FintechAuthApi.kt
internal interface FintechAuthApi {
    @POST("v1/auth/login")      suspend fun login(@Body req: LoginRequest): LoginResponse
    @POST("v1/auth/logout")     suspend fun logout(): EmptyResponse
    @POST("v1/auth/otp")        suspend fun requestOtp(@Body req: OtpRequest): OtpHandleDto
    @POST("v1/auth/otp/verify") suspend fun verifyOtp(@Body req: VerifyOtpRequest): UserSessionDto
    @POST("v1/auth/refresh")    suspend fun refresh(@Body req: RefreshRequest): UserSessionDto
    @POST("v1/auth/pin")        suspend fun setPin(@Body req: SetPinRequest): EmptyResponse
    @POST("v1/auth/pin/verify") suspend fun verifyPin(@Body req: VerifyPinRequest): EmptyResponse
}

// :data/api/FintechLoanApi.kt
internal interface FintechLoanApi {
    @GET("v1/loans/products")       suspend fun products(): List<LoanProductDto>
    @GET("v1/loans/products/{id}")  suspend fun productDetail(@Path("id") id: String): LoanProductDto
    @GET("v1/loans/mine")           suspend fun myLoans(): List<LoanDto>
    @GET("v1/loans/{id}")           suspend fun loan(@Path("id") id: String): LoanDto
    @GET("v1/loans/{id}/contract")  @Streaming suspend fun contractPdf(@Path("id") id: String): ResponseBody
}

// :data/api/FintechLoanApplicationApi.kt
internal interface FintechLoanApplicationApi {
    @POST("v1/loan-applications")            suspend fun submit(@Body req: LoanApplicationRequest): LoanApplicationResponse
    @GET("v1/loan-applications/{id}")        suspend fun status(@Path("id") id: String): LoanApplicationResponse
    @POST("v1/loan-applications/{id}/accept") suspend fun accept(@Path("id") id: String): EmptyResponse
}

// :data/api/FintechRepaymentApi.kt
internal interface FintechRepaymentApi {
    @GET("v1/repayments/loans/{loanId}/schedule") suspend fun schedule(
        @Path("loanId") loanId: String,
    ): RepaymentScheduleDto

    @POST("v1/repayments/installments/{id}/pay") suspend fun pay(
        @Path("id") installmentId: String,
        @Body req: PaymentRequest,
    ): PaymentReceiptDto

    @POST("v1/repayments/loans/{loanId}/payoff") suspend fun payoff(
        @Path("loanId") loanId: String,
    ): PayoffQuoteDto
}

// :data/api/FintechGuarantorApi.kt
internal interface FintechGuarantorApi {
    @POST("v1/guarantors/invite")               suspend fun invite(@Body req: GuarantorInviteRequest): GuarantorInviteResponse
    @GET("v1/guarantors/{id}/verification")     suspend fun verification(
        @Path("id") guarantorId: String,
    ): GuarantorVerificationDto
}

// :data/api/FintechKycApi.kt
internal interface FintechKycApi {
    @Multipart
    @POST("v1/kyc/upload")           suspend fun upload(
        @Part("type") type: RequestBody,
        @Part document: MultipartBody.Part,
    ): KycSubmissionDto

    @GET("v1/kyc/submissions/{id}")  suspend fun submission(@Path("id") id: String): KycSubmissionDto
}
```

What's **not** here: no `KhBakong*` methods, no `VnNapas*` methods, no per-bank routing on the client. Every endpoint is `/v1/...`; the server reads the auth token + `X-Account-Id` header (stamped by `AccountIdInterceptor` — see [12 — Departments and Session](12-departments-and-session.md)) and dispatches to the right downstream system itself.

### Third-party clients in `:data/external/`

Some integrations are not served by the primary fintech backend. They live in `:data/external/` with their own base URL (sourced from MG's `RuntimeConfig`, never BuildConfig):

```kotlin
// :data/external/CbcApi.kt
internal interface CbcApi {
    @GET("credit-report/{nationalId}") suspend fun report(@Path("nationalId") nid: String): CbcReportDto
}

// :data/external/BankStatementAnalyzerApi.kt
internal interface BankStatementAnalyzerApi {
    @Multipart
    @POST("analyze") suspend fun analyze(@Part file: MultipartBody.Part): IncomeAnalysisDto
}
```

These get their own Retrofit instances (different base URL, possibly different cert pin) provided in `DataModule`. The repos that consume them (`CbcRepo`, `BankStatementAnalyzerRepo`) still implement `:core` interfaces — the consumer doesn't know whether a repo talks to the primary backend or to a third party.

### When to add a new `*Api` interface

| Situation | Action |
|---|---|
| New endpoint within an existing area (auth, loan, repayment, …) | Add a method to the existing `Fintech<Area>Api` |
| New feature area on the primary backend (e.g., insurance) | New `Fintech<Area>Api.kt` |
| Integration with a **non-primary backend** (CBC, MWL agency, third-party scoring) | New file in `:data/external/` |
| Provider-bound capability (chat, push) | Lives in its own subdirectory (`:data/chat/`, `:data/push/`) |
| Endpoint shared by multiple repos (rare, e.g. an internal `/auth/refresh`) | Stays in its natural area; multiple repos can inject the same `*Api` |

Avoid creating an interface per endpoint — that's the opposite over-correction.

---

## 4. Repository Implementation Pattern

Every `*Repo` class:

1. Implements one or more `:core` interfaces.
2. Holds an injected `*Api` for its feature area (and possibly more if it crosses boundaries).
3. Reads `Session.activeAccountId` for account-scoped requests.
4. Maps DTOs to domain models in dedicated extension functions.
5. Wraps results in `Result<T>` — never throws across the module boundary.

```kotlin
// :data/repo/LoanApplicationRepo.kt
internal class LoanApplicationRepo @Inject constructor(
    private val api: FintechLoanApplicationApi,
    private val session: Session,
) : LoanApplicationRepository {

    override suspend fun submit(application: LoanApplication): Result<LoanApplicationId> = runCatching {
        val request = LoanApplicationRequest.from(application, session.activeAccountId.value)
        api.submit(request).id.let(::LoanApplicationId)
    }

    override fun applicationStatus(id: LoanApplicationId): Flow<LoanApplicationStatus> = flow {
        // Poll until terminal status; in production this would be FCM-pushed
        while (currentCoroutineContext().isActive) {
            val dto = api.status(id.value)
            emit(dto.toDomain())
            if (dto.status.isTerminal()) break
            delay(15.seconds)
        }
    }

    override suspend fun accept(id: LoanApplicationId): Result<Unit> = runCatching {
        api.accept(id.value)
    }
}

// :data/repo/RepaymentRepo.kt
internal class RepaymentRepo @Inject constructor(
    private val api: FintechRepaymentApi,
) : RepaymentRepository {

    override fun schedule(loanId: LoanId): Flow<RepaymentSchedule> = flow {
        emit(api.schedule(loanId.value).toDomain())
    }

    override suspend fun pay(installmentId: InstallmentId): Result<PaymentReceipt> = runCatching {
        api.pay(installmentId.value, PaymentRequest.standard()).toDomain()
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
    // … requestOtp, verifyOtp, setPin, verifyPin …
}
```

The repo does **not** know about tenants. The active `TenantContext` is available via Hilt (bound into `LoggedInComponent`) if a request needs it for the request body, but typically the server derives the routing from auth alone.

---

## 5. The Hilt Binding Module

`DataModule` is the only public surface of `:data`. It binds repository implementations to their `:core` interfaces and provides each `*Api` instance.

```kotlin
@Module
@InstallIn(LoggedInComponent::class)
abstract class DataModule {

    // Primary fintech repository bindings
    @Binds @LoggedInScoped abstract fun authRepo(impl: FintechAuthRepo): AuthRepository
    @Binds @LoggedInScoped abstract fun loanRepo(impl: LoanRepo): LoanRepository
    @Binds @LoggedInScoped abstract fun loanApplicationRepo(impl: LoanApplicationRepo): LoanApplicationRepository
    @Binds @LoggedInScoped abstract fun repaymentRepo(impl: RepaymentRepo): RepaymentRepository
    @Binds @LoggedInScoped abstract fun guarantorRepo(impl: GuarantorRepo): GuarantorRepository
    @Binds @LoggedInScoped abstract fun kycRepo(impl: KycRepo): KycRepository
    @Binds @LoggedInScoped abstract fun referralRepo(impl: ReferralRepo): ReferralRepository
    @Binds @LoggedInScoped abstract fun consultationRepo(impl: ConsultationRepo): ConsultationRepository
    @Binds @LoggedInScoped abstract fun branchRepo(impl: BranchRepo): BranchRepository

    // Third-party bindings
    @Binds @LoggedInScoped abstract fun cbcRepo(impl: CbcRepo): CbcRepository

    // Provider-bound bindings
    @Binds @LoggedInScoped abstract fun chatRepo(impl: SendbirdChatRepo): ChatRepository

    companion object {
        // One Retrofit instance for the primary fintech backend, shared across all Fintech*Api interfaces.
        @Provides @LoggedInScoped @FintechRetrofit
        fun fintechRetrofit(
            retrofitFactory: RetrofitFactory,
            runtimeConfig: RuntimeConfig,
        ): Retrofit = retrofitFactory.builder(runtimeConfig.urls.main).build()

        // Separate Retrofit per third-party host (different base URL, separate cert pin set).
        @Provides @LoggedInScoped @CbcRetrofit
        fun cbcRetrofit(
            retrofitFactory: RetrofitFactory,
            runtimeConfig: RuntimeConfig,
        ): Retrofit = retrofitFactory.builder(runtimeConfig.urls.cbc).build()

        @Provides @LoggedInScoped
        fun authApi(@FintechRetrofit r: Retrofit): FintechAuthApi = r.create(FintechAuthApi::class.java)

        @Provides @LoggedInScoped
        fun loanApi(@FintechRetrofit r: Retrofit): FintechLoanApi = r.create(FintechLoanApi::class.java)

        @Provides @LoggedInScoped
        fun loanAppApi(@FintechRetrofit r: Retrofit): FintechLoanApplicationApi =
            r.create(FintechLoanApplicationApi::class.java)

        @Provides @LoggedInScoped
        fun repaymentApi(@FintechRetrofit r: Retrofit): FintechRepaymentApi = r.create(FintechRepaymentApi::class.java)

        @Provides @LoggedInScoped
        fun guarantorApi(@FintechRetrofit r: Retrofit): FintechGuarantorApi = r.create(FintechGuarantorApi::class.java)

        @Provides @LoggedInScoped
        fun kycApi(@FintechRetrofit r: Retrofit): FintechKycApi = r.create(FintechKycApi::class.java)

        // … one provider per Fintech*Api

        @Provides @LoggedInScoped
        fun cbcApi(@CbcRetrofit r: Retrofit): CbcApi = r.create(CbcApi::class.java)
    }
}
```

Why one `Retrofit` instance for all primary `Fintech*Api`s? They share the same base URL (`RuntimeConfig.urls.main`) and the same OkHttp client (cert pinning, interceptors). Splitting Retrofit instances would gain nothing and cost slightly more memory. **Third-party hosts** (CBC, statement analyzer) get their own Retrofit because they have different base URLs (and possibly different cert pins).

`@InstallIn(LoggedInComponent::class)` puts every binding inside the session-scoped graph — they live as long as the user is logged in and become GC-eligible at logout.

---

## 6. Why `:data` Does Not Live Inside `:core`

`:core` is the contract layer — interfaces and immutable models. Every product module recompiles when `:core` changes. If repository impls and DTOs lived in `:core`, every API tweak (which happens often) would force a recompile of `:features`, every `:tenants:*:*`, and `:app`.

By keeping `:data` separate:

- A DTO field rename recompiles `:data` and `:app` only.
- A new endpoint method recompiles `:data` and `:app` only.
- `:core` stays small, stable, and grep-able as "interfaces and types only."

---

## 7. Why `:data` Does Not Live Inside `:tenants:*:*`

The unified server makes the API identical for every tenant. Putting the repo impls in (say) `:tenants:cambodia:nh` would either:

- Require copying the same code into every tenant module (duplicated maintenance), or
- Make `:tenants:cambodia:nh` the *only* repo provider, breaking when other tenants log in.

`:data` providing the repo binding once for everyone is the only sensible shape.

---

## 8. What Does NOT Go In `:data`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Repository **interfaces** | `:core` |
| Domain models | `:core` |
| Tenant-specific validation / fee rules | `:tenants:{region}:{tenantSlug}/policy/` or `:tenants:{region}:base/policy/` |
| `OkHttpClient` configuration | `:aos-sdk` |
| `if (tenant.id == "cambodia:nh") api.x() else api.y()` | nowhere — there is one server, server-side demux |
| MG endpoint URL | `:app` (build-time) |
| Third-party SDK app-ids (Sendbird, Google Maps) | MG `RuntimeConfig` (not BuildConfig) |
| Tenant-only Retrofit endpoints | `:features-{tenant-feature}` (e.g. `:features-bakong-disputes`) — see [07](07-variants.md) §9 |

If a repo implementation needs a branch on tenant identity, **the server is doing the wrong thing** — escalate to backend before adding the branch on the client.

---

## 9. Testing

`:data` is highly testable in isolation:

- **Unit tests** mock the relevant `*Api` (e.g. with MockK) and assert that `*Repo` classes map DTOs to domain models correctly.
- **Mapper tests** are pure JVM — given a DTO, return the expected domain model.
- **No Hilt graph required** for `:data` tests; instances are constructed directly.

```kotlin
class LoanApplicationRepoTest {
    private val api = mockk<FintechLoanApplicationApi>()
    private val session = fakeSession(activeAccountId = AccountId("acc-001"))
    private val repo = LoanApplicationRepo(api, session)

    @Test fun `submit maps response to LoanApplicationId`() = runTest {
        coEvery { api.submit(any()) } returns loanApplicationResponseFixture(id = "app-123")
        val result = repo.submit(loanApplicationFixture())
        assertTrue(result.isSuccess)
        assertEquals(LoanApplicationId("app-123"), result.getOrNull())
    }

    @Test fun `applicationStatus emits then stops at terminal`() = runTest {
        coEvery { api.status("app-123") } returnsMany listOf(
            loanApplicationResponseFixture(status = LoanApplicationStatusDto.UnderReview),
            loanApplicationResponseFixture(status = LoanApplicationStatusDto.Approved),
        )
        val emissions = repo.applicationStatus(LoanApplicationId("app-123")).toList()
        assertEquals(2, emissions.size)
        assertTrue(emissions.last() is LoanApplicationStatus.Approved)
    }
}
```

Run with `./gradlew :data:test`.

---

## 10. Cross-references

- The interfaces `:data` implements: [03 — `:core`](03-core.md)
- The infrastructure `:data` builds on: [02 — `:aos-sdk`](02-aos-core.md)
- The `Session` and account interceptor that `:data` reads: [12 — Departments and Session](12-departments-and-session.md)
- The MG-derived URLs that the Retrofit instances are bound against: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- Tenant-unique endpoints (don't go here): [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md)
