/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.ManifestEntry
import com.reverie.paint.core.sync.RemoteEntry
import com.reverie.paint.core.sync.SyncCategory
import com.reverie.paint.core.sync.SyncClient
import com.reverie.paint.core.sync.SyncEngine
import com.reverie.paint.core.sync.SyncException
import com.reverie.paint.core.sync.SyncSource
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineRestoreTest {
    private val suffix = " (cloud)"

    private fun sources(
        root: File,
        brushRoot: File,
    ): List<SyncSource> =
        listOf(
            SyncSource(SyncCategory.ARTWORKS, root, "") { SyncEngine.isSyncable(it) },
            SyncSource(SyncCategory.ARTWORKS, File(root, "autosave"), "autosave/") {
                SyncEngine.isPlainFile(it) && it.endsWith(".revp", ignoreCase = true)
            },
            SyncSource(SyncCategory.BRUSHES, File(brushRoot, "presets"), "brushes/presets/") {
                SyncEngine.isPlainFile(it)
            },
            SyncSource(SyncCategory.BRUSHES, File(brushRoot, "tips"), "brushes/tips/") {
                SyncEngine.isPlainFile(it)
            },
        )

    @Test
    fun `safe relative path rejects traversal and absolute`() {
        assertTrue(SyncEngine.isSafeRelativePath("a.revp"))
        assertTrue(SyncEngine.isSafeRelativePath("画集/b.revp"))
        assertFalse(SyncEngine.isSafeRelativePath(""))
        assertFalse(SyncEngine.isSafeRelativePath("/abs.revp"))
        assertFalse(SyncEngine.isSafeRelativePath("../evil.revp"))
        assertFalse(SyncEngine.isSafeRelativePath("a/../../b.revp"))
        assertFalse(SyncEngine.isSafeRelativePath("a/./b.revp"))
    }

    @Test
    fun `writeAtomically writes content and leaves no temp file`() {
        val root = Files.createTempDirectory("revp-atomic").toFile()
        try {
            val target = File(root, "sub/a.revp")
            SyncEngine.writeAtomically(target, "hello".toByteArray())
            assertEquals("hello", target.readText())
            assertFalse(File(root, "sub/a.revp.part").exists())

            SyncEngine.writeAtomically(target, "world".toByteArray())
            assertEquals("world", target.readText())
            assertEquals(1, File(root, "sub").listFiles()!!.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `conflictName inserts suffix before extension`() {
        assertEquals("a (cloud).revp", SyncEngine.conflictName("a.revp", suffix))
        assertEquals("a (cloud)", SyncEngine.conflictName("a", suffix))
    }

    @Test
    fun `scan adds remote prefixes per source`() {
        val root = Files.createTempDirectory("revp-scan-root").toFile()
        val brushes = Files.createTempDirectory("revp-scan-brush").toFile()
        try {
            root.resolve("a.revp").writeText("a")
            File(root, "autosave").mkdirs()
            root.resolve("autosave/x.autosave.revp").writeText("x")
            File(brushes, "presets").mkdirs()
            File(brushes, "tips").mkdirs()
            File(brushes, "presets/my.kpp").writeText("p")
            File(brushes, "tips/my.gbr").writeText("t")

            val paths = SyncEngine.scan(sources(root, brushes)).map { it.relativePath }.toSet()
            assertEquals(
                setOf(
                    "a.revp",
                    "autosave/x.autosave.revp",
                    "brushes/presets/my.kpp",
                    "brushes/tips/my.gbr",
                ),
                paths,
            )
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `localTarget picks the longest matching prefix`() {
        val root = Files.createTempDirectory("revp-target").toFile()
        val brushes = Files.createTempDirectory("revp-target-brush").toFile()
        try {
            val src = sources(root, brushes)
            assertEquals(File(root, "a.revp"), SyncEngine.localTarget(src, "a.revp"))
            assertEquals(File(root, "autosave/x.autosave.revp"), SyncEngine.localTarget(src, "autosave/x.autosave.revp"))
            assertEquals(File(brushes, "tips/my.gbr"), SyncEngine.localTarget(src, "brushes/tips/my.gbr"))
            assertEquals(File(brushes, "presets/my.kpp"), SyncEngine.localTarget(src, "brushes/presets/my.kpp"))
            assertNull(SyncEngine.localTarget(src, "../evil.revp"))
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `listRemote splits categories`() {
        val root = Files.createTempDirectory("revp-list-root").toFile()
        val brushes = Files.createTempDirectory("revp-list-brush").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["autosave/x.autosave.revp"] = "x".toByteArray()
            client.files["brushes/presets/my.kpp"] = "p".toByteArray()
            client.files["brushes/tips/my.gbr"] = "t".toByteArray()
            client.files["note.txt"] = "n".toByteArray()

            val src = sources(root, brushes)
            assertEquals(
                listOf("a.revp", "autosave/x.autosave.revp"),
                SyncEngine.listRemote(client, src, SyncCategory.ARTWORKS).map { it.path },
            )
            assertEquals(
                listOf("brushes/presets/my.kpp", "brushes/tips/my.gbr"),
                SyncEngine.listRemote(client, src, SyncCategory.BRUSHES).map { it.path },
            )
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore artworks downloads missing and records manifest`() {
        val root = Files.createTempDirectory("revp-restore-art").toFile()
        val brushes = Files.createTempDirectory("revp-restore-art-b").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["autosave/x.autosave.revp"] = "xx".toByteArray()
            client.files["brushes/tips/my.gbr"] = "t".toByteArray()

            val src = sources(root, brushes)
            val out = SyncEngine.restore(client, src, SyncCategory.ARTWORKS, emptyList(), emptyMap(), suffix)
            assertEquals(2, out.downloaded)
            assertEquals("a", root.resolve("a.revp").readText())
            assertEquals("xx", root.resolve("autosave/x.autosave.revp").readText())
            assertFalse(root.resolve("brushes").exists())
            assertEquals(2, out.manifest.size)
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore brushes writes into brush dirs`() {
        val root = Files.createTempDirectory("revp-restore-brush").toFile()
        val brushes = Files.createTempDirectory("revp-restore-brush-b").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["brushes/presets/my.kpp"] = "p".toByteArray()
            client.files["brushes/tips/my.gbr"] = "t".toByteArray()

            val src = sources(root, brushes)
            val out = SyncEngine.restore(client, src, SyncCategory.BRUSHES, emptyList(), emptyMap(), suffix)
            assertEquals(2, out.downloaded)
            assertEquals("p", brushes.resolve("presets/my.kpp").readText())
            assertEquals("t", brushes.resolve("tips/my.gbr").readText())
            assertFalse(root.resolve("a.revp").exists())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore skips file already in sync`() {
        val root = Files.createTempDirectory("revp-restore-skip").toFile()
        val brushes = Files.createTempDirectory("revp-restore-skip-b").toFile()
        try {
            val local = root.resolve("a.revp")
            local.writeText("same")
            val hash = SyncEngine.sha256(local)

            val client = TreeClient()
            client.files["a.revp"] = "remote-different".toByteArray()

            val src = sources(root, brushes)
            val out =
                SyncEngine.restore(
                    client,
                    src,
                    SyncCategory.ARTWORKS,
                    SyncEngine.scan(src),
                    mapOf("a.revp" to ManifestEntry(hash, "")),
                    suffix,
                )
            assertEquals(0, out.downloaded)
            assertEquals(1, out.skipped)
            assertEquals("same", local.readText())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore keeps local file and writes a conflict copy`() {
        val root = Files.createTempDirectory("revp-restore-conflict").toFile()
        val brushes = Files.createTempDirectory("revp-restore-conflict-b").toFile()
        try {
            val local = root.resolve("a.revp")
            local.writeText("local-newer")

            val client = TreeClient()
            client.files["a.revp"] = "cloud-older".toByteArray()

            val src = sources(root, brushes)
            val out = SyncEngine.restore(client, src, SyncCategory.ARTWORKS, SyncEngine.scan(src), emptyMap(), suffix)
            assertEquals(0, out.downloaded)
            assertEquals(1, out.conflicts)
            assertEquals("local-newer", local.readText())
            assertEquals("cloud-older", root.resolve("a (cloud).revp").readText())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore rejects checksum mismatch and writes nothing`() {
        val root = Files.createTempDirectory("revp-restore-badsum").toFile()
        val brushes = Files.createTempDirectory("revp-restore-badsum-b").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "payload".toByteArray()

            val src = sources(root, brushes)
            val token =
                SyncEngine.versionToken(
                    RemoteEntry("a.revp", false, "payload".toByteArray().size.toLong(), 0L, null),
                )
            val out =
                SyncEngine.restore(
                    client,
                    src,
                    SyncCategory.ARTWORKS,
                    emptyList(),
                    mapOf("a.revp" to ManifestEntry("deadbeef", token)),
                    suffix,
                )
            assertEquals(0, out.downloaded)
            assertEquals(1, out.failed)
            assertFalse(root.resolve("a.revp").exists())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore keeps going after a failed download`() {
        val root = Files.createTempDirectory("revp-restore-fail").toFile()
        val brushes = Files.createTempDirectory("revp-restore-fail-b").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["b.revp"] = "b".toByteArray()
            client.failOn = "a.revp"

            val src = sources(root, brushes)
            val out = SyncEngine.restore(client, src, SyncCategory.ARTWORKS, emptyList(), emptyMap(), suffix)
            assertEquals(1, out.downloaded)
            assertEquals(1, out.failed)
            assertTrue(root.resolve("b.revp").exists())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    @Test
    fun `restore updates a locally unchanged file when remote changed`() {
        val root = Files.createTempDirectory("revp-restore-update").toFile()
        val brushes = Files.createTempDirectory("revp-restore-update-b").toFile()
        try {
            val local = root.resolve("a.revp")
            local.writeText("old-local")
            val oldHash = SyncEngine.sha256(local)

            val client = TreeClient()
            client.files["a.revp"] = "new-from-cloud".toByteArray()

            val src = sources(root, brushes)
            val manifest = mapOf("a.revp" to ManifestEntry(oldHash, "stale-token"))
            val out = SyncEngine.restore(client, src, SyncCategory.ARTWORKS, SyncEngine.scan(src), manifest, suffix)
            assertEquals(1, out.updated)
            assertEquals(0, out.conflicts)
            assertEquals("new-from-cloud", local.readText())
        } finally {
            root.deleteRecursively()
            brushes.deleteRecursively()
        }
    }

    private class TreeClient : SyncClient {
        val files = LinkedHashMap<String, ByteArray>()
        var failOn: String? = null

        override fun list(remotePath: String): List<RemoteEntry> {
            val prefix = if (remotePath.isEmpty()) "" else "$remotePath/"
            val dirs = LinkedHashSet<String>()
            val out = ArrayList<RemoteEntry>()
            for (path in files.keys) {
                if (!path.startsWith(prefix)) continue
                val rest = path.substring(prefix.length)
                if (rest.isEmpty()) continue
                val slash = rest.indexOf('/')
                if (slash >= 0) {
                    dirs.add(prefix + rest.substring(0, slash))
                } else {
                    out.add(RemoteEntry(path, false, files[path]!!.size.toLong(), 0L, null))
                }
            }
            for (dir in dirs) out.add(RemoteEntry(dir, true, 0L, 0L, null))
            return out
        }

        override fun stat(remotePath: String): RemoteEntry? = null

        override fun mkdir(remotePath: String) = Unit

        override fun put(
            remotePath: String,
            data: ByteArray,
        ) {
            files[remotePath] = data
        }

        override fun get(remotePath: String): ByteArray {
            if (remotePath == failOn) throw SyncException(SyncException.Kind.NETWORK, "boom")
            return files[remotePath] ?: throw SyncException(SyncException.Kind.NOT_FOUND, "missing")
        }

        override fun delete(remotePath: String) {
            files.remove(remotePath)
        }

        override fun conflictRename(
            remotePath: String,
            suffix: String,
        ): String {
            val slash = remotePath.lastIndexOf('/')
            val dir = if (slash >= 0) remotePath.substring(0, slash + 1) else ""
            val name = if (slash >= 0) remotePath.substring(slash + 1) else remotePath
            return dir + SyncEngine.conflictName(name, suffix)
        }
    }
}
