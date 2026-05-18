# 05 · `:data` — The Data Layer

> **Type:** Local Android library
> **Role:** The Retrofit interfaces, DTOs, and repository implementations. Variant- and tenant-agnostic.
> **Constraint:** Implements `:core` repository interfaces. Does not implement policy interfaces.

---

## 1. Purpose

`:data` is where the wire meets the rest of the app. It owns:

- **`Ippp*Api` interfaces** — the team's unified IPPP backend, split by feature area (auth, receipt, approval, card, expense, OCR, notice) for ergonomics. *One backend, many Retrofit interfaces — not one mega-interface.*
- **DTOs** — request/response data classes the API serializes to JSON, organized by feature. In Bizplay's current shape these are the `*_REQ` / `*_RES` / `*_REC` Java classes; the framework replaces them with typed Kotlin DTOs.
- **Mappers** — extension functions that translate DTOs to `:core` domain models.
- **`Ippp*Repo` implementations** — classes that implement `:core` repository interfaces by calling the corresponding `Ippp*Api`.
- **`DataModule`** — the single Hilt module that exposes the repo bindings and provides each `*Api`.

The defining property: **everything in `:data` is the same regardless of which variant or tenant the user belongs to.** The IPPP backend demuxes by user identity (auth token) + active institution (`USE_INTT_ID` / `COMPANY_CD` stamped by `AccountIdInterceptor`); the Android side calls the same endpoints with the same DTOs, no per-corporate-customer routing on the client.

---

## 2. Module Layout

The internal structure is organized by **feature area** so that `:data` scales as the API surface grows. New endpoints land in the existing feature area, or a new feature area gets its own `*Api`/`*Repo` pair.

