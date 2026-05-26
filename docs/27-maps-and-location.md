# 27 · Maps and Location (Branch Locator)

> **Use case:** PRD item 72 — map view of branches/ATMs with search, directions, contact details.
> **Library:** Google Maps Compose (`com.google.maps.android:maps-compose`).
> **UI module:** `:features-branch-locator` (heavy-SDK sibling — same pattern as `:features-chatbot`).
> **Data:** `BranchRepository` in `:core`; offline-cacheable via Room (see [29 — Local Database](29-local-database.md)).

---

## 1. Why a Sibling Module

Google Maps Compose pulls in ~3 MB of native code (the underlying Google Play Services Maps client) plus model assets. Co-locating that with `:features` would slow incremental builds of every other feature. The "heavy SDK" criterion documented in [01 § 4.5](01-module-topology.md) applies — branch locator becomes its own module.

---

## 2. The Module Shape

```
:features-branch-locator/
└── src/main/kotlin/com/<org>/features/branchlocator/
    ├── (maps-compose imported here)
    ├── ui/
    │   ├── BranchMapScreen.kt              # map view with markers
    │   ├── BranchListScreen.kt             # list view (tab switch with map)
    │   ├── BranchDetailSheet.kt            # bottom-sheet for selected branch
    │   └── component/
    │       └── BranchMarker.kt             # custom Compose marker
    ├── viewmodel/
    │   └── BranchLocatorViewModel.kt
    ├── contract/
    │   └── BranchLocatorContract.kt
    └── nav/
        └── BranchLocatorNavigator.kt       # NavGraphBuilder.branchLocatorNavGraph(...)
```

---

## 3. The `:core` Contract

```kotlin
// :core/model/Branch.kt
data class Branch(
    val id: BranchId,
    val name: String,
    val address: String,
    val location: GeoPoint,
    val phone: String,
    val openingHours: String,                // human-readable, e.g., "Mon-Fri, 8 AM - 6 PM"
    val services: Set<BranchService>,        // e.g., LoanOrigination, AtmDeposit, ForeignCurrency
)
data class GeoPoint(val latitudeDeg: Double, val longitudeDeg: Double)
enum class BranchService { LoanOrigination, Atm, AtmDeposit, ForeignCurrency, SafetyDeposit }
@JvmInline value class BranchId(val value: String)

// :core/repository/BranchRepository.kt
interface BranchRepository {
    /** Returns the cached list immediately, then refreshes from the server in the background. */
    fun branches(): Flow<List<Branch>>

    /** Returns branches within a bounding box; useful for map viewport queries. */
    fun branchesWithinBox(southWest: GeoPoint, northEast: GeoPoint): Flow<List<Branch>>

    /** Forces a re-fetch from the server, updating the cache. */
    suspend fun refresh(): Result<Unit>
}
```

