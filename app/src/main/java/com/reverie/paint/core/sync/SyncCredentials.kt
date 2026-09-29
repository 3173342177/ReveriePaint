/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

internal data class SyncCredentials(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
) {
    fun isConfigured(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()

    companion object {
        const val DEFAULT_SCHEME = "https"

        private val URL_REGEX = Regex("""^https?://[^\s/]+(/.*)?$""", RegexOption.IGNORE_CASE)

        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val withScheme =
                if (trimmed.contains("://")) trimmed else "$DEFAULT_SCHEME://$trimmed"
            return withScheme.trimEnd('/')
        }

        fun isValidServerUrl(raw: String): Boolean {
            val normalized = normalizeServerUrl(raw)
            return normalized.isNotEmpty() && URL_REGEX.matches(normalized)
        }

        fun redactServerUrl(raw: String): String {
            val normalized = normalizeServerUrl(raw)
            if (normalized.isEmpty()) return ""
            val schemeSep = normalized.indexOf("://")
            if (schemeSep <= 0) return normalized
            val scheme = normalized.substring(0, schemeSep)
            val rest = normalized.substring(schemeSep + 3).substringBefore('#').substringBefore('?')
            val slash = rest.indexOf('/')
            val authority = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash) else ""
            val hostPort = authority.substringAfterLast('@')
            return "$scheme://$hostPort$path"
        }
    }
}