```
:data/
└── src/main/kotlin/com/bizplay/data/
    ├── api/
    │   ├── IpppAuthApi.kt             # login, OTP, company picker, refresh
    │   ├── IpppReceiptApi.kt          # receipt CRUD, list, detail
    │   ├── IpppApprovalApi.kt         # approval inbox, approve/reject, line lookup
    │   ├── IpppCardApi.kt             # card register, list, statement
    │   ├── IpppExpenseApi.kt          # expense reports, business-trip bundles, categories
    │   ├── IpppOcrApi.kt              # receipt OCR, ticket OCR submission + result polling
    │   ├── IpppNoticeApi.kt           # announcements, contact, help articles
    │   └── dto/
    │       ├── auth/
    │       │   ├── LoginRequest.kt
    │       │   ├── LoginResponse.kt        # carries variantId + tenantId + tenantFlags + tenantParams + accounts
    │       │   ├── OtpHandleDto.kt
    │       │   └── InstitutionDto.kt       # USE_INTT_ID + COMPANY_CD + DVSN_CD + display name
    │       ├── receipt/
    │       │   ├── ReceiptDraftRequest.kt
    │       │   ├── ReceiptResponse.kt
    │       │   ├── ReceiptListResponse.kt
    │       │   └── ReceiptFilter.kt
    │       ├── approval/
    │       │   ├── ApprovalListResponse.kt
    │       │   ├── ApprovalActionRequest.kt
    │       │   └── ApprovalLineDto.kt
    │       ├── card/
    │       │   ├── CardRegistrationRequest.kt
    │       │   ├── CardListResponse.kt
    │       │   └── StatementResponse.kt
    │       ├── expense/
    │       │   ├── ExpenseReportRequest.kt
    │       │   └── BizTripBundleRequest.kt
    │       ├── ocr/
    │       │   ├── OcrSubmissionRequest.kt
    │       │   └── OcrResultDto.kt
    │       └── shared/
    │           └── EmptyResponse.kt
    ├── repo/
    │   ├── IpppAuthRepo.kt            # implements AuthRepository
    │   ├── IpppReceiptRepo.kt         # implements ReceiptRepository
    │   ├── IpppApprovalRepo.kt        # implements ApprovalRepository
    │   ├── IpppCardRepo.kt            # implements CardRepository
    │   ├── IpppExpenseRepo.kt         # implements ExpenseRepository
    │   ├── IpppOcrRepo.kt             # implements OcrRepository
    │   ├── IpppNoticeRepo.kt          # implements NoticeRepository
    │   └── mapping/
    │       ├── AuthMapping.kt         # DTO → :core domain extensions
    │       ├── ReceiptMapping.kt
    │       ├── ApprovalMapping.kt
    │       └── CardMapping.kt
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
// :data/api/IpppAuthApi.kt
internal interface IpppAuthApi {
    @POST("v1/auth/login")        suspend fun login(@Body req: LoginRequest): LoginResponse
    @POST("v1/auth/logout")       suspend fun logout(): EmptyResponse
    @POST("v1/auth/otp")          suspend fun requestOtp(@Body req: OtpRequest): OtpHandleDto
    @POST("v1/auth/otp/verify")   suspend fun verifyOtp(@Body req: VerifyOtpRequest): UserSessionDto
    @POST("v1/auth/refresh")      suspend fun refresh(@Body req: RefreshRequest): UserSessionDto
    @GET("v1/auth/institutions")  suspend fun institutions(): List<InstitutionDto>  // companies user can act for
}

// :data/api/IpppReceiptApi.kt
internal interface IpppReceiptApi {
    @POST("v1/receipt/list")          suspend fun list(@Body req: ReceiptListRequest): ReceiptListResponse
    @GET("v1/receipt/{id}")           suspend fun detail(@Path("id") id: String): ReceiptResponse
    @POST("v1/receipt")               suspend fun create(@Body req: ReceiptDraftRequest): ReceiptResponse
    @PATCH("v1/receipt/{id}")         suspend fun update(@Path("id") id: String, @Body req: ReceiptEditsRequest): ReceiptResponse
    @DELETE("v1/receipt/{id}")        suspend fun delete(@Path("id") id: String): EmptyResponse
    @Multipart
    @POST("v1/receipt/{id}/photo")    suspend fun attachPhoto(@Path("id") id: String, @Part photo: MultipartBody.Part): ReceiptResponse
}

// :data/api/IpppApprovalApi.kt
internal interface IpppApprovalApi {
    @POST("v1/approval/inbox")        suspend fun inbox(@Body req: ApprovalInboxRequest): ApprovalListResponse
    @GET("v1/approval/{id}")          suspend fun detail(@Path("id") id: String): ApprovalDetailResponse
    @POST("v1/approval/{id}/approve") suspend fun approve(@Path("id") id: String, @Body req: ApprovalActionRequest): EmptyResponse
    @POST("v1/approval/{id}/reject")  suspend fun reject(@Path("id") id: String, @Body req: ApprovalActionRequest): EmptyResponse
    @GET("v1/approval/lines/{draftId}") suspend fun routeOptions(@Path("draftId") id: String): List<ApprovalLineDto>
}

// :data/api/IpppCardApi.kt
internal interface IpppCardApi {
    @POST("v1/card/register")             suspend fun register(@Body req: CardRegistrationRequest): CardResponse
    @GET("v1/card")                       suspend fun cards(): CardListResponse
    @GET("v1/card/{id}/statement")        suspend fun statement(
        @Path("id") cardId: String, @Query("from") fromIso: String, @Query("to") toIso: String,
    ): StatementResponse
}
```

What's **not** here: no `KrKakaoPay*` methods, no `KhLocalRail*` methods, no per-corporate-customer routing on the client. Every endpoint is `/v1/...`; the server reads the auth token + the `USE_INTT_ID` / `COMPANY_CD` headers (stamped by `AccountIdInterceptor` — see [12 — Departments and Session](12-departments-and-session.md)) and dispatches to the right backend tenant/rail itself.

### When to add a new `*Api` interface

| Situation | Action |
|---|---|
| New endpoint within an existing area (auth, receipt, approval, …) | Add a method to the existing `Ippp<Area>Api` |
| New feature area (e.g., chatbot, MyData, payroll integration) | New `Ippp<Area>Api.kt` |
| Endpoint shared by multiple repos (rare, e.g. an internal `/auth/refresh`) | Stays in its natural area; multiple repos can inject the same `*Api` |

