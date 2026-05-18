package com.bizplay.demo.navigation

/** Single source of truth for navigation routes. */
object Routes {
    const val BOOT = "boot"
    const val LOGIN = "login"

    private const val SELECT_INSTITUTION_BASE = "select_institution"
    const val ARG_MODE = "mode"
    const val SELECT_INSTITUTION = "$SELECT_INSTITUTION_BASE/{$ARG_MODE}"

    fun selectInstitution(mode: String): String = "$SELECT_INSTITUTION_BASE/$mode"

    const val HOME = "home"
}
