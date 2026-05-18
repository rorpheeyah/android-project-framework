package com.bizplay.data.api.dto

import com.bizplay.core.runtime.ApiUrls
import com.bizplay.core.runtime.ForceUpdate
import com.bizplay.core.runtime.MaintenanceState
import com.bizplay.core.runtime.RuntimeConfig
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class MgGateResponseDto(
    val urls: UrlsDto,
    val webRoutes: Map<String, String>?,
    val maintenance: MaintenanceDto,
    val forceUpdate: ForceUpdateDto,
)

@JsonClass(generateAdapter = true)
internal data class UrlsDto(
    val main: String,
    val auxiliary: String?,
)

@JsonClass(generateAdapter = true)
internal data class MaintenanceDto(
    val status: String,
    val message: String?,
    val etaIso8601: String?,
)

@JsonClass(generateAdapter = true)
internal data class ForceUpdateDto(
    val minimumVersionCode: Int,
    val storeUrl: String,
)

internal fun MgGateResponseDto.toDomain(): RuntimeConfig = RuntimeConfig(
    urls = ApiUrls(main = urls.main, auxiliary = urls.auxiliary),
    webRoutes = webRoutes.orEmpty(),
    maintenance = MaintenanceState(
        status = if (maintenance.status.equals("down", ignoreCase = true))
            MaintenanceState.Status.DOWN else MaintenanceState.Status.UP,
        message = maintenance.message,
        etaIso8601 = maintenance.etaIso8601,
    ),
    forceUpdate = ForceUpdate(
        minimumVersionCode = forceUpdate.minimumVersionCode,
        storeUrl = forceUpdate.storeUrl,
    ),
)
