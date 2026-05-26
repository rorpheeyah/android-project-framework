# 23 · KYC Capture (Camera + ML Kit)

> **Direction:** In-house build, not vendor SDK. CameraX + ML Kit Document Scanner + ML Kit Face Detection.
> **Split:** Primitives in `:aos-sdk/{camera,ml,imaging}/`; flow in `:features-kyc`.
> **Contract:** `KycRepository` in `:core` (provider-polymorphic) lets a future managed-vendor swap be a `:data/kyc/` change, not a flow rewrite.

> **PRD scope:** items 33 (guarantor face + ID capture), 34 (guarantor confirm app form), 67 (customer profile + KYC review). Loan apps are KYC-bound; bad capture quality = bad approval rate. Built right matters.

---

## 1. Why the Split

The framework's principle for capability layering: **commodity primitives go in `:aos-sdk`, opinionated flows go in feature modules**. Camera and ML Kit are commodity — every consuming product might want camera (KYC, profile photo, chat attachments, merchant product photos). Locking them inside `:features-kyc` would force every other consumer to re-implement.

The KYC *flow* (capture front → capture back → liveness selfie → review → upload) is opinionated UX, banking-locked. It belongs in `:features-kyc`.

Same shape as `CompassWebView` (in `:aos-sdk/webview/`) vs. specific web flows (in `:features-{name}`).

---

## 2. `:aos-sdk` Primitives

```
:aos-sdk/
├── camera/
│   ├── CameraXController.kt        # lifecycle-aware camera session
│   └── CompassCameraView.kt        # Compose preview primitive (like CompassWebView)
├── ml/
│   ├── DocumentScannerWrapper.kt   # wraps ML Kit Document Scanner (ID-card crop + perspective + glare)
│   └── FaceDetectorWrapper.kt      # wraps ML Kit Face Detection (liveness quality gate)
└── imaging/
    ├── ImageCompressor.kt          # JPEG 1280px max edge, quality 80
    ├── ExifStripper.kt             # remove GPS + sensitive metadata before upload
    ├── Watermarker.kt              # tamper-resistant overlay (timestamp + userId + nonce)
    └── BitmapRedactor.kt           # for logging — never log raw pixels
```

### 2.1 `CompassCameraView`

```kotlin
// :aos-sdk/camera/CompassCameraView.kt
@Composable
fun CompassCameraView(
    controller: CameraXController,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx -> PreviewView(ctx).also { controller.bindPreview(it, lifecycleOwner, cameraSelector) } },
        modifier = modifier,
    )
    Box(modifier = modifier, content = overlay)
}

class CameraXController {
    suspend fun capture(): Result<Bitmap> { /* CameraX takePicture */ }
    fun bindPreview(view: PreviewView, owner: LifecycleOwner, selector: CameraSelector) { /* … */ }
    fun close() { /* release ImageCapture, ImageAnalysis */ }
}
```

The view is generic — KYC, profile photo, future flows reuse it. The overlay slot accepts a Composable for the framing rectangle ("align ID card here") or face oval ("center your face").

### 2.2 `DocumentScannerWrapper` and `FaceDetectorWrapper`

```kotlin
// :aos-sdk/ml/DocumentScannerWrapper.kt
class DocumentScannerWrapper {
    suspend fun scan(bitmap: Bitmap): Result<DocumentScanResult> {
        // ML Kit Document Scanner: crop to document edges, correct perspective, return cleaned bitmap + glare warnings
    }
}
data class DocumentScanResult(
    val cropped: Bitmap,
    val confidence: Float,
    val warnings: List<DocumentWarning>,
)
enum class DocumentWarning { LowLight, Glare, EdgeNotFound, Blurry }

// :aos-sdk/ml/FaceDetectorWrapper.kt
class FaceDetectorWrapper {
    suspend fun analyze(bitmap: Bitmap): Result<FaceQualityResult> {
        // ML Kit Face Detection: face present, centered, eyes open, not occluded
    }
}
data class FaceQualityResult(
    val faceCount: Int,                      // expect 1
    val centered: Boolean,
    val eyesOpen: Boolean,
    val unoccluded: Boolean,
    val boundingBoxRatio: Float,             // face occupies sensible portion of frame
) {
    val isAcceptable: Boolean get() = faceCount == 1 && centered && eyesOpen && unoccluded && boundingBoxRatio in 0.25f..0.75f
}
```