Avoid creating an interface per endpoint — that's the opposite over-correction. The current Bizplay `tranCode`-keyed dispatch is the *too-monolithic* extreme; per-endpoint interfaces would be the *too-fragmented* extreme. Feature-area interfaces are the middle.

---

## 4. Repository Implementation Pattern

Every `Ippp*Repo` class:

1. Implements one or more `:core` interfaces.
2. Holds an injected `Ippp*Api` for its feature area (and possibly more if it crosses boundaries).
3. Reads `Session.activeAccountId` for account-scoped requests.
4. Maps DTOs to domain models in dedicated extension functions.
5. Wraps results in `Result<T>` — never throws across the module boundary.

```kotlin
// :data/repo/IpppReceiptRepo.kt
internal class IpppReceiptRepo @Inject constructor(
    private val api: IpppReceiptApi,
    private val session: Session,
) : ReceiptRepository {

    override suspend fun list(filter: ReceiptFilter, page: Int): Result<ReceiptPage> = runCatching {
        api.list(ReceiptListRequest.from(filter, page, session.activeAccountId.value)).toDomain()
    }

    override suspend fun detail(id: ReceiptId): Result<Receipt> = runCatching {
        api.detail(id.value).toDomain()
    }

    override suspend fun create(draft: ReceiptDraft): Result<Receipt> = runCatching {
        api.create(ReceiptDraftRequest.from(draft)).toDomain()
    }

    override suspend fun update(id: ReceiptId, edits: ReceiptEdits): Result<Receipt> = runCatching {
        api.update(id.value, ReceiptEditsRequest.from(edits)).toDomain()
    }

    override suspend fun delete(id: ReceiptId): Result<Unit> = runCatching {
        api.delete(id.value)
        Unit
    }

    override fun observe(id: ReceiptId): Flow<Receipt> = flow {
        emit(api.detail(id.value).toDomain())
    }
}

// :data/repo/IpppAuthRepo.kt
internal class IpppAuthRepo @Inject constructor(
    private val api: IpppAuthApi,
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

The repo does **not** know about variants or tenants. The active `VariantContext` and `TenantContext` are available via Hilt (bound into `LoggedInComponent`) if a request truly needs them in the request body, but typically the server derives both from auth alone.

---

## 5. The Hilt Binding Module

`DataModule` is the only public surface of `:data`. It both binds repository implementations to their `:core` interfaces and provides each `Ippp*Api` instance.

```kotlin
@Module
@InstallIn(LoggedInComponent::class)
abstract class DataModule {

    @Binds @LoggedInScoped abstract fun authRepo(impl: IpppAuthRepo): AuthRepository
    @Binds @LoggedInScoped abstract fun receiptRepo(impl: IpppReceiptRepo): ReceiptRepository
    @Binds @LoggedInScoped abstract fun approvalRepo(impl: IpppApprovalRepo): ApprovalRepository
    @Binds @LoggedInScoped abstract fun cardRepo(impl: IpppCardRepo): CardRepository
    @Binds @LoggedInScoped abstract fun expenseRepo(impl: IpppExpenseRepo): ExpenseRepository
    @Binds @LoggedInScoped abstract fun ocrRepo(impl: IpppOcrRepo): OcrRepository
    @Binds @LoggedInScoped abstract fun noticeRepo(impl: IpppNoticeRepo): NoticeRepository

