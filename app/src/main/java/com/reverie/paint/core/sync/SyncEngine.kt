/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

import java.io.File
import java.security.MessageDigest

internal data class LocalProject(
    val relativePath: String,
    val file: File,
    val size: Long,
    val sha256: String,
)

internal data class BackupOutcome(
    val uploaded: Int,
    val skipped: Int,
    val failed: Int,
    val bytes: Long,
    val errors: List<String>,
    val manifest: Map<String, String>,
)

internal object SyncEngine {
    private const val EXT = ".revp"

    fun isSyncable(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        if (!normalized.endsWith(EXT, ignoreCase = true)) return false
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it.startsWith(".") }) return false
        if (segments.any { it.equals("autosave", ignoreCase = true) }) return false
        val name = segments.last().lowercase()
        if (name.endsWith(".autosave$EXT")) return false
        if (name.endsWith(".tmp")) return false
        return true
    }

    fun scan(rootDir: File): List<LocalProject> {
        if (!rootDir.isDirectory) return emptyList()
        val out = ArrayList<LocalProject>()
        rootDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val rel = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            if (isSyncable(rel)) {
                out.add(LocalProject(rel, file, file.length(), sha256(file)))
            }
        }
        return out.sortedBy { it.relativePath }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    fun planUploads(
        local: List<LocalProject>,
        manifest: Map<String, String>,
    ): List<LocalProject> = local.filter { manifest[it.relativePath] != it.sha256 }

    fun requiredRemoteDirs(paths: List<String>): List<String> {
        val dirs = LinkedHashSet<String>()
        for (path in paths) {
            var idx = path.indexOf('/')
            while (idx >= 0) {
                dirs.add(path.substring(0, idx))
                idx = path.indexOf('/', idx + 1)
            }
        }
        return dirs.sorted()
    }

    fun encodeManifest(manifest: Map<String, String>): String =
        manifest.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" }

    fun decodeManifest(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            out[line.substring(0, tab)] = line.substring(tab + 1)
        }
        return out
    }

    fun backup(
        client: SyncClient,
        local: List<LocalProject>,
        manifest: Map<String, String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BackupOutcome {
        val uploads = planUploads(local, manifest)
        val newManifest = LinkedHashMap(manifest)
        val errors = ArrayList<String>()
        var uploaded = 0
        var bytes = 0L

        for (dir in requiredRemoteDirs(local.map { it.relativePath })) {
            try {
                client.mkdir(dir)
            } catch (_: Exception) {
            }
        }

        val total = uploads.size
        onProgress(0, total)
        for ((index, project) in uploads.withIndex()) {
            try {
                client.put(project.relativePath, project.file.readBytes())
                newManifest[project.relativePath] = project.sha256
                uploaded++
                bytes += project.size
            } catch (e: Exception) {
                errors.add("${project.relativePath}: ${e.message ?: e.javaClass.simpleName}")
            }
            onProgress(index + 1, total)
        }

        return BackupOutcome(
            uploaded = uploaded,
            skipped = local.size - uploads.size,
            failed = errors.size,
            bytes = bytes,
            errors = errors,
            manifest = newManifest,
        )
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
