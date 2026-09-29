/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.LocalProject
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
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private fun sources(root: File): List<SyncSource> =
        listOf(SyncSource(SyncCategory.ARTWORKS, root, "") { SyncEngine.isSyncable(it) })

    @Test
    fun `isSyncable only accepts formal revp projects`() {
        assertTrue(SyncEngine.isSyncable("a.revp"))
        assertTrue(SyncEngine.isSyncable("画集/b.revp"))
        assertFalse(SyncEngine.isSyncable("note.txt"))
        assertFalse(SyncEngine.isSyncable("c.tmp"))
        assertFalse(SyncEngine.isSyncable(".hidden.revp"))
        assertFalse(SyncEngine.isSyncable("autosave/x.autosave.revp"))
        assertFalse(SyncEngine.isSyncable("x.autosave.revp"))
    }

    @Test
    fun `sha256 matches known digest`() {
        val f = File.createTempFile("revp", ".bin")
        try {
            f.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SyncEngine.sha256(f),
            )
        } finally {
            f.delete()
        }
    }

    @Test
    fun `scan returns only formal projects sorted`() {
        val root = Files.createTempDirectory("revp-scan").toFile()
        try {
            root.resolve("a.revp").writeText("a")
            File(root, "画集").mkdirs()
            root.resolve("画集/b.revp").writeText("b")
            File(root, "autosave").mkdirs()
            root.resolve("autosave/x.autosave.revp").writeText("x")
            root.resolve("note.txt").writeText("n")
            root.resolve(".hidden.revp").writeText("h")

            val names = SyncEngine.scan(sources(root)).map { it.relativePath }
            assertEquals(listOf("a.revp", "画集/b.revp"), names)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `planUploads skips unchanged and uploads changed`() {
        val a = LocalProject("a.revp", File("a.revp"), 10, "hash-a")
        val b = LocalProject("b.revp", File("b.revp"), 20, "hash-b")
        val manifest = mapOf("a.revp" to ManifestEntry("hash-a", "e1"), "b.revp" to ManifestEntry("old", "e1"))
        val tokens = mapOf("a.revp" to "e1", "b.revp" to "e1")
        val uploads = SyncEngine.planUploads(listOf(a, b), manifest, tokens).map { it.relativePath }
        assertEquals(listOf("b.revp"), uploads)
    }

    @Test
    fun `planUploads uploads when remote token changed`() {
        val a = LocalProject("a.revp", File("a.revp"), 10, "hash-a")
        val manifest = mapOf("a.revp" to ManifestEntry("hash-a", "e1"))
        val tokens = mapOf("a.revp" to "e2")
        assertEquals(1, SyncEngine.planRemoteChanged(listOf(a), manifest, tokens).size)
    }

    @Test
    fun `manifest roundtrip and tolerant decode`() {
        val manifest =
            mapOf(
                "a.revp" to ManifestEntry("h1", "etag-1"),
                "画集/b.revp" to ManifestEntry("h2", ""),
            )
        assertEquals(manifest, SyncEngine.decodeManifest(SyncEngine.encodeManifest(manifest)))
        assertEquals(
            mapOf("x" to ManifestEntry("y", "")),
            SyncEngine.decodeManifest("badline\nx\ty\n\n"),
        )
    }

    @Test
    fun `required remote dirs lists all ancestors`() {
        assertEquals(
            listOf("画集", "画集/子"),
            SyncEngine.requiredRemoteDirs(listOf("画集/子/a.revp", "b.revp")),
        )
    }

    @Test
    fun `backup uploads changed files and is idempotent`() {
        val root = Files.createTempDirectory("revp-backup").toFile()
        try {
            root.resolve("a.revp").writeText("alpha")
            File(root, "sub").mkdirs()
            root.resolve("sub/b.revp").writeText("beta")

            val client = FakeClient()
            val src = sources(root)
            val first =
                SyncEngine.backup(
                    client,
                    src,
                    SyncEngine.scan(src),
                    emptyMap(),
                    SyncEngine.listRemoteAll(client, src),
                )
            assertEquals(2, first.uploaded)
            assertEquals(0, first.skipped)
            assertTrue(client.dirs.contains("sub"))
            assertTrue(client.files.containsKey("sub/b.revp"))
            assertFalse(first.manifest["a.revp"]!!.token.isEmpty())

            val second =
                SyncEngine.backup(
                    client,
                    src,
                    SyncEngine.scan(src),
                    first.manifest,
                    SyncEngine.listRemoteAll(client, src),
                )
            assertEquals(0, second.uploaded)
            assertEquals(2, second.skipped)

            root.resolve("a.revp").writeText("alpha-2")
            val third =
                SyncEngine.backup(
                    client,
                    src,
                    SyncEngine.scan(src),
                    second.manifest,
                    SyncEngine.listRemoteAll(client, src),
                )
            assertEquals(1, third.uploaded)
            assertEquals(1, third.skipped)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `backup does not overwrite a file changed by another device`() {
        val root = Files.createTempDirectory("revp-multi").toFile()
        try {
            root.resolve("a.revp").writeText("local")
            val client = FakeClient()
            val src = sources(root)
            val first =
                SyncEngine.backup(client, src, SyncEngine.scan(src), emptyMap(), SyncEngine.listRemoteAll(client, src))
            assertEquals(1, first.uploaded)

            client.writeRemote("a.revp", "from-another-device")
            val second =
                SyncEngine.backup(client, src, SyncEngine.scan(src), first.manifest, SyncEngine.listRemoteAll(client, src))
            assertEquals(0, second.uploaded)
            assertEquals(1, second.remoteChanged)
            assertEquals("from-another-device", String(client.files["a.revp"]!!))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `backup keeps remote file changed by another device when local deleted`() {
        val root = Files.createTempDirectory("revp-multi-del").toFile()
        try {
            root.resolve("a.revp").writeText("local")
            val client = FakeClient()
            val src = sources(root)
            val first =
                SyncEngine.backup(client, src, SyncEngine.scan(src), emptyMap(), SyncEngine.listRemoteAll(client, src))

            root.resolve("a.revp").delete()
            client.writeRemote("a.revp", "changed-remotely")
            val second =
                SyncEngine.backup(client, src, SyncEngine.scan(src), first.manifest, SyncEngine.listRemoteAll(client, src))
            assertEquals(0, second.deleted)
            assertTrue(client.files.containsKey("a.revp"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `planDeletions skips entries whose source dir is gone`() {
        val root = Files.createTempDirectory("revp-del-gone").toFile()
        try {
            val src = sources(root)
            val manifest = mapOf("a.revp" to ManifestEntry("h1", "e1"), "gone.revp" to ManifestEntry("h2", "e1"))
            val local = listOf(LocalProject("a.revp", File(root, "a.revp"), 1, "h1"))
            root.deleteRecursively()
            assertTrue(SyncEngine.planDeletions(src, local, manifest, emptyMap()).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `planDeletions returns files missing locally`() {
        val root = Files.createTempDirectory("revp-del").toFile()
        try {
            val src = sources(root)
            val manifest = mapOf("a.revp" to ManifestEntry("h1", "e1"), "gone.revp" to ManifestEntry("h2", "e1"))
            val local = listOf(LocalProject("a.revp", File(root, "a.revp"), 1, "h1"))
            assertEquals(
                listOf("gone.revp"),
                SyncEngine.planDeletions(src, local, manifest, mapOf("gone.revp" to "e1")),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `backup deletes stale remote file and prunes manifest`() {
        val root = Files.createTempDirectory("revp-del-run").toFile()
        try {
            root.resolve("a.revp").writeText("alpha")
            val client = FakeClient()
            val src = sources(root)
            val first =
                SyncEngine.backup(client, src, SyncEngine.scan(src), emptyMap(), SyncEngine.listRemoteAll(client, src))
            assertEquals(1, first.uploaded)

            client.writeRemote("gone.revp", "x")
            val withGone = LinkedHashMap(first.manifest)
            withGone["gone.revp"] = ManifestEntry("h2", client.tokenOf("gone.revp"))

            val second =
                SyncEngine.backup(client, src, SyncEngine.scan(src), withGone, SyncEngine.listRemoteAll(client, src))
            assertEquals(0, second.uploaded)
            assertEquals(1, second.deleted)
            assertFalse(client.files.containsKey("gone.revp"))
            assertFalse(second.manifest.containsKey("gone.revp"))
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakeClient : SyncClient {
        val files = LinkedHashMap<String, ByteArray>()
        val dirs = LinkedHashSet<String>()
        private val etags = HashMap<String, String>()
        private var seq = 0

        fun tokenOf(path: String): String = etags[path] ?: ""

        fun writeRemote(
            path: String,
            content: String,
        ) {
            files[path] = content.toByteArray()
            etags[path] = "e${++seq}"
        }

        override fun list(remotePath: String): List<RemoteEntry> {
            val prefix = if (remotePath.isEmpty()) "" else "$remotePath/"
            val childDirs = LinkedHashSet<String>()
            val out = ArrayList<RemoteEntry>()
            for (path in files.keys) {
                if (!path.startsWith(prefix)) continue
                val rest = path.substring(prefix.length)
                if (rest.isEmpty()) continue
                val slash = rest.indexOf('/')
                if (slash >= 0) {
                    childDirs.add(prefix + rest.substring(0, slash))
                } else {
                    out.add(entryOf(path))
                }
            }
            for (dir in childDirs) out.add(RemoteEntry(dir, true, 0L, 0L, null))
            return out
        }

        override fun stat(remotePath: String): RemoteEntry? = if (files.containsKey(remotePath)) entryOf(remotePath) else null

        override fun mkdir(remotePath: String) {
            dirs.add(remotePath)
        }

        override fun put(
            remotePath: String,
            data: ByteArray,
        ) {
            files[remotePath] = data
            etags[remotePath] = "e${++seq}"
        }

        override fun get(remotePath: String): ByteArray =
            files[remotePath] ?: throw SyncException(SyncException.Kind.NOT_FOUND, "missing")

        override fun delete(remotePath: String) {
            files.remove(remotePath)
            etags.remove(remotePath)
        }

        override fun conflictRename(
            remotePath: String,
            suffix: String,
        ): String = remotePath + suffix

        private fun entryOf(path: String): RemoteEntry =
            RemoteEntry(path, false, files[path]!!.size.toLong(), 0L, etags[path] ?: "e0")
    }
}
