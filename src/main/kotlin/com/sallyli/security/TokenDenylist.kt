package com.sallyli.security

import java.util.concurrent.ConcurrentHashMap

class TokenDenylist {
    // jti -> expiresAt (epoch ms). Entries are lazily cleaned up when checked.
    private val revoked = ConcurrentHashMap<String, Long>()

    fun revoke(jti: String, expiresAtMs: Long) {
        revoked[jti] = expiresAtMs
    }

    fun isRevoked(jti: String): Boolean {
        val expiresAt = revoked[jti] ?: return false
        if (System.currentTimeMillis() > expiresAt) {
            revoked.remove(jti)  // token expired naturally, clean up
            return false
        }
        return true
    }
}
