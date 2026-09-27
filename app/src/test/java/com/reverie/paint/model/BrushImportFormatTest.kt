/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushImportFormatTest {

    private val supportedBrushExtensions = setOf("kpp", "bundle", "abr", "gbr", "gih")

    private fun isBrushExtension(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").substringBefore('?').lowercase()
        return ext in supportedBrushExtensions
    }

    private fun inferRecommendedGroup(filename: String, defaultGroup: String = "导入"): String {
        val ext = filename.substringAfterLast('.', "").substringBefore('?').lowercase()
        return if (ext == "bundle" || ext == "zip") {
            filename.substringBeforeLast('.')
                .removeSuffix(".bundle")
                .removeSuffix(".zip")
                .trim()
                .ifBlank { defaultGroup }
        } else {
            defaultGroup
        }
    }

    @Test
    fun `isBrushExtension detects valid brush file extensions`() {
        assertTrue(isBrushExtension("pencil.kpp"))
        assertTrue(isBrushExtension("watercolor_pack.bundle"))
        assertTrue(isBrushExtension("photoshop_brushes.abr"))
        assertTrue(isBrushExtension("sample.gbr"))
        assertTrue(isBrushExtension("animated_tip.gih"))
        assertTrue(isBrushExtension("PENCIL_UPPERCASE.KPP"))

        assertFalse(isBrushExtension("image.png"))
        assertFalse(isBrushExtension("project.revp"))
        assertFalse(isBrushExtension("artwork.kra"))
        assertFalse(isBrushExtension("document.psd"))
    }

    @Test
    fun `inferRecommendedGroup extracts clean group name for bundles`() {
        assertEquals("MasterBrushes", inferRecommendedGroup("MasterBrushes.bundle"))
        assertEquals("Sketch_Set", inferRecommendedGroup("Sketch_Set.zip"))
        assertEquals("导入", inferRecommendedGroup("single_brush.kpp"))
        assertEquals("导入", inferRecommendedGroup("photoshop_special.abr"))
        assertEquals("导入", inferRecommendedGroup(" .bundle"))
    }
}
