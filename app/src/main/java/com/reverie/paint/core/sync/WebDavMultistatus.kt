/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

internal object WebDavMultistatus {
    private const val NS_DAV = "DAV:"

    data class DavResource(
        val href: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModifiedMs: Long,
        val etag: String?,
    )

    fun parse(xml: String): List<DavResource> {
        val doc =
            try {
                DocumentBuilderFactory
                    .newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .apply {
                        setErrorHandler(
                            object : ErrorHandler {
                                override fun warning(e: SAXParseException) = Unit

                                override fun error(e: SAXParseException) {
                                    throw e
                                }

                                override fun fatalError(e: SAXParseException) {
                                    throw e
                                }
                            },
                        )
                    }
                    .parse(InputSource(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))))
            } catch (e: Exception) {
                throw SyncException(SyncException.Kind.PROTOCOL, "multistatus 解析失败", e)
            }

        val root = doc.documentElement ?: return emptyList()
        val out = ArrayList<DavResource>()
        for (response in children(root, "response")) {
            val href = children(response, "href").firstOrNull()?.textContent?.trim().orEmpty()
            if (href.isEmpty()) continue

            var isDirectory = false
            var size = 0L
            var lastModified = 0L
            var etag: String? = null

            for (propstat in children(response, "propstat")) {
                if (!isSuccessfulStatus(textOf(propstat, "status"))) continue
                val prop = children(propstat, "prop").firstOrNull() ?: continue
                val resourceType = children(prop, "resourcetype").firstOrNull()
                if (resourceType != null && children(resourceType, "collection").isNotEmpty()) {
                    isDirectory = true
                }
                children(prop, "getcontentlength").firstOrNull()?.textContent?.trim()?.let {
                    size = it.toLongOrNull() ?: 0L
                }
                children(prop, "getlastmodified").firstOrNull()?.textContent?.trim()?.let {
                    lastModified = parseHttpDate(it)
                }
                children(prop, "getetag").firstOrNull()?.textContent?.trim()?.let {
                    if (it.isNotEmpty()) etag = it
                }
            }

            out.add(DavResource(href, isDirectory, size, lastModified, etag))
        }
        return out
    }

    fun relativePathFromHref(
        href: String,
        basePath: String,
    ): String? {
        val noQuery = href.substringBefore('?').substringBefore('#')
        val pathOnly = pathOf(noQuery)
        val decoded = percentDecode(pathOnly)
        val base = normalizeBase(basePath)
        if (base.isNotEmpty() && !decoded.startsWith(base)) return null
        return decoded.removePrefix(base).trim('/')
    }

    private fun pathOf(raw: String): String {
        if (raw.startsWith("//")) {
            val slash = raw.indexOf('/', 2)
            return if (slash < 0) "/" else raw.substring(slash)
        }
        val schemeSep = raw.indexOf("://")
        if (schemeSep < 0) return raw
        val afterScheme = raw.substring(schemeSep + 3)
        val slash = afterScheme.indexOf('/')
        return if (slash < 0) "/" else afterScheme.substring(slash)
    }

    fun percentDecode(s: String): String {
        val bytes = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            val cp =
                if (Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) {
                    s.substring(i, i + 2).codePointAt(0).also { i++ }
                } else {
                    c.code
                }
            bytes.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun normalizeBase(basePath: String): String {
        val decoded = percentDecode(basePath.substringBefore('?').substringBefore('#'))
        if (decoded.isEmpty() || decoded == "/") return ""
        return "/" + decoded.trim('/')
    }

    private fun isSuccessfulStatus(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        val parts = status.trim().split(' ')
        if (parts.size < 2) return false
        val code = parts[1].toIntOrNull() ?: return false
        return code in 200..299
    }

    private fun parseHttpDate(raw: String): Long {
        val patterns =
            listOf(
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE MMM d HH:mm:ss yyyy",
            )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
                return fmt.parse(raw)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun textOf(
        parent: Element,
        localName: String,
    ): String? = children(parent, localName).firstOrNull()?.textContent?.trim()

    private fun children(
        parent: Element,
        localName: String,
    ): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            val ns = el.namespaceURI
            val nameMatches = el.localName == localName || el.tagName == localName || el.tagName.endsWith(":$localName")
            if (nameMatches && (ns == null || ns == NS_DAV)) out.add(el)
        }
        return out
    }
}
