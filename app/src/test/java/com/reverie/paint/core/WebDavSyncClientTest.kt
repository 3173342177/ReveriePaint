/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.SyncException
import com.reverie.paint.core.sync.WebDavSyncClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WebDavSyncClientTest {
    private fun client(url: String = "https://example.com/remote.php/dav/files/u/ReveriePaint"): WebDavSyncClient =
        WebDavSyncClient(url, "user", "pass")

    @Test
    fun `conflict rename inserts suffix before extension`() {
        val c = client()
        assertEquals("a/b/name (conflict).revp", c.conflictRename("a/b/name.revp", " (conflict)"))
        assertEquals("name (conflict).revp", c.conflictRename("name.revp", " (conflict)"))
        assertEquals("name (conflict)", c.conflictRename("name", " (conflict)"))
        assertEquals("a/name (conflict).revp", c.conflictRename("/a/name.revp", " (conflict)"))
    }

    @Test
    fun `conflict rename keeps dotfiles without extension split`() {
        val c = client()
        assertEquals(".hidden (conflict)", c.conflictRename(".hidden", " (conflict)"))
    }

    @Test
    fun `invalid server url throws protocol error`() {
        try {
            WebDavSyncClient("not a url", "u", "p")
            fail("应当抛出 SyncException")
        } catch (e: SyncException) {
            assertEquals(SyncException.Kind.PROTOCOL, e.kind)
        }
    }
}
