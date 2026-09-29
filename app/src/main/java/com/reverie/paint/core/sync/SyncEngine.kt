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

internal data class ManifestEntry(
    val hash: String,
    val token: String,
)

internal data class BackupOutcome(
    val uploaded: Int,
    val skipped: Int,
    val remoteChanged: Int,
    val deleted: Int,
    val failed: Int,
    val bytes: Long,
    val errors: List<String>,
    val manifest: Map<String, ManifestEntry>,
)

internal data class RestoreOutcome(
    val downloaded: Int,
    val updated: Int,
    val skipped: Int,
    val conflicts: Int,
    val failed: Int,
    val bytes: Long,
    val errors: List<String>,
    val manifest: Map<String, ManifestEntry>,
)

internal enum class SyncCategory { ARTWORKS, BRUSHES }

internal data class SyncSource(
    val category: SyncCategory,
    val localDir: File,
    val remotePrefix: String,
    val accept: (String) -> Boolean,
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

    fun isPlainFile(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it.startsWith(".") }) return false
        return !segments.last().lowercase().endsWith(".tmp")
    }

    fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith("/")) return false
        return normalized.split('/').all { it.isNotEmpty() && it != "." && it != ".." && !it.contains('\u0000') }
    }

    fun scan(sources: List<SyncSource>): List<LocalProject> {
        val out = ArrayList<LocalProject>()
        for (source in sources) {
            if (!source.localDir.isDirectory) continue
            source.localDir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val rel = file.relativeTo(source.localDir).path.replace(File.separatorChar, '/')
                if (!isSafeRelativePath(rel) || !source.accept(rel)) return@forEach
                out.add(LocalProject(source.remotePrefix + rel, file, file.length(), sha256(file)))
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
        return hex(digest.digest())
    }

    fun sha256(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    fun versionToken(entry: RemoteEntry): String {
        val etag = entry.etag
        if (!etag.isNullOrEmpty()) return etag
        return "${entry.size}:${entry.lastModifiedMs}"
    }

    fun encodeManifest(manifest: Map<String, ManifestEntry>): String =
        manifest.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}\t${it.value.hash}\t${sanitize(it.value.token)}" }

    fun decodeManifest(text: String): Map<String, ManifestEntry> {
        val out = LinkedHashMap<String, ManifestEntry>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0].isEmpty()) continue
            out[parts[0]] = ManifestEntry(parts[1], parts.getOrElse(2) { "" })
        }
        return out
    }

    private fun sanitize(token: String): String = token.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

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

    fun writeAtomically(
        target: File,
        data: ByteArray,
    ) {
        val parent = target.parentFile
        parent?.mkdirs()
        val temp = File(parent, "${target.name}.part")
        try {
            temp.outputStream().use { out ->
                out.write(data)
                out.flush()
                (out as? java.io.FileOutputStream)?.fd?.sync()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    fun writeTextAtomically(
        target: File,
        text: String,
    ) = writeAtomically(target, text.toByteArray(Charsets.UTF_8))

    fun assignSource(
        sources: List<SyncSource>,
        remotePath: String,
    ): SyncSource? =
        sources
            .filter { remotePath.startsWith(it.remotePrefix) }
            .maxByOrNull { it.remotePrefix.length }

    fun localTarget(
        sources: List<SyncSource>,
        remotePath: String,
    ): File? {
        val source = assignSource(sources, remotePath) ?: return null
        val rel = remotePath.removePrefix(source.remotePrefix)
        if (!isSafeRelativePath(rel) || !source.accept(rel)) return null
        return File(source.localDir, rel)
    }

    fun conflictName(
        name: String,
        suffix: String,
    ): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            "${name.substring(0, dot)}$suffix${name.substring(dot)}"
        } else {
            name + suffix
        }
    }

    fun listRemote(
        client: SyncClient,
        sources: List<SyncSource>,
        category: SyncCategory,
    ): List<RemoteEntry> {
        val wanted = sources.filter { it.category == category }
        val startPrefix = commonDirPrefix(wanted.map { it.remotePrefix }).trimEnd('/')
        val excluded = sources.filter { it.category != category }.map { it.remotePrefix }.filter { it.isNotEmpty() }

        val out = ArrayList<RemoteEntry>()
        val dirs = ArrayDeque<String>()
        dirs.add(startPrefix)
        var visited = 0
        while (dirs.isNotEmpty() && visited < MAX_REMOTE_DIRS) {
            val dir = dirs.removeFirst()
            visited++
            val entries =
                try {
                    client.list(dir)
                } catch (e: SyncException) {
                    if (e.kind == SyncException.Kind.NOT_FOUND) continue
                    throw e
                }
            for (entry in entries) {
                if (!isSafeRelativePath(entry.path)) continue
                if (excluded.any { entry.path == it.trimEnd('/') || entry.path.startsWith(it) }) continue
                if (entry.isDirectory) {
                    dirs.add(entry.path)
                    continue
                }
                val source = assignSource(sources, entry.path) ?: continue
                if (source.category != category) continue
                val rel = entry.path.removePrefix(source.remotePrefix)
                if (!isSafeRelativePath(rel) || !source.accept(rel)) continue
                out.add(entry)
            }
        }
        return out.sortedBy { it.path }
    }

    fun listRemoteAll(
        client: SyncClient,
        sources: List<SyncSource>,
    ): List<RemoteEntry> = SyncCategory.values().flatMap { listRemote(client, sources, it) }

    fun planUploads(
        local: List<LocalProject>,
        manifest: Map<String, ManifestEntry>,
        remoteTokens: Map<String, String>,
    ): List<LocalProject> =
        local.filter { project ->
            val recorded = manifest[project.relativePath] ?: return@filter true
            if (project.sha256 != recorded.hash) return@filter true
            val now = remoteTokens[project.relativePath] ?: return@filter true
            recorded.token.isNotEmpty() && now != recorded.token
        }

    fun planRemoteChanged(
        local: List<LocalProject>,
        manifest: Map<String, ManifestEntry>,
        remoteTokens: Map<String, String>,
    ): List<String> =
        local.mapNotNull { project ->
            val recorded = manifest[project.relativePath] ?: return@mapNotNull null
            val now = remoteTokens[project.relativePath] ?: return@mapNotNull null
            if (recorded.token.isNotEmpty() && now != recorded.token) project.relativePath else null
        }

    fun planDeletions(
        sources: List<SyncSource>,
        local: List<LocalProject>,
        manifest: Map<String, ManifestEntry>,
        remoteTokens: Map<String, String>,
    ): List<String> {
        val localPaths = local.mapTo(HashSet()) { it.relativePath }
        return manifest.keys
            .filter { it !in localPaths }
            .filter { path -> assignSource(sources, path)?.localDir?.isDirectory == true }
            .filter { path ->
                val recorded = manifest[path]
                val now = remoteTokens[path] ?: return@filter true
                recorded == null || recorded.token.isEmpty() || now == recorded.token
            }
            .sorted()
    }

    fun backup(
        client: SyncClient,
        sources: List<SyncSource>,
        local: List<LocalProject>,
        manifest: Map<String, ManifestEntry>,
        remoteEntries: List<RemoteEntry>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BackupOutcome {
        val remoteTokens = remoteEntries.associate { it.path to versionToken(it) }
        val remoteChanged = planRemoteChanged(local, manifest, remoteTokens)
        val changedSet = remoteChanged.toHashSet()
        val uploads = planUploads(local, manifest, remoteTokens).filter { it.relativePath !in changedSet }
        val deletions = planDeletions(sources, local, manifest, remoteTokens)
        val newManifest = LinkedHashMap(manifest)
        val errors = ArrayList<String>()
        var uploaded = 0
        var deleted = 0
        var bytes = 0L

        for (path in remoteChanged) {
            errors.add("$path: cloud changed by another device, not overwritten")
        }

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
                newManifest[project.relativePath] = ManifestEntry(project.sha256, tokenAfterUpload(client, project.relativePath))
                uploaded++
                bytes += project.size
            } catch (e: Exception) {
                errors.add("${project.relativePath}: ${e.message ?: e.javaClass.simpleName}")
            }
            onProgress(index + 1, total)
        }

        for (path in deletions) {
            try {
                client.delete(path)
                newManifest.remove(path)
                deleted++
            } catch (e: Exception) {
                errors.add("$path: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        val keepDirs = requiredRemoteDirs(local.map { it.relativePath }).toSet()
        val staleDirs = requiredRemoteDirs(deletions).filter { it !in keepDirs }.sortedByDescending { it.length }
        for (dir in staleDirs) {
            try {
                client.delete(dir)
            } catch (_: Exception) {
            }
        }

        return BackupOutcome(
            uploaded = uploaded,
            skipped = local.size - uploads.size - remoteChanged.size,
            remoteChanged = remoteChanged.size,
            deleted = deleted,
            failed = errors.size,
            bytes = bytes,
            errors = errors,
            manifest = newManifest,
        )
    }

    fun restore(
        client: SyncClient,
        sources: List<SyncSource>,
        category: SyncCategory,
        local: List<LocalProject>,
        manifest: Map<String, ManifestEntry>,
        conflictSuffix: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RestoreOutcome {
        val remote = listRemote(client, sources, category)
        val localHashes = local.associate { it.relativePath to it.sha256 }
        val newManifest = LinkedHashMap(manifest)
        val errors = ArrayList<String>()
        var downloaded = 0
        var updated = 0
        var skipped = 0
        var conflicts = 0
        var bytes = 0L

        onProgress(0, remote.size)
        for ((index, entry) in remote.withIndex()) {
            val target = localTarget(sources, entry.path)
            if (target == null) {
                errors.add("${entry.path}: invalid path")
                onProgress(index + 1, remote.size)
                continue
            }
            val recorded = manifest[entry.path]
            val existingHash = localHashes[entry.path]
            val token = versionToken(entry)
            val localUnchanged = existingHash != null && existingHash == recorded?.hash
            val remoteChanged = recorded != null && recorded.token.isNotEmpty() && recorded.token != token

            if (existingHash != null && localUnchanged && !remoteChanged) {
                skipped++
                onProgress(index + 1, remote.size)
                continue
            }
            try {
                val data = client.get(entry.path)
                val hash = sha256(data)
                if (recorded != null && !remoteChanged && recorded.hash.isNotEmpty() && hash != recorded.hash) {
                    errors.add("${entry.path}: checksum mismatch")
                } else if (existingHash == null) {
                    writeAtomically(target, data)
                    newManifest[entry.path] = ManifestEntry(hash, token)
                    downloaded++
                    bytes += data.size.toLong()
                } else if (localUnchanged) {
                    writeAtomically(target, data)
                    newManifest[entry.path] = ManifestEntry(hash, token)
                    updated++
                    bytes += data.size.toLong()
                } else if (hash == existingHash) {
                    newManifest[entry.path] = ManifestEntry(hash, token)
                    skipped++
                } else {
                    val copy = File(target.parentFile, conflictName(target.name, conflictSuffix))
                    if (isSafeRelativePath(copy.name)) {
                        writeAtomically(copy, data)
                        conflicts++
                        bytes += data.size.toLong()
                    } else {
                        errors.add("${entry.path}: invalid conflict path")
                    }
                }
            } catch (e: Exception) {
                errors.add("${entry.path}: ${e.message ?: e.javaClass.simpleName}")
            }
            onProgress(index + 1, remote.size)
        }

        return RestoreOutcome(
            downloaded = downloaded,
            updated = updated,
            skipped = skipped,
            conflicts = conflicts,
            failed = errors.size,
            bytes = bytes,
            errors = errors,
            manifest = newManifest,
        )
    }

    private fun tokenAfterUpload(
        client: SyncClient,
        path: String,
    ): String =
        try {
            client.stat(path)?.let { versionToken(it) } ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun commonDirPrefix(prefixes: List<String>): String {
        if (prefixes.isEmpty()) return ""
        var common = prefixes.first()
        for (other in prefixes) {
            var i = 0
            while (i < common.length && i < other.length && common[i] == other[i]) i++
            common = common.substring(0, i)
        }
        val slash = common.lastIndexOf('/')
        return if (slash >= 0) common.substring(0, slash + 1) else ""
    }

    private const val MAX_REMOTE_DIRS = 512

    private val HEX = "0123456789abcdef".toCharArray()
}
