/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.SyncCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCredentialsTest {

    @Test
    fun `normalize adds https and trims trailing slash`() {
        assertEquals("https://cloud.example.com/dav", SyncCredentials.normalizeServerUrl("cloud.example.com/dav/"))
        assertEquals("https://cloud.example.com/dav", SyncCredentials.normalizeServerUrl("  cloud.example.com/dav  "))
        assertEquals("http://nas.local:8080/dav", SyncCredentials.normalizeServerUrl("http://nas.local:8080/dav/"))
        assertEquals("", SyncCredentials.normalizeServerUrl("   "))
    }

    @Test
    fun `valid server url accepts http https and non-ascii path`() {
        assertTrue(SyncCredentials.isValidServerUrl("https://cloud.example.com"))
        assertTrue(SyncCredentials.isValidServerUrl("https://cloud.example.com/remote.php/dav/files/user"))
        assertTrue(SyncCredentials.isValidServerUrl("http://192.168.1.10:8080/dav"))
        assertTrue(SyncCredentials.isValidServerUrl("https://cloud.example.com/dav/画集"))
        assertTrue(SyncCredentials.isValidServerUrl("cloud.example.com"))
    }

    @Test
    fun `invalid server url is rejected`() {
        assertFalse(SyncCredentials.isValidServerUrl(""))
        assertFalse(SyncCredentials.isValidServerUrl("https://"))
        assertFalse(SyncCredentials.isValidServerUrl("ftp://example.com"))
        assertFalse(SyncCredentials.isValidServerUrl("not a url"))
        assertFalse(SyncCredentials.isValidServerUrl("https://has space.com/dav"))
    }

    @Test
    fun `redact strips userinfo query and fragment`() {
        assertEquals(
            "https://cloud.example.com/dav",
            SyncCredentials.redactServerUrl("https://user:pass@cloud.example.com/dav?token=abc#frag"),
        )
        assertEquals(
            "https://cloud.example.com:8443/dav",
            SyncCredentials.redactServerUrl("https://cloud.example.com:8443/dav/"),
        )
        assertEquals(
            "https://cloud.example.com",
            SyncCredentials.redactServerUrl("https://cloud.example.com"),
        )
        assertEquals("", SyncCredentials.redactServerUrl(""))
    }

    @Test
    fun `isConfigured requires server url and username`() {
        assertTrue(SyncCredentials(serverUrl = "https://x.com", username = "u").isConfigured())
        assertFalse(SyncCredentials(serverUrl = "https://x.com").isConfigured())
        assertFalse(SyncCredentials(username = "u").isConfigured())
    }
}
