package com.bizplay.aoscore.security

import javax.inject.Inject

/**
 * Wraps TransKey (mtk SDK) — the on-screen keypad whose key codes never enter
 * the standard input pipeline. The plaintext password never leaves the SDK;
 * only the encrypted blob is returned for transmission.
 *
 * Product-agnostic: nothing about login flows lives here, only key collection
 * and encryption primitives. LoginScreen renders a [SecureKeypadHandle].
 */
interface SecureKeypad {
    suspend fun open(field: Field): SecureKeypadHandle

    enum class Field { PASSWORD, COMPANY_CODE_PIN }
}

/** A handle to an active keypad session. The plaintext stays inside the SDK. */
interface SecureKeypadHandle {
    val cipherText: String
    val plaintextLength: Int
    fun dismiss()
}

/**
 * Demo stub: pretends to encrypt by base64-ish wrapping. In production this is
 * replaced by the real TransKey integration.
 */
class StubSecureKeypad @Inject constructor() : SecureKeypad {
    override suspend fun open(field: SecureKeypad.Field): SecureKeypadHandle =
        object : SecureKeypadHandle {
            override val cipherText: String = ""
            override val plaintextLength: Int = 0
            override fun dismiss() {}
        }
}
