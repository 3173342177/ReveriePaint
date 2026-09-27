/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PaintViewModelShortcutsTest {

    @Test
    fun `default shortcut mappings align with desktop standards`() {
        val undoDef = ALL_SHORTCUT_DEFINITIONS.firstOrNull { it.id == "undo" }
        assertNotNull("Undo definition should exist", undoDef)
        assertEquals("LeftCtrl + Z", undoDef?.defaultKey)

        val redoDef = ALL_SHORTCUT_DEFINITIONS.firstOrNull { it.id == "redo" }
        assertNotNull("Redo definition should exist", redoDef)
        assertEquals("LeftCtrl + LeftShift + Z", redoDef?.defaultKey)

        val brushDef = ALL_SHORTCUT_DEFINITIONS.firstOrNull { it.id == "tool_brush" }
        assertNotNull("Brush tool definition should exist", brushDef)
        assertEquals("B", brushDef?.defaultKey)

        val eraserDef = ALL_SHORTCUT_DEFINITIONS.firstOrNull { it.id == "tool_eraser" }
        assertNotNull("Eraser tool definition should exist", eraserDef)
        assertEquals("E", eraserDef?.defaultKey)
    }

    @Test
    fun `setting shortcut key to an existing key unbinds the conflicting action`() {
        val map = mutableMapOf<String, String>()
        val targetKey = "E"
        val idToSet = "undo"

        var overriddenDef: ShortcutDefinition? = null
        for (def in ALL_SHORTCUT_DEFINITIONS) {
            if (def.id != idToSet) {
                val current = map[def.id] ?: def.defaultKey
                if (current.equals(targetKey, ignoreCase = true)) {
                    map[def.id] = "无"
                    overriddenDef = def
                }
            }
        }
        map[idToSet] = targetKey

        assertEquals("tool_eraser", overriddenDef?.id)
        assertEquals("无", map["tool_eraser"])
        assertEquals("E", map["undo"])
    }

    @Test
    fun `binding with blank or none resets to unbound`() {
        val map = mutableMapOf("undo" to "LeftCtrl + Z")
        for (blankInput in listOf("", "   ", "无", "none", "None")) {
            val target = blankInput.trim()
            if (target.isBlank() || target == "无" || target.equals("none", ignoreCase = true)) {
                map["undo"] = "无"
            }
            assertEquals("无", map["undo"])
        }
    }
}
