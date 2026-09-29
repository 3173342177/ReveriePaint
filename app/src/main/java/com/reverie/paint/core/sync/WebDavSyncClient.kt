/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

import java.io.IOException
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class WebDavSyncClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient = OkHttpClient(),
) : SyncClient {
    private val baseUrl: HttpUrl =
        serverUrl.trim().toHttpUrlOrNull()
            ?: throw SyncException(SyncException.Kind.PROTOCOL, "非法的服务器地址: $serverUrl")

    override fun list(remotePath: String): List<RemoteEntry> {
        val xml = propfind(remotePath, depth = "1", trailingSlash = true)
        val basePath = baseUrl.encodedPath
        return WebDavMultistatus
            .parse(xml)
            .mapNotNull { res ->
                val rel = WebDavMultistatus.relativePathFromHref(res.href, basePath) ?: return@mapNotNull null
                if (rel.isEmpty() || rel == remotePath.trim('/')) return@mapNotNull null
                RemoteEntry(
                    path = rel,
                    isDirectory = res.isDirectory,
                    size = res.size,
                    lastModifiedMs = res.lastModifiedMs,
                    etag = res.etag,
                )
            }
    }

    override fun stat(remotePath: String): RemoteEntry? {
        val xml =
            try {
                propfind(remotePath, depth = "0")
            } catch (e: SyncException) {
                if (e.kind == SyncException.Kind.NOT_FOUND) return null
                throw e
            }
        val basePath = baseUrl.encodedPath
        val res =
            WebDavMultistatus.parse(xml).firstOrNull { r ->
                WebDavMultistatus.relativePathFromHref(r.href, basePath)?.trim('/') == remotePath.trim('/')
            } ?: return null
        return RemoteEntry(
            path = remotePath.trim('/'),
            isDirectory = res.isDirectory,
            size = res.size,
            lastModifiedMs = res.lastModifiedMs,
            etag = res.etag,
        )
    }

    override fun mkdir(remotePath: String) {
        val request = auth(Request.Builder().url(urlFor(remotePath)).method("MKCOL", null)).build()
        executeFollowingRedirects(request).use { response ->
            if (response.code != 201 && response.code != 405) {
                throw mapError(response.code, "MKCOL $remotePath")
            }
        }
    }

    override fun put(
        remotePath: String,
        data: ByteArray,
    ) {
        val body = data.toRequestBody(OCTET_STREAM)
        val request = auth(Request.Builder().url(urlFor(remotePath)).put(body)).build()
        executeFollowingRedirects(request).use { response ->
            if (response.code !in 200..299) throw mapError(response.code, "PUT $remotePath")
        }
    }

    override fun get(remotePath: String): ByteArray {
        val request = auth(Request.Builder().url(urlFor(remotePath)).get()).build()
        return try {
            executeFollowingRedirects(request).use { response ->
                if (response.code == 404) throw SyncException(SyncException.Kind.NOT_FOUND, "GET $remotePath")
                if (response.code !in 200..299) throw mapError(response.code, "GET $remotePath")
                response.body?.bytes() ?: ByteArray(0)
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: IOException) {
            throw SyncException(SyncException.Kind.NETWORK, "GET $remotePath 失败", e)
        }
    }

    override fun delete(remotePath: String) {
        val request = auth(Request.Builder().url(urlFor(remotePath)).delete()).build()
        executeFollowingRedirects(request).use { response ->
            if (response.code != 404 && response.code !in 200..299) {
                throw mapError(response.code, "DELETE $remotePath")
            }
        }
    }

    override fun conflictRename(
        remotePath: String,
        suffix: String,
    ): String {
        val path = remotePath.trim('/')
        val slash = path.lastIndexOf('/')
        val dir = if (slash >= 0) path.substring(0, slash + 1) else ""
        val name = if (slash >= 0) path.substring(slash + 1) else path
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return "$dir$stem$suffix$ext"
    }

    private fun propfind(
        remotePath: String,
        depth: String,
        trailingSlash: Boolean = false,
    ): String {
        val request = propfindRequest(remotePath, depth, trailingSlash)
        return try {
            executeFollowingRedirects(request).use { response ->
                if (response.code == 404) throw SyncException(SyncException.Kind.NOT_FOUND, "PROPFIND $remotePath")
                if (response.code != 207 && response.code !in 200..299) {
                    throw mapError(response.code, "PROPFIND $remotePath")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: IOException) {
            throw SyncException(SyncException.Kind.NETWORK, "PROPFIND $remotePath 失败", e)
        }
    }

    private fun propfindRequest(
        remotePath: String,
        depth: String,
        trailingSlash: Boolean,
    ): Request =
        auth(
            Request
                .Builder()
                .url(urlFor(remotePath, trailingSlash))
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
                .header("Depth", depth),
        ).build()

    private fun executeFollowingRedirects(request: Request): Response {
        var current = request
        var redirects = 0
        while (true) {
            val response =
                try {
                    client.newCall(current).execute()
                } catch (e: IOException) {
                    throw SyncException(SyncException.Kind.NETWORK, "请求失败: ${current.method}", e)
                }
            if (redirects >= MAX_REDIRECTS || response.code !in REDIRECT_CODES) {
                return response
            }
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) {
                throw SyncException(SyncException.Kind.PROTOCOL, "重定向缺少 Location")
            }
            val next =
                current.url.resolve(location)
                    ?: throw SyncException(SyncException.Kind.PROTOCOL, "无法解析重定向地址: $location")
            current = auth(current.newBuilder().url(next)).build()
            redirects++
        }
    }

    private fun urlFor(
        remotePath: String,
        trailingSlash: Boolean = false,
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        remotePath.trim('/').split('/').forEach { segment ->
            if (segment.isNotEmpty()) builder.addPathSegment(segment)
        }
        if (trailingSlash) builder.addPathSegment("")
        return builder.build()
    }

    private fun auth(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", Credentials.basic(username, password))

    private fun mapError(
        code: Int,
        what: String,
    ): SyncException =
        when (code) {
            401, 403 -> SyncException(SyncException.Kind.AUTH, "$what 鉴权失败 ($code)")
            404 -> SyncException(SyncException.Kind.NOT_FOUND, "$what 目标不存在")
            else -> SyncException(SyncException.Kind.PROTOCOL, "$what 失败, HTTP $code")
        }

    private companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        private val PROPFIND_BODY =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:resourcetype/>
                <D:getcontentlength/>
                <D:getlastmodified/>
                <D:getetag/>
              </D:prop>
            </D:propfind>
            """.trimIndent()
    }
}
