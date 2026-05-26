# 05 · Case Study — Multi-Tenant Lending App

> **Role of this doc:** the framework's current reference instance, described as **one** worked example of how to apply the framework. Not a standard to follow; just a real product that helped validate the framework's shape.
> **Why concentrated here:** so the other framework-template docs stay domain-neutral. New products on different domains (insurance, brokerage, enterprise, marketplace) read this only for "an example of someone applying the framework", not as required reading.

---

## 1. What the Reference Project Is

The framework's design was validated against a **multi-tenant Android lending product**:

- **Domain:** consumer lending (loan origination, servicing, repayment)
- **Tenant model:** one consuming organization initially (NH Finance), with the ability to add additional partner organizations under the same regulator without framework changes
- **Region:** Cambodia (regulator: NBC); architecturally room for additional regions (Korea was discussed but deferred)
- **Currencies:** KHR + USD; KRW reserved for a future Korea-region scope
- **Languages:** Khmer, English, Korean

The PRD that drove this validation is preserved as `PRD-FIT-ASSESSMENT.md` at the repo root.

The framework as it currently exists was iterated through the PRD-fit process: each round identified gaps, missing capabilities, or over-engineering, and the framework was adjusted. The capability docs (20–30) were generated specifically to address the lending PRD's needs, but the *patterns* in those docs are domain-neutral.

---

## 2. What's Specific to This Instance

### 2.1 Domain types (in `:core`)

The instance defines repository interfaces like:
- `LoanRepository`, `LoanApplicationRepository`, `RepaymentRepository`
- `GuarantorRepository`, `KycRepository`
- `ReferralRepository`, `ConsultationRepository`, `BranchRepository`
- `ChatRepository` (provider-agnostic; instance uses Sendbird in `:data/chat/`)

Policy interfaces:
- `LoanEligibilityPolicy`, `EmiCalculator`, `RepaymentPenaltyCalculator`
- `KycRequirementPolicy`, `StaffIdValidator`

Domain models:
- `Loan`, `LoanProduct`, `LoanApplication`, `LoanApplicationStatus`
- `RepaymentSchedule`, `Installment`, `PaymentReceipt`, `PayoffQuote`
- `Guarantor`, `GuarantorInvite`, `GuarantorVerification`
- `KycCaptureRequest`, `KycSubmissionStatus`, `KycDocumentType`
- `Branch`, `GeoPoint`, `BranchService`

**Reusability:** these names are specific to the lending domain. A different product (insurance, brokerage, etc.) defines its own. The framework's `:core` shape (the existence of `:core/repository/`, `:core/policy/`, `:core/model/`) is reused; the *contents* are not.

### 2.2 API surface (in `:data`)

The instance's `Fintech*Api` family:
- `FintechAuthApi`, `FintechLoanApi`, `FintechLoanApplicationApi`
- `FintechRepaymentApi`, `FintechGuarantorApi`, `FintechKycApi`
- `FintechReferralApi`, `FintechConsultationApi`, `FintechBranchApi`

Plus third-party clients in `:data/external/`:
- `CbcApi` (Credit Bureau Cambodia)
- `BankStatementAnalyzerApi`
- `MwlAgencyApi` (Migrant Worker Loan agency systems — specific to the MWL product line)

Plus the provider-bound chat repo:
- `:data/chat/SendbirdChatRepo`

**Reusability:** the per-feature-area split convention is universal; the specific area names are domain-bound. The framework doc shows the *pattern* (one `*Api` per logical area, `:data/external/` for third-parties, provider-bound subdirectories for SDK-coupled deps); this case study documents the *concrete instance*.

### 2.3 Tenant identity

The instance's first concrete tenant is `:tenants:cambodia:nh`, with composite `TenantId("cambodia:nh")`. The region base is `:tenants:cambodia:base` providing:
- `KhDefaultLoanEligibilityPolicy`
- `KhDefaultEmiCalculator`
- `KhDefaultRepaymentPenaltyCalculator`
- `KhOtpDeliveryPolicy` (NBC-mandated 6-digit, 5-minute expiry)
- `KhComplianceThresholds` (NBC-aligned)
- `KhBusinessCalendar` (Cambodian holiday calendar)
- `KhDefaultKycRequirementPolicy`
- `KhrAmountFormatter`, `UsdAmountFormatter` (dual-currency)
- `KhBaseCapabilities`

