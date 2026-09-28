/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import com.reverie.paint.core.KritaBundleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class BundleExportStructureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `exportBundle generates compliant Krita bundle structure`() {
        val outDir = tempFolder.newFolder("output")
        val presetDir = tempFolder.newFolder("presets")
        val brushDir = tempFolder.newFolder("brushes")

        val presetFile = File(presetDir, "MyBrush.kpp").apply {
            writeBytes("DUMMY_KPP_CONTENT".toByteArray())
        }
        val tipFile = File(brushDir, "pattern_tip.png").apply {
            writeBytes("DUMMY_PNG_TIP_CONTENT".toByteArray())
        }

        val bundleFile = KritaBundleManager.exportBundle(
            bundleName = "Test & Special",
            groupName = "Sketch <Art>",
            presets = listOf("MyBrush" to presetFile),
            tipAssets = listOf(tipFile),
            customOutDir = outDir,
        )

        assertTrue(bundleFile.exists())
        assertTrue(bundleFile.length() > 0)

        ZipFile(bundleFile).use { zip ->
            val entries = zip.entries().toList()
            assertTrue(entries.isNotEmpty())

            // 1. First entry must be uncompressed 'mimetype'
            val firstEntry = entries.first()
            assertEquals("mimetype", firstEntry.name)
            assertEquals(ZipEntry.STORED, firstEntry.method)
            val mimetypeContent = zip.getInputStream(firstEntry).reader().readText()
            assertEquals("application/x-krita-resourcebundle", mimetypeContent)

            // 2. Preset entry
            val kppEntry = zip.getEntry("paintoppresets/MyBrush.kpp")
            assertNotNull(kppEntry)

            // 3. Brush tip entry
            val tipEntry = zip.getEntry("brushes/pattern_tip.png")
            assertNotNull(tipEntry)

            // 4. Preview image at root
            val previewEntry = zip.getEntry("preview.png")
            assertNotNull(previewEntry)

            // 5. meta.xml
            val metaEntry = zip.getEntry("meta.xml")
            assertNotNull(metaEntry)
            val metaXml = zip.getInputStream(metaEntry).reader().readText()
            assertTrue(metaXml.contains("<meta:meta"))
            assertTrue(metaXml.contains("application/x-krita-resourcebundle") || metaXml.contains("<meta:generator>ReveriePaint</meta:generator>"))
            assertTrue(metaXml.contains("<dc:title>Test &amp; Special</dc:title>"))
            assertTrue(metaXml.contains("""meta:value="Sketch &lt;Art&gt;""""))

            // 6. META-INF/manifest.xml
            val manifestEntry = zip.getEntry("META-INF/manifest.xml")
            assertNotNull(manifestEntry)
            val manifestXml = zip.getInputStream(manifestEntry).reader().readText()
            assertTrue(manifestXml.contains("""<manifest:file-entry manifest:media-type="application/x-krita-resourcebundle" manifest:full-path="/"/>"""))
            assertTrue(manifestXml.contains("""manifest:media-type="paintoppresets""""))
            assertTrue(manifestXml.contains("""manifest:full-path="paintoppresets/MyBrush.kpp""""))
            assertTrue(manifestXml.contains("""manifest:md5sum="""))
            assertTrue(manifestXml.contains("""<manifest:tag>Sketch &lt;Art&gt;</manifest:tag>"""))
            assertTrue(manifestXml.contains("""manifest:media-type="brushes""""))
            assertTrue(manifestXml.contains("""manifest:full-path="brushes/pattern_tip.png""""))
        }
    }
}
