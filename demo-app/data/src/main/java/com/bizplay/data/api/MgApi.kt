package com.bizplay.data.api

import com.bizplay.data.api.dto.MgGateResponseDto
import retrofit2.http.GET

/**
 * The single hardcoded endpoint. Per framework rule #8, the MgGate URL is the only
 * network configuration baked into the binary; everything else comes from the
 * RuntimeConfig response payload returned here.
 *
 * Existing Bizplay equivalent: `Conf.SITE_MG_URL + "/MgGate"`.
 */
internal interface MgApi {

    @GET("MgGate")
    suspend fun fetchRuntimeConfig(): MgGateResponseDto
}
