/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.reverie.paint.core

import com.reverie.paint.core.sync.RemoteEntry
import com.reverie.paint.core.sync.SyncClient
import com.reverie.paint.core.sync.SyncEngine
import com.reverie.paint.core.sync.SyncException
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineRestoreTest {
    private val suffix = " (cloud)"

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
    fun `listRemoteProjects recurses and filters`() {
        val client = TreeClient()
        client.files["a.revp"] = "a".toByteArray()
        client.files["画集/b.revp"] = "b".toByteArray()
        client.files["画集/子/c.revp"] = "c".toByteArray()
        client.files["note.txt"] = "n".toByteArray()
        client.files["autosave/x.autosave.revp"] = "x".toByteArray()
        client.files["../evil.revp"] = "e".toByteArray()

        val names = SyncEngine.listRemoteProjects(client).map { it.path }
        assertEquals(listOf("a.revp", "画集/b.revp", "画集/子/c.revp"), names)
    }

    @Test
    fun `restore into empty dir downloads and records manifest`() {
        val root = Files.createTempDirectory("revp-restore-empty").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["sub/b.revp"] = "bb".toByteArray()

            val out = SyncEngine.restore(client, root, SyncEngine.scan(root), emptyMap(), suffix)
            assertEquals(2, out.downloaded)
            assertEquals(0, out.skipped)
            assertEquals(3L, out.bytes)
            assertEquals("bb", root.resolve("sub/b.revp").readText())
            assertEquals(2, out.manifest.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore skips file already in sync`() {
        val root = Files.createTempDirectory("revp-restore-skip").toFile()
        try {
            val local = root.resolve("a.revp")
            local.writeText("same")
            val hash = SyncEngine.sha256(local)

            val client = TreeClient()
            client.files["a.revp"] = "remote-different".toByteArray()

            val out = SyncEngine.restore(client, root, SyncEngine.scan(root), mapOf("a.revp" to hash), suffix)
            assertEquals(0, out.downloaded)
            assertEquals(1, out.skipped)
            assertEquals(0, out.conflicts)
            assertEquals("same", local.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore keeps local file and writes a conflict copy`() {
        val root = Files.createTempDirectory("revp-restore-conflict").toFile()
        try {
            val local = root.resolve("a.revp")
            local.writeText("local-newer")

            val client = TreeClient()
            client.files["a.revp"] = "cloud-older".toByteArray()

            val out = SyncEngine.restore(client, root, SyncEngine.scan(root), emptyMap(), suffix)
            assertEquals(0, out.downloaded)
            assertEquals(1, out.conflicts)
            assertEquals("local-newer", local.readText())
            assertEquals("cloud-older", root.resolve("a (cloud).revp").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore rejects checksum mismatch and writes nothing`() {
        val root = Files.createTempDirectory("revp-restore-badsum").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "payload".toByteArray()

            val out = SyncEngine.restore(client, root, SyncEngine.scan(root), mapOf("a.revp" to "deadbeef"), suffix)
            assertEquals(0, out.downloaded)
            assertEquals(1, out.failed)
            assertFalse(root.resolve("a.revp").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore keeps going after a failed download`() {
        val root = Files.createTempDirectory("revp-restore-fail").toFile()
        try {
            val client = TreeClient()
            client.files["a.revp"] = "a".toByteArray()
            client.files["b.revp"] = "b".toByteArray()
            client.failOn = "a.revp"

            val out = SyncEngine.restore(client, root, SyncEngine.scan(root), emptyMap(), suffix)
            assertEquals(1, out.downloaded)
            assertEquals(1, out.failed)
            assertTrue(root.resolve("b.revp").exists())
        } finally {
            root.deleteRecursively()
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
            val dot = name.lastIndexOf('.')
            return if (dot > 0) {
                "$dir${name.substring(0, dot)}$suffix${name.substring(dot)}"
            } else {
                "$dir$name$suffix"
            }
        }
    }
}