`BranchRepository` is **tenant-agnostic** (branch list is per region/country, but the user's tenant determines which branches are theirs — handled server-side). Implementation in `:data/repo/BranchRepo.kt`:

```kotlin
// :data/repo/BranchRepo.kt
internal class BranchRepo @Inject constructor(
    private val api: FintechBranchApi,
    private val dao: BranchDao,                              // Room — see [29]
) : BranchRepository {

    override fun branches(): Flow<List<Branch>> = dao.observeAll().map { it.map(BranchEntity::toDomain) }

    override fun branchesWithinBox(sw: GeoPoint, ne: GeoPoint): Flow<List<Branch>> =
        dao.observeWithinBox(sw.latitudeDeg, ne.latitudeDeg, sw.longitudeDeg, ne.longitudeDeg)
            .map { it.map(BranchEntity::toDomain) }

    override suspend fun refresh(): Result<Unit> = runCatching {
        val dtos = api.list()
        dao.replaceAll(dtos.map(BranchDto::toEntity))
    }
}
```

The "read from cache, refresh in background" pattern is critical: the branch locator is the most-used offline screen (users open it without internet to find an address). Always serve the cached list first.

---

## 4. The Map Screen

```kotlin
// :features-branch-locator/ui/BranchMapScreen.kt
@Composable
internal fun BranchMapScreen(
    viewModel: BranchLocatorViewModel = hiltViewModel(),
    onBranchSelected: (BranchId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.initialCenter.toLatLng(), 12f)
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = state.locationGranted),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.branches.forEach { branch ->
            Marker(
                state = MarkerState(position = branch.location.toLatLng()),
                title = branch.name,
                snippet = branch.openingHours,
                onClick = { viewModel.onEvent(BranchEvent.MarkerTapped(branch.id)); false },
            )
        }
    }

    state.selectedBranch?.let { branch ->
        BranchDetailSheet(
            branch = branch,
            onCallTapped = { viewModel.onEvent(BranchEvent.CallTapped(branch.phone)) },
            onDirectionsTapped = { viewModel.onEvent(BranchEvent.DirectionsTapped(branch.location)) },
            onDismiss = { viewModel.onEvent(BranchEvent.SheetDismissed) },
        )
    }
}
```

The Google Maps API key comes from `RuntimeConfig.thirdPartyAppIds.googleMaps` — never `BuildConfig`.

```kotlin
// :app/src/main/AndroidManifest.xml — uses placeholder
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}"/>
```

The placeholder is filled at app start via `MapsInitializer` using the MG-sourced value. (Google Maps SDK reads the manifest meta-data once at first init; for runtime override, use `MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST, callback)` after the value is available.)

---

## 5. Location Permission Flow

The "my location" button on the map requires `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Request at point-of-use, not at cold start:

```xml
<!-- :app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

```kotlin
// :features-branch-locator/ui/BranchMapScreen.kt
val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION) {
    viewModel.onEvent(BranchEvent.LocationPermissionResult(granted = it))
}

LaunchedEffect(Unit) {
    if (!locationPermission.status.isGranted) {
        locationPermission.launchPermissionRequest()
    }
}
```

If the user declines, the map still works — they just don't see "their position" or get a "directions from here" button. The branch list and addresses remain visible. Don't gate the whole feature on the permission.

`rememberPermissionState` is from **Accompanist Permissions** (`com.google.accompanist:accompanist-permissions`) or its successor when Accompanist deprecates. The framework should pick one and document it in `:aos-sdk/permissions/`.

---

## 6. Directions and Place Search

For "Get directions" — open the system's preferred maps app via Intent rather than embedding navigation:

```kotlin
// :features-branch-locator/ui/component/DirectionsLauncher.kt
fun launchDirections(context: Context, destination: GeoPoint, label: String) {
    val uri = Uri.parse("geo:0,0?q=${destination.latitudeDeg},${destination.longitudeDeg}(${Uri.encode(label)})")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // Fallback to any geo: handler
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
```

For **branch search by name/address**, query the local cache first. If results are insufficient, optionally fall back to Places API (`places-ktx`). For the PRD's scope, branch-name search over the cached list is sufficient — Places API is a future enhancement.

---

## 7. Offline Behavior

The branch list is the most-likely offline-accessed screen. Pattern:

1. **Cold start, offline:** `BranchRepository.branches()` emits the cached list from Room. Map tiles fail to load (Google Maps shows a "no internet" indicator) but markers and the list-tab still render correctly.
2. **Cold start, online:** Cache emits first, then a background refresh runs. New branches appear within seconds.
3. **Cache empty (first run, offline):** Show a "Connect to load branches" empty state. Refresh is enqueued via WorkManager for when connectivity returns ([28 — Background Work](28-background-work.md)).

Map tiles cannot be pre-cached — Google's licensing prohibits it. The branches themselves and their metadata can and should be.

---

## 8. What Does NOT Belong Here

| ❌ Not in this module | ✅ Belongs in |
|---|---|
| Google Maps API key in `BuildConfig` | `RuntimeConfig.thirdPartyAppIds.googleMaps` |
| Hardcoded branch list in the APK | Always server-served via `BranchRepository.refresh()` |
| Background location tracking | Out of scope; the framework requests location only at point-of-use |
| Custom map renderer (Mapbox, OpenStreetMap) | Stick with Google Maps unless there's a regulatory reason |
| Pre-caching map tiles | Google's TOS prohibits |
| Per-tenant branch filtering on the client | Server-side filtering: the API returns the branches relevant to the user's tenant |

---

## 9. Cross-references

- The Room-backed cache: [29 — Local Database](29-local-database.md)
- The WorkManager refresh job: [28 — Background Work](28-background-work.md)
- The `:aos-sdk/permissions/` requester: [02 — `:aos-sdk`](02-aos-core.md)
- The `RuntimeConfig.thirdPartyAppIds` contract: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The deeplink to a specific branch (future): [22 — Deeplinks](22-deeplinks.md)