Wrappers, not bare ML Kit. The wrapper guarantees: nullable callbacks become `Result<T>`, threading is `suspend`, domain types replace raw ML Kit DTOs. No ML Kit type ever escapes `:aos-sdk/ml/`.

### 2.3 `:aos-sdk/imaging/`

```kotlin
class ImageCompressor {
    fun compress(bitmap: Bitmap, maxEdgePx: Int = 1280, qualityPercent: Int = 80): ByteArray
}

class ExifStripper {
    fun strip(jpegBytes: ByteArray): ByteArray         // remove GPS, datetime, device model
}

class Watermarker {
    /** Overlay timestamp + userId + nonce in a corner; tamper-resistant via low contrast + structured patterns */
    fun watermark(bitmap: Bitmap, userId: UserId, nonce: String = generateNonce()): Bitmap
}

class BitmapRedactor {
    fun toLoggableString(bitmap: Bitmap): String = "Bitmap(${bitmap.width}x${bitmap.height}, redacted)"
}
```

Zero permissions, pure Android Graphics. Trivially testable.

---

## 3. The `:core` Contract

```kotlin
// :core/repository/KycRepository.kt
interface KycRepository {
    suspend fun submitDocument(request: KycCaptureRequest): Result<KycSubmissionId>
    fun submissionStatus(id: KycSubmissionId): Flow<KycSubmissionStatus>
}

// :core/kyc/KycCaptureRequest.kt
data class KycCaptureRequest(
    val type: KycDocumentType,
    val imageBytes: ByteArray,            // already compressed + watermarked + EXIF-stripped
    val capturedAt: Instant,
    val captureMetadata: CaptureMetadata,
)
data class CaptureMetadata(
    val deviceModel: String,
    val osVersion: String,
    val captureNonce: String,             // matches the watermark; server validates
)
enum class KycDocumentType {
    NationalIdFront,
    NationalIdBack,
    SelfieWithLiveness,
    Passport,
    DrivingLicense,
}

// :core/policy/KycRequirementPolicy.kt — what docs the active tenant requires
interface KycRequirementPolicy {
    val requiredDocuments: List<KycDocumentType>
    val requiresLivenessSelfie: Boolean
    val maxRetries: Int
}
```

`:features-kyc` consumes `KycRepository` and `KycRequirementPolicy`. It does not import CameraX, ML Kit, or Sendbird types — those are SDK primitives wrapped behind the wizard.

---

## 4. The `:features-kyc` Wizard

```
:features-kyc/
└── src/main/kotlin/com/<org>/features/kyc/
    ├── ui/
    │   ├── KycFlowScreen.kt              # wraps the wizard, reads KycRequirementPolicy to pick the step list
    │   ├── IdCardCaptureScreen.kt        # uses CompassCameraView + DocumentScannerWrapper
    │   ├── SelfieScreen.kt               # uses CompassCameraView (front) + FaceDetectorWrapper
    │   ├── ReviewScreen.kt               # preview captures, retake-or-submit
    │   └── component/
    │       ├── IdFraming.kt              # the overlay rectangle composable
    │       └── FaceOval.kt               # the overlay face-position composable
    ├── upload/
    │   └── KycUploadWorker.kt            # WorkManager — resumable, retry-with-backoff, see [28]
    ├── viewmodel/
    │   └── KycFlowViewModel.kt
    └── contract/
        └── KycFlowContract.kt            # WizardState, WizardEvent, WizardEffect — see [30 — Form Wizard]
```

### 4.1 The capture loop

