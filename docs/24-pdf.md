# 24 · PDF Download and Preview

> **Use case:** loan-contract download and in-app preview (PRD items 41, 44, 53).
> **Primitives:** `:aos-sdk/pdf/{PdfDownloader, PdfViewer}`.
> **Storage:** app-private (`Context.filesDir`) — contracts contain PII and must never land in shared storage.
> **Download mechanics:** OkHttp streaming + WorkManager retry; never plain `DownloadManager` because that writes to public Downloads.

---

## 1. Why This Lives in `:aos-sdk`

Loan contracts are the most-shared PDF type in this product, but PDFs aren't banking-specific — a future product (insurance, brokerage, merchant statements) will need the same primitives. Bitmap manipulation, file storage, and PDF rendering are commodity infrastructure.

`:aos-sdk/pdf/` provides the download/store/preview/share pipeline. `:features/loan/contract/` consumes it to wire the loan-contract screen.

---

## 2. The Download Primitive

```kotlin
// :aos-sdk/pdf/PdfDownloader.kt
class PdfDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    /**
     * Downloads a PDF to app-private storage and returns its File handle.
     * Streams the response body; does not buffer the whole PDF in memory.
     * Resumable via WorkManager wrapper — see :features/loan/contract/ContractDownloadWorker.
     */
    suspend fun download(url: HttpUrl, targetFileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                check(response.isSuccessful) { "HTTP ${response.code}" }

                val outFile = File(context.filesDir, "pdf/$targetFileName").apply {
                    parentFile?.mkdirs()
                }
                response.body!!.byteStream().use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                outFile
            }
        }

    fun exists(fileName: String): Boolean = File(context.filesDir, "pdf/$fileName").exists()

    fun remove(fileName: String): Boolean = File(context.filesDir, "pdf/$fileName").delete()
}
```

**Why not `DownloadManager`?** The Android `DownloadManager` writes to public/shared storage, exposes the download to other apps via the system Downloads UI, and has poor failure semantics. Contracts contain PII (NID, amounts, signatures); they must never appear in the public Downloads folder.

**Why `app-private` (`Context.filesDir`)?** Other apps can't read it. It is included in app data backup unless explicitly excluded — for contracts, **add `<exclude>` to `data_extraction_rules.xml`** so they don't ride into Google Drive backup either.

```xml
<!-- :app/src/main/res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="pdf/"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="pdf/"/>
    </device-transfer>
</data-extraction-rules>
```

---

## 3. The Preview Primitive

```kotlin
// :aos-sdk/pdf/PdfViewer.kt
@Composable
fun PdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val pageCount = remember(file) { PdfRenderer(ParcelFileDescriptor.open(file, MODE_READ_ONLY)).use { it.pageCount } }
    var currentPage by remember { mutableIntStateOf(initialPage) }

    Column(modifier = modifier) {
        PdfPage(file = file, pageIndex = currentPage, modifier = Modifier.weight(1f).fillMaxWidth())
        Row(/* prev / next / page indicator */) { /* … */ }
    }

    LaunchedEffect(currentPage) { onPageChanged(currentPage) }
}

@Composable
private fun PdfPage(file: File, pageIndex: Int, modifier: Modifier) {
    val bitmap = remember(file, pageIndex) {
        PdfRenderer(ParcelFileDescriptor.open(file, MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also { bmp ->
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}
```

This uses the framework's built-in `PdfRenderer` (Android 5.0+). For richer features (text selection, search, annotations) consider **`androidx.pdf`** (Android 14+) as a future upgrade — but the basic preview-only PdfRenderer is sufficient for the lending PRD.

**Performance:** rendering each page synchronously is fine for contracts of ≤20 pages. For multi-hundred-page documents, render in `Dispatchers.Default` and cache rendered bitmaps in an LRU.

---

## 4. The Share Primitive (FileProvider)

For the user's "share contract" action (export to email, save to Google Drive manually):

```xml
<!-- :app/src/main/AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- :app/src/main/res/xml/file_paths.xml -->
<paths>
    <files-path name="pdf" path="pdf/" />
</paths>
```

```kotlin
// :aos-sdk/pdf/PdfSharer.kt
class PdfSharer @Inject constructor(@ApplicationContext private val context: Context) {
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
```

The FileProvider URI is the only way to share an app-private file safely — it grants read permission to the receiving app without exposing the path directly.

---

## 5. The Feature Wiring

```
:features/loan/contract/
├── ContractScreen.kt              # composes PdfViewer + download button + share button
├── ContractViewModel.kt
└── ContractDownloadWorker.kt      # WorkManager wrapper around PdfDownloader for retry/resume
```

```kotlin
// :features/loan/contract/ContractViewModel.kt
@HiltViewModel
internal class ContractViewModel @Inject constructor(
    private val loanRepo: LoanRepository,
    private val workScheduler: BackgroundWorkScheduler,        // see [28]
    private val downloader: PdfDownloader,                     // for "already downloaded" check
) : MviViewModel<ContractState, ContractEvent, ContractEffect>(initial = ContractState.Loading) {

    fun loadContract(loanId: LoanId) = viewModelScope.launch {
        val cached = "loan-${loanId.value}.pdf"
        if (downloader.exists(cached)) {
            setState { ContractState.Ready(file = File(context.filesDir, "pdf/$cached")) }
            return@launch
        }
        workScheduler.enqueueDownloadContract(loanId).also { workId ->
            setState { ContractState.Downloading(workId = workId) }
            workScheduler.observe(workId).collect { status ->
                when (status) {
                    is WorkStatus.Succeeded -> setState { ContractState.Ready(file = File(context.filesDir, "pdf/$cached")) }
                    is WorkStatus.Failed    -> setState { ContractState.Error(status.message) }
                    else                    -> Unit
                }
            }
        }
    }
}
```

`ContractDownloadWorker` is a `CoroutineWorker` that calls `LoanRepository.contractPdfUrl(loanId)` → `PdfDownloader.download(...)`. WorkManager handles retry-with-backoff and survives process death.

---

## 6. What Does NOT Belong Here

| ❌ Not in this layer | ✅ Belongs in |
|---|---|
| Downloads to public storage | App-private only — contracts have PII |
| `DownloadManager` for any PII-bearing file | OkHttp + WorkManager |
| PDF rendering library that pulls in megabytes | Stick with `PdfRenderer` (free, built-in) until richer features are required |
| Logging the contract bytes | Log the file size + checksum only, never the content |
| Pre-shipping contract templates in the APK | Always server-issued; client downloads on demand |
| Sharing without FileProvider | Direct `file://` URIs throw `FileUriExposedException` on API 24+ |

---

## 7. Cross-references

- The WorkManager wrapper used for download retry: [28 — Background Work](28-background-work.md)
- The encrypted storage that holds tokens (not PDFs — PDFs are in `filesDir`): [29 — Local Database](29-local-database.md)
- The `:aos-sdk` module structure: [17 — Project Structure](17-project-structure.md)
- The loan-domain API that returns the contract URL: [05 — `:data`](05-data.md)