The concrete tenant `:tenants:cambodia:nh` overrides only what's NH-KH-specific:
- `NhKhTenantProfile` (TenantContext factory)
- `NhKhStaffIdValidator` (NH-specific staff ID regex)
- `NhKhSupportContacts`
- `NhKhCapabilities`

**Reusability:** the *pattern* of region-base + concrete-tenant override is the framework's contract. The *names* (Cambodia, NH) are this instance's. A different consuming product in a different country uses the same pattern with its own region/tenant slugs.

### 2.4 Multi-step flows

The lending PRD has two long multi-step apply forms:

- **NON-MWL Apply** (9 steps): borrower info → branch → documents → referral → review (and supporting screens for submit / track / accept / disbursement)
- **MWL Apply** (18 steps): borrower quick info → branch → employment → referral → MWL agency → loan request → bank account → confirm form → add guarantor → awaiting guarantor confirmation → confirm guarantor review → submit → (back-office process) → approval offer → accept → disbursement → notification → contract

Both flows use the `:core/wizard/` contract pattern documented in `docs/framework/30-form-wizard.md`. The instance-specific step lists are extracted to `docs/reference-app/instance-flows.md` (after the doc refactor lands).

**Reusability:** the wizard *contract* is universal — any multi-step form in any product uses the same `:core/wizard/` types and the `hiltViewModel(parentEntry)` NavGraph scoping. The *step lists* are instance-specific.

### 2.5 Capability set used

The instance uses the following capabilities from the framework's catalog:

| Capability | Doc | Instance use |
|---|---|---|
| Sendbird chat | `docs/framework/20-chat.md` | Customer ↔ loan officer support chat (PRD items 62, 63) |
| Push channels | `docs/framework/21-push-channels.md` | Reminder (repayment due), transaction (disbursement confirmed), announcement (product news) |
| Deeplinks | `docs/framework/22-deeplinks.md` | Guarantor SMS link → KYC verify; payment deeplink from push |
| KYC capture | `docs/framework/23-kyc-capture.md` | Borrower KYC + guarantor identity verification (in-house CameraX + ML Kit) |
| PDF | `docs/framework/24-pdf.md` | Loan contract download + preview + share |
| Locale | `docs/framework/25-locale.md` | Khmer / English / Korean |
| PIN + session timeout | `docs/framework/26-pin-and-session.md` | 4-digit PIN, NBC-recommended 5-minute inactivity timeout |
| Maps | `docs/framework/27-maps-and-location.md` | Branch locator (PRD item 72) |
| Background work | `docs/framework/28-background-work.md` | KYC upload, contract download, FCM token refresh, draft sync, MG retry |
| Local DB | `docs/framework/29-local-database.md` | Chat history, draft applications, branch cache, notification inbox, repayment schedule |
| Form wizard | `docs/framework/30-form-wizard.md` | The two apply flows |

A different product uses a subset of these (or all, or adds new ones). The capability docs are à la carte — no product is required to use every capability.

---

## 3. What's Generic and Already Validated

The PRD-fit process exercised these framework decisions, validating that they work for at least one real product:

- **Single tenant axis** — held up. The lending app uses one variant degenerately (cambodia) and one tenant (NH). The framework's variant→tenant collapse was triggered by this PRD; the resulting single-axis model is simpler and equally expressive.
- **`:aos-sdk` capability split** — held up. The 11 capability docs (20–30) emerged from the PRD's needs; each one is domain-neutral.
- **Region as Gradle hierarchy** — held up. Cambodia exists as a region; Korea was discussed and deferred; the structural pattern is ready.
- **MG-driven config + stale fallback** — held up. The PRD's "session managed with configurable timeout" requirement drove the SessionTimeoutPolicy contract; the stale-config fallback emerged from "what if MG is down?"
- **Wizard contract** — held up. The 18-step MWL flow forced the framework to define a wizard pattern instead of stretching per-screen MVI.
- **Logic-Blind UI + compile-time forbidden imports** — held up. The lending app's `:features` doesn't import any `:tenants:cambodia:nh` types; tenant policies dispatch via Hilt multibindings.

