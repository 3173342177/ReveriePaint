/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.LocalProject
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
        val manifest = mapOf("a.revp" to "hash-a", "b.revp" to "old")
        val uploads = SyncEngine.planUploads(listOf(a, b), manifest).map { it.relativePath }
        assertEquals(listOf("b.revp"), uploads)
    }

    @Test
    fun `manifest roundtrip and tolerant decode`() {
        val manifest = mapOf("a.revp" to "h1", "画集/b.revp" to "h2")
        assertEquals(manifest, SyncEngine.decodeManifest(SyncEngine.encodeManifest(manifest)))
        assertEquals(mapOf("x" to "y"), SyncEngine.decodeManifest("badline\nx\ty\n\n"))
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
            val first = SyncEngine.backup(client, sources(root), SyncEngine.scan(sources(root)), emptyMap())
            assertEquals(2, first.uploaded)
            assertEquals(0, first.skipped)
            assertTrue(client.dirs.contains("sub"))
            assertTrue(client.files.containsKey("sub/b.revp"))

            val second = SyncEngine.backup(client, sources(root), SyncEngine.scan(sources(root)), first.manifest)
            assertEquals(0, second.uploaded)
            assertEquals(2, second.skipped)

            root.resolve("a.revp").writeText("alpha-2")
            val third = SyncEngine.backup(client, sources(root), SyncEngine.scan(sources(root)), second.manifest)
            assertEquals(1, third.uploaded)
            assertEquals(1, third.skipped)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `planDeletions skips entries whose source dir is gone`() {
        val root = Files.createTempDirectory("revp-del-gone").toFile()
        try {
            val src = sources(root)
            val manifest = mapOf("a.revp" to "h1", "gone.revp" to "h2")
            val local = listOf(LocalProject("a.revp", File(root, "a.revp"), 1, "h1"))
            root.deleteRecursively()
            assertTrue(SyncEngine.planDeletions(src, local, manifest).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `planDeletions returns files missing locally`() {
        val root = Files.createTempDirectory("revp-del").toFile()
        try {
            val src = sources(root)
            val manifest = mapOf("a.revp" to "h1", "gone.revp" to "h2")
            val local = listOf(LocalProject("a.revp", File(root, "a.revp"), 1, "h1"))
            assertEquals(listOf("gone.revp"), SyncEngine.planDeletions(src, local, manifest))
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
            client.files["gone.revp"] = "x".toByteArray()
            val manifest = mapOf("a.revp" to SyncEngine.sha256(root.resolve("a.revp")), "gone.revp" to "h2")

            val out = SyncEngine.backup(client, sources(root), SyncEngine.scan(sources(root)), manifest)
            assertEquals(0, out.uploaded)
            assertEquals(1, out.deleted)
            assertFalse(client.files.containsKey("gone.revp"))
            assertFalse(out.manifest.containsKey("gone.revp"))
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakeClient : SyncClient {
        val files = LinkedHashMap<String, ByteArray>()
        val dirs = LinkedHashSet<String>()

        override fun list(remotePath: String): List<RemoteEntry> = emptyList()

        override fun stat(remotePath: String): RemoteEntry? = null

        override fun mkdir(remotePath: String) {
            dirs.add(remotePath)
        }

        override fun put(
            remotePath: String,
            data: ByteArray,
        ) {
            files[remotePath] = data
        }

        override fun get(remotePath: String): ByteArray =
            files[remotePath] ?: throw SyncException(SyncException.Kind.NOT_FOUND, "missing")

        override fun delete(remotePath: String) {
            files.remove(remotePath)
        }

        override fun conflictRename(
            remotePath: String,
            suffix: String,
        ): String = remotePath + suffix
    }
}
