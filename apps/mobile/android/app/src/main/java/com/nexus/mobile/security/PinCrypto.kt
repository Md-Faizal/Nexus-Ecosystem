package com.nexus.mobile.security

import java.security.MessageDigest

/**
 * Matches `apps/mobile/rust-core/src/auth.rs` (`hash_pin` / `verify_pin`):
 * 10,000-round salted SHA-256 with domain separation and constant-time equality check.
 */
object PinCrypto {
    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("nexus-mobile-pin-v1:".toByteArray(Charsets.UTF_8))
        md.update(salt.toByteArray(Charsets.UTF_8))
        md.update(":".toByteArray(Charsets.UTF_8))
        md.update(pin.toByteArray(Charsets.UTF_8))
        var digest = md.digest()

        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        repeat(10_000) {
            md.reset()
            md.update(digest)
            md.update(saltBytes)
            digest = md.digest()
        }
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, salt: String, expectedHex: String): Boolean {
        val computed = hashPin(pin, salt)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedHex.toByteArray(Charsets.UTF_8),
        )
    }
}