```kotlin
// :features-kyc/ui/IdCardCaptureScreen.kt
@Composable
internal fun IdCardCaptureScreen(
    side: IdCardSide,
    viewModel: IdCardCaptureViewModel = hiltViewModel(),
    onSuccess: (KycCaptureRequest) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = remember { CameraXController() }
    DisposableEffect(Unit) { onDispose { controller.close() } }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is IdCardCaptureEffect.Captured -> onSuccess(effect.request)
            }
        }
    }

    CompassCameraView(controller = controller, modifier = Modifier.fillMaxSize()) {
        IdFraming(modifier = Modifier.align(Alignment.Center))
        CompassButton(
            onClick = { viewModel.onEvent(IdCardCaptureEvent.CaptureTapped(controller)) },
            isLoading = state.isProcessing,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Text(stringResource(R.string.kyc_capture_button)) }

        state.lastWarnings.forEach { warning ->
            CompassWarning(text = warning.displayName())
        }
    }
}

@HiltViewModel
internal class IdCardCaptureViewModel @Inject constructor(
    private val docScanner: DocumentScannerWrapper,
    private val compressor: ImageCompressor,
    private val stripper: ExifStripper,
    private val watermarker: Watermarker,
    private val session: Session,
) : MviViewModel<IdCardCaptureState, IdCardCaptureEvent, IdCardCaptureEffect>(initial = IdCardCaptureState()) {

    override fun onEvent(event: IdCardCaptureEvent) = when (event) {
        is IdCardCaptureEvent.CaptureTapped -> capture(event.controller)
    }

    private fun capture(controller: CameraXController) = viewModelScope.launch {
        setState { copy(isProcessing = true) }
        val raw = controller.capture().getOrElse { setState { copy(isProcessing = false) }; return@launch }
        val scanned = docScanner.scan(raw).getOrElse { setState { copy(isProcessing = false) }; return@launch }
        if (scanned.warnings.isNotEmpty() || scanned.confidence < 0.7f) {
            setState { copy(isProcessing = false, lastWarnings = scanned.warnings) }
            return@launch
        }
        val watermarked = watermarker.watermark(scanned.cropped, session.userSession.userId)
        val jpegBytes = compressor.compress(watermarked)
        val cleanBytes = stripper.strip(jpegBytes)
        emitEffect(IdCardCaptureEffect.Captured(
            KycCaptureRequest(
                type = KycDocumentType.NationalIdFront,    // passed in via constructor for back/passport
                imageBytes = cleanBytes,
                capturedAt = Clock.System.now(),
                captureMetadata = CaptureMetadata(/* … */),
            ),
        ))
        setState { copy(isProcessing = false) }
    }
}
```

### 4.2 The liveness selfie

```kotlin
// :features-kyc/ui/SelfieScreen.kt — same shape as IdCardCaptureScreen but with FaceDetectorWrapper
// Capture loop continuously analyzes preview frames; capture button enables only when isAcceptable == true
```

### 4.3 Upload via WorkManager

`KycCaptureRequest` is handed to `KycUploadWorker` (see [28 — Background Work](28-background-work.md)). Upload is **resumable** and **retries** with exponential backoff. The user can leave the app between capture and upload completion; the worker drives it home.

---

## 5. Tenant Policy Hooks

`KycRequirementPolicy` is bound at the region-base or concrete-tenant layer:

```kotlin
// :tenants:cambodia:base/policy/KhDefaultKycRequirementPolicy.kt
internal class KhDefaultKycRequirementPolicy : KycRequirementPolicy {
    override val requiredDocuments = listOf(
        KycDocumentType.NationalIdFront,
        KycDocumentType.NationalIdBack,
        KycDocumentType.SelfieWithLiveness,
    )
    override val requiresLivenessSelfie = true
    override val maxRetries = 3
}
```

A tenant that requires additional documents (e.g., passport for an expat-focused product) overrides the policy in its concrete-tenant module. The `:features-kyc` wizard reads the policy to assemble its step list at runtime.

---

## 6. The Guarantor Capture Variant

Item 33 in the PRD: "Guarantor upload (front, back) ID card and take selfie while hold ID card". This is a variant of the customer KYC flow where the **selfie-with-ID-card** is held. Implementation:

