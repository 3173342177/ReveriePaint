/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

import java.io.File

internal interface SyncClient {
    fun list(remotePath: String): List<RemoteEntry>

    fun stat(remotePath: String): RemoteEntry?

    fun mkdir(remotePath: String)

    fun putFile(
        remotePath: String,
        file: File,
    )

    fun getToFile(
        remotePath: String,
        target: File,
    )

    fun delete(remotePath: String)

    fun conflictRename(
        remotePath: String,
        suffix: String,
    ): String
}

internal data class RemoteEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedMs: Long,
    val etag: String?,
)

internal class SyncException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        NETWORK,
        AUTH,
        NOT_FOUND,
        PROTOCOL,
    }
}
