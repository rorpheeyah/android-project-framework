package com.compass.app.session

import com.compass.app.variant.VariantCatalogue
import com.compass.core.model.LoginResponse
import com.compass.core.session.AccountId
import com.compass.core.session.Session
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionFactory @Inject constructor(
    private val variantCatalogue: VariantCatalogue,
) {
    fun build(login: LoginResponse, activeAccountId: AccountId): Session = Session(
        userSession = login.userSession,
        variantContext = variantCatalogue.resolve(login.variantId),
        accounts = login.accounts,
        initialActiveAccount = activeAccountId,
    )
}
