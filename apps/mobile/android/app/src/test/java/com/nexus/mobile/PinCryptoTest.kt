package com.nexus.mobile

import com.nexus.mobile.security.PinCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCryptoTest {
    @Test
    fun pinHashAndVerifyMatchesConstantTimeCheck() {
        val hash = PinCrypto.hashPin("2580", "salt-123")
        assertTrue(PinCrypto.verifyPin("2580", "salt-123", hash))
        assertFalse(PinCrypto.verifyPin("2581", "salt-123", hash))
        assertFalse(PinCrypto.verifyPin("2580", "wrong-salt", hash))
    }
}