- A `KycDocumentType.SelfieHoldingId` enum value (in addition to plain `SelfieWithLiveness`).
- The `FaceDetectorWrapper` extension `analyzeWithDocument()` returns `FaceQualityResult` AND verifies an ID card edge is detected in-frame.
- The wizard's step list switches to the guarantor variant when the flow is launched from the guarantor deeplink (see [22 — Deeplinks § 6](22-deeplinks.md)).

This is the same `:features-kyc` module — the guarantor entry composes a different sequence of the same capture screens. No new dependencies.

---

## 7. Dependency Cost

| Library | Size impact |
|---|---|
| `androidx.camera:camera-core` + `camera-camera2` + `camera-lifecycle` + `camera-view` + `camera-compose` | ~1.5 MB |
| `com.google.mlkit:document-scanner` | ~3 MB (on-device model lazy-loaded) |
| `com.google.mlkit:face-detection` | ~5 MB (on-device model lazy-loaded) |
| **Total `:aos-sdk` impact** | ~9.5 MB |

Acceptable; well within the 50 MB Play Store APK budget. R8 strips unused classes per consumer; an SDK consumer that never instantiates `FaceDetectorWrapper` doesn't load the model at runtime, though the dependency ships.

If a future consumer (e.g., a B2B admin tool) genuinely never needs camera, split `:aos-sdk` into per-capability artifacts (`:aos-sdk-camera`, `:aos-sdk-ml`, …) like `androidx.*`. Defer until consumer count crosses ~4 and dependency weight becomes a real complaint.

---

## 8. PII Handling

| Concern | Mitigation |
|---|---|
| EXIF GPS in captured images | `ExifStripper` always run before upload |
| Captured bitmap surfacing in logs | `BitmapRedactor.toLoggableString()` — Timber tree configured to use it |
| Bitmap left in memory across screens | Bitmaps recycled in `DisposableEffect { onDispose { … } }`; explicit `Bitmap.recycle()` after upload |
| Replay attack (re-submitting a captured image) | Watermark contains a nonce; server validates the nonce was issued recently |
| Tamper attack (modifying the captured bitmap) | Server-side image quality + watermark-presence check; mobile is not the line of defense |

---

## 9. Vendor Substitution Plan (Onfido / Sumsub / iProov)

If at scale the in-house build proves insufficient (compliance auditor pushback, fraud rate, regulatory acceptance), the swap path is:

1. Add `:data/kyc/<Vendor>KycRepo` implementing `KycRepository`.
2. The vendor's SDK takes over capture too — `:features-kyc` becomes a thin host that launches the vendor's flow and receives the result.
3. `:aos-sdk/{camera,ml,imaging}/` primitives remain available for non-KYC uses (profile photo, chat attachments).

This is the standard build-then-buy hedge: build the in-house path, keep the contract polymorphic so a vendor swap is a `:data/kyc/` change.

---

## 10. What Does NOT Belong Here

| ❌ Not in this layer | ✅ Belongs in |
|---|---|
| ML Kit types in `:features-kyc` | `:aos-sdk/ml/` wrappers only |
| CameraX types in `:features-kyc` ViewModels | `:aos-sdk/camera/` wrappers only |
| Tenant-specific document requirements hardcoded in `:features-kyc` | `KycRequirementPolicy` impl in `:tenants:*:*` |
| Raw image upload bypassing WorkManager | Always use `KycUploadWorker` for retry/resume |
| EXIF data in uploaded images | `ExifStripper` mandatory before upload |
| Logging raw bitmaps | `BitmapRedactor.toLoggableString()` only |

---

## 11. Cross-references

- The split rationale: [PRD-FIT-ASSESSMENT § 3.1](../PRD-FIT-ASSESSMENT.md)
- The `:aos-sdk` module structure that hosts the primitives: [17 — Project Structure](17-project-structure.md)
- The wizard pattern the KYC flow uses: [30 — Form Wizard](30-form-wizard.md)
- The WorkManager wrapper for resumable upload: [28 — Background Work](28-background-work.md)
- The guarantor flow that consumes this module: [22 — Deeplinks § 6](22-deeplinks.md)
- Tenant-policy escalation: [07 — `:tenants:*`](07-variants.md), [19 — Tenants and Regions](19-tenants-and-variants.md)