    companion object {
        // One Retrofit instance, shared across all *Api interfaces (same base URL: RuntimeConfig.urls.main).
        @Provides @LoggedInScoped
        fun ipppRetrofit(
            retrofitFactory: RetrofitFactory,
            runtimeConfig: RuntimeConfig,
        ): Retrofit = retrofitFactory.builder(runtimeConfig.urls.main).build()

        @Provides @LoggedInScoped
        fun authApi(retrofit: Retrofit): IpppAuthApi = retrofit.create(IpppAuthApi::class.java)

        @Provides @LoggedInScoped
        fun receiptApi(retrofit: Retrofit): IpppReceiptApi = retrofit.create(IpppReceiptApi::class.java)

        @Provides @LoggedInScoped
        fun approvalApi(retrofit: Retrofit): IpppApprovalApi = retrofit.create(IpppApprovalApi::class.java)

        @Provides @LoggedInScoped
        fun cardApi(retrofit: Retrofit): IpppCardApi = retrofit.create(IpppCardApi::class.java)

        @Provides @LoggedInScoped
        fun expenseApi(retrofit: Retrofit): IpppExpenseApi = retrofit.create(IpppExpenseApi::class.java)

        @Provides @LoggedInScoped
        fun ocrApi(retrofit: Retrofit): IpppOcrApi = retrofit.create(IpppOcrApi::class.java)

        @Provides @LoggedInScoped
        fun noticeApi(retrofit: Retrofit): IpppNoticeApi = retrofit.create(IpppNoticeApi::class.java)
    }
}
```

Why one `Retrofit` instance for all `*Api`s? They share the same base URL (`RuntimeConfig.urls.main` — today's `Conf.IPPP_SITE_URL`) and the same OkHttp client (cert pinning, interceptors). Splitting Retrofit instances would gain nothing and cost slightly more memory. If a future endpoint needs a different base URL (e.g., `RuntimeConfig.urls.auxiliary` for a partner callback), it gets its own provider.

`@InstallIn(LoggedInComponent::class)` puts every binding inside the session-scoped graph — they live as long as the user is logged in and become GC-eligible at logout.

---

## 6. Why `:data` Does Not Live Inside `:core`

`:core` is the contract layer — interfaces and immutable models. Every product module recompiles when `:core` changes. If repository impls and DTOs lived in `:core`, every API tweak (which happens often — the IPPP backend evolves continuously) would force a recompile of `:features`, every `:variants-*`, and `:app`.

By keeping `:data` separate:

- A DTO field rename recompiles `:data` and `:app` only.
- A new endpoint method recompiles `:data` and `:app` only.
- `:core` stays small, stable, and grep-able as "interfaces and types only."

---

## 7. Why `:data` Does Not Live Inside `:variants-*`

The unified IPPP server makes the API identical for every variant and every tenant. Putting the repo impls in (say) `:variants-kr` would either:

- Require copying the same code into every variant module (duplicated maintenance), or
- Make `:variants-kr` the *only* repo provider, breaking when KH or VN users log in.

`:data` providing the repo binding once for everyone is the only sensible shape.

---

## 8. What Does NOT Go In `:data`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Repository **interfaces** | `:core` |
| Domain models | `:core` |
| Variant-specific validation / amount limits | `:variants-{id}/policy/` |
| Tenant-specific field visibility | `TenantFlags` (in `:core`); set by tenant `*Profile` factory in `:variants-{region}/tenants/{id}/` |
| `OkHttpClient` configuration | `:aos-core` |
| `if (variantId == "kr") api.x() else api.y()` | nowhere — there is one server, server-side demux |
| `if (DetailConfig.isPOSCO_ICT()) api.poscoEndpoint()` | nowhere — same reasoning |
| MgGate endpoint URL | `:app` (build-time) |
| Variant-only Retrofit endpoints | `:features-{variant-feature}` (e.g. `:features-hipass` for Korea-only highway-toll APIs) — see [07](07-variants.md) §9 |

If a repo implementation needs a branch on variant or tenant identity, **the server is doing the wrong thing** — escalate to backend before adding the branch on the client.

---

## 9. Testing

`:data` is highly testable in isolation:

- **Unit tests** mock the relevant `Ippp*Api` (e.g. with MockK) and assert that `*Repo` classes map DTOs to domain models correctly.
- **Mapper tests** are pure JVM — given a DTO, return the expected domain model.
- **No Hilt graph required** for `:data` tests; instances are constructed directly.

```kotlin
class IpppReceiptRepoTest {
    private val api = mockk<IpppReceiptApi>()
    private val session = fakeSession(activeAccountId = AccountId("INTT-001"))
    private val repo = IpppReceiptRepo(api, session)

    @Test fun `create maps response to Receipt`() = runTest {
        coEvery { api.create(any()) } returns receiptResponseFixture()
        val result = repo.create(receiptDraftFixture())
        assertTrue(result.isSuccess)
        assertEquals("rcpt-123", result.getOrNull()!!.id.value)
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
