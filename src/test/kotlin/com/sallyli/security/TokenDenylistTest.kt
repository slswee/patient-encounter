package com.sallyli.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenDenylistTest {

    private val denylist = TokenDenylist()

    @Test
    fun testRevokedTokenIsRejected() {
        denylist.revoke("jti-abc", System.currentTimeMillis() + 60_000)
        assertTrue(denylist.isRevoked("jti-abc"))
    }

    @Test
    fun testUnknownTokenIsNotRevoked() {
        assertFalse(denylist.isRevoked("jti-never-seen"))
    }

    @Test
    fun testExpiredDenylistEntryIsNotConsideredRevoked() {
        // Token expired 1 second ago — should not be treated as actively revoked
        denylist.revoke("jti-expired", System.currentTimeMillis() - 1_000)
        assertFalse(denylist.isRevoked("jti-expired"))
    }

    @Test
    fun testExpiredEntryIsRemovedOnCheck() {
        denylist.revoke("jti-cleanup", System.currentTimeMillis() - 1_000)
        denylist.isRevoked("jti-cleanup")           // triggers lazy removal
        assertFalse(denylist.isRevoked("jti-cleanup"))
    }

    @Test
    fun testMultipleTokensTrackedIndependently() {
        denylist.revoke("jti-1", System.currentTimeMillis() + 60_000)
        denylist.revoke("jti-2", System.currentTimeMillis() + 60_000)

        assertTrue(denylist.isRevoked("jti-1"))
        assertTrue(denylist.isRevoked("jti-2"))
        assertFalse(denylist.isRevoked("jti-3"))
    }
}