---

## 4. What This Case Study Does NOT Imply

- **The framework is not "for lending."** It's for multi-tenant Android products; lending happens to be the first one.
- **The framework is not "NH-branded."** Naming decisions ([`04-naming-decisions.md`](04-naming-decisions.md)) recommend Option C: keep "Compass" at doc identity, use generic `Aos*` prefix in SDK code, project-prefix placeholders in `:design-system`.
- **The Cambodia/Korea region scope is not the framework's default.** Future projects pick their own regions.
- **The 9-step NON-MWL and 18-step MWL flows are not the standard.** They are this instance's flows. A different product has different step lists, possibly fewer, possibly more.
- **The lending-domain capability set is not "the right set."** A brokerage product uses a different mix (price alerts, watchlists, order placement); a marketplace product uses yet another mix.

---

## 5. What Future Projects Can Crib From

Even if a future product is in a completely different domain (e.g., enterprise expense management or a marketplace app), it can reuse:

- **The capability docs (20–30) verbatim** — every capability primitive is product-agnostic
- **The framework spec (00–19) verbatim** — architecture is product-agnostic
- **The init checklist** ([`03-new-project-init-checklist.md`](03-new-project-init-checklist.md)) — domain-neutral by design
- **The shape of `:data/api/` and `:data/external/`** — the per-feature-area split + the third-party-clients convention apply to any product
- **The shape of `:tenants:<region>:base` + concrete tenants** — the regional baseline + per-org override pattern works for any multi-tenant product
- **The Hilt multibinding pattern with `@TenantKey`** — the dispatch mechanism is generic
- **The wizard contract** — any multi-step form anywhere

What the future project **defines for itself**:

- `:core/repository/*Repository.kt` interfaces (their domain)
- `:core/policy/*Policy.kt` interfaces (their domain)
- `:core/model/*.kt` types (their domain)
- `:data/api/Fintech*Api.kt` interfaces (their backend)
- `:data/external/*Api.kt` (their third-party integrations)
- `:tenants:<region>:base/*Policy.kt` (their region's regulator rules)
- `:tenants:<region>:<tenant>/*` (their tenant identity)
- Brand prefix (`<TheirBrand>Button`, `<TheirBrand>Application`)
- Regions, tenants, currencies, languages

---

## 6. Where to Find Reference-Instance Content After the Refactor

Once [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md) lands, the instance-specific content moves out of the framework docs into a dedicated section:

```
docs/reference-app/
├── README.md                                 ← "this is one instance"
├── prd-summary.md                            ← short PRD recap with link to PRD-FIT-ASSESSMENT.md
├── domain-types.md                           ← all the Loan*/Repayment*/Guarantor*/etc. types
├── api-surface.md                            ← all the FintechLoan*/FintechRepayment*/etc. APIs
├── reference-tenant.md                       ← the :tenants:cambodia:nh module spec
├── instance-flows.md                         ← the 9-step NON-MWL and 18-step MWL apply flows
├── multi-tenant-migration-example.md         ← the 11-customer DetailConfig.isXxx() → TenantFlags worked example
└── reference-project-structure.md            ← full module tree with concrete paths
```

That's where future projects look for "how did the first instance handle this concrete situation?" — without that content polluting the framework spec.

---

## 7. Cross-references

- The strategy that puts this case study in its right place: [`00-strategy.md`](00-strategy.md)
- The doc refactor that creates the proper `docs/reference-app/` home: [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)
- The full PRD-fit assessment that drove the framework's iterations: [`../PRD-FIT-ASSESSMENT.md`](../PRD-FIT-ASSESSMENT.md)
- The framework spec (currently mixed with this instance's content; the refactor cleans it up): [`../docs/`](../docs/)
