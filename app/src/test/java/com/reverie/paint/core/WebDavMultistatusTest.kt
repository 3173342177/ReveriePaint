/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.SyncException
import com.reverie.paint.core.sync.WebDavMultistatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebDavMultistatusTest {
    private val base = "/remote.php/dav/files/user/ReveriePaint"

    @Test
    fun `parses nextcloud style multistatus with d prefix`() {
        val xml =
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>$base/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>$base/sketch.revp</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>1048576</d:getcontentlength>
                    <d:getlastmodified>Wed, 01 Jan 2025 12:34:56 GMT</d:getlastmodified>
                    <d:getetag>"abc123"</d:getetag>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """.trimIndent()

        val out = WebDavMultistatus.parse(xml)
        assertEquals(2, out.size)
        assertTrue(out[0].isDirectory)
        assertEquals(0L, out[0].size)

        val file = out[1]
        assertFalse(file.isDirectory)
        assertEquals("$base/sketch.revp", file.href)
        assertEquals(1048576L, file.size)
        assertEquals(Instant.parse("2025-01-01T12:34:56Z").toEpochMilli(), file.lastModifiedMs)
        assertEquals("\"abc123\"", file.etag)
    }

    @Test
    fun `handles default namespace and missing optional props`() {
        val xml =
            """
            <multistatus xmlns="DAV:">
              <response>
                <href>$base/note.revp</href>
                <propstat>
                  <prop><getcontentlength>2048</getcontentlength></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """.trimIndent()

        val out = WebDavMultistatus.parse(xml)
        assertEquals(1, out.size)
        assertEquals(2048L, out[0].size)
        assertEquals(0L, out[0].lastModifiedMs)
        assertNull(out[0].etag)
        assertFalse(out[0].isDirectory)
    }

    @Test
    fun `ignores properties from failed propstat`() {
        val xml =
            """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>$base/x.revp</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>999</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 403 Forbidden</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:getcontentlength>7</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """.trimIndent()

        assertEquals(7L, WebDavMultistatus.parse(xml).single().size)
    }

    @Test
    fun `malformed xml throws protocol error`() {
        try {
            WebDavMultistatus.parse("<not-closed")
            fail("应当抛出 SyncException")
        } catch (e: SyncException) {
            assertEquals(SyncException.Kind.PROTOCOL, e.kind)
        }
    }

    @Test
    fun `maps href to path relative to base`() {
        assertEquals(
            "sketch.revp",
            WebDavMultistatus.relativePathFromHref("$base/sketch.revp", base),
        )
        assertEquals(
            "画集/a.revp",
            WebDavMultistatus.relativePathFromHref("$base/%E7%94%BB%E9%9B%86/a.revp", base),
        )
        assertEquals(
            "",
            WebDavMultistatus.relativePathFromHref("$base/", base),
        )
        assertEquals(
            "sketch.revp",
            WebDavMultistatus.relativePathFromHref("$base/sketch.revp", "$base/"),
        )
    }

    @Test
    fun `maps absolute url and protocol relative href`() {
        assertEquals(
            "a.revp",
            WebDavMultistatus.relativePathFromHref("https://cloud.example.com$base/a.revp", base),
        )
        assertEquals(
            "画集/a.revp",
            WebDavMultistatus.relativePathFromHref("https://cloud.example.com$base/%E7%94%BB%E9%9B%86/a.revp", base),
        )
        assertEquals(
            "a.revp",
            WebDavMultistatus.relativePathFromHref("//cloud.example.com$base/a.revp", base),
        )
    }

    @Test
    fun `rejects href outside base`() {
        assertNull(WebDavMultistatus.relativePathFromHref("/other/place/x.revp", base))
        assertNull(WebDavMultistatus.relativePathFromHref("https://cloud.example.com/other/x.revp", base))
    }

    @Test
    fun `percent decode handles utf8 and leaves plus untouched`() {
        assertEquals(" ", WebDavMultistatus.percentDecode("%20"))
        assertEquals("中", WebDavMultistatus.percentDecode("%E4%B8%AD"))
        assertEquals("中文 file", WebDavMultistatus.percentDecode("%E4%B8%AD%E6%96%87%20file"))
        assertEquals("a+b", WebDavMultistatus.percentDecode("a+b"))
        assertEquals("%ZZ", WebDavMultistatus.percentDecode("%ZZ"))
        assertEquals("plain", WebDavMultistatus.percentDecode("plain"))
    }
}
