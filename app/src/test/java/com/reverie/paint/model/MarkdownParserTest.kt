/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parse block elements correctly`() {
        val markdown = """
            # Header 1
            ## Header 2
            ### Header 3
            
            ---
            
            > This is a quote
            
            - Bullet item 1
            - Bullet item 2
            
            1. First step
            2. Second step
            
            ```kotlin
            val a = 1
            ```
            
            Regular paragraph.
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown)
        assertEquals(11, blocks.size)

        assertEquals(MarkdownBlock.Header(1, "Header 1"), blocks[0])
        assertEquals(MarkdownBlock.Header(2, "Header 2"), blocks[1])
        assertEquals(MarkdownBlock.Header(3, "Header 3"), blocks[2])
        assertEquals(MarkdownBlock.HorizontalRule, blocks[3])
        assertEquals(MarkdownBlock.BlockQuote("This is a quote"), blocks[4])
        assertEquals(MarkdownBlock.ListItem(ordered = false, number = null, indent = 0, text = "Bullet item 1"), blocks[5])
        assertEquals(MarkdownBlock.ListItem(ordered = false, number = null, indent = 0, text = "Bullet item 2"), blocks[6])
        assertEquals(MarkdownBlock.ListItem(ordered = true, number = 1, indent = 0, text = "First step"), blocks[7])
        assertEquals(MarkdownBlock.ListItem(ordered = true, number = 2, indent = 0, text = "Second step"), blocks[8])
        assertEquals(MarkdownBlock.CodeBlock("kotlin", "val a = 1"), blocks[9])
        assertEquals(MarkdownBlock.Paragraph("Regular paragraph."), blocks[10])
    }

    @Test
    fun `parse changelog real case`() {
        val changelog = """
            ## [v1.3.5] - 2026-09-28
            
            ### 新增特性 (Features)
            - **液化工具**: 引入 `RGBA16F` 位移场与 GLES (TextureView + EGL) 覆盖层
            - **参考图**: 支持长按吸色
        """.trimIndent()

        val blocks = MarkdownParser.parse(changelog)
        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Header(2, "[v1.3.5] - 2026-09-28"), blocks[0])
        assertEquals(MarkdownBlock.Header(3, "新增特性 (Features)"), blocks[1])
        assertTrue(blocks[2] is MarkdownBlock.ListItem)
        assertTrue(blocks[3] is MarkdownBlock.ListItem)

        val inlineParts = MarkdownParser.parseInline((blocks[2] as MarkdownBlock.ListItem).text)
        assertEquals(4, inlineParts.size)
        assertEquals(InlineStyleType.BOLD, inlineParts[0].type)
        assertEquals("液化工具", inlineParts[0].text)
        assertNull(inlineParts[1].type)
        assertEquals(": 引入 ", inlineParts[1].text)
        assertEquals(InlineStyleType.CODE, inlineParts[2].type)
        assertEquals("RGBA16F", inlineParts[2].text)
        assertNull(inlineParts[3].type)
        assertEquals(" 位移场与 GLES (TextureView + EGL) 覆盖层", inlineParts[3].text)
    }

    @Test
    fun `parse inline links and mixed styles`() {
        val text = "Check [ReveriePaint](https://github.com/LanRhyme/ReveriePaint) for ***details*** and ~~old~~ code."
        val parts = MarkdownParser.parseInline(text)
        assertEquals(7, parts.size)

        assertEquals("Check ", parts[0].text)
        assertNull(parts[0].type)

        assertEquals("ReveriePaint", parts[1].text)
        assertEquals(InlineStyleType.LINK, parts[1].type)
        assertEquals("https://github.com/LanRhyme/ReveriePaint", parts[1].linkUrl)

        assertEquals(" for ", parts[2].text)
        assertNull(parts[2].type)

        assertEquals("details", parts[3].text)
        assertEquals(InlineStyleType.BOLD_ITALIC, parts[3].type)

        assertEquals(" and ", parts[4].text)
        assertNull(parts[4].type)

        assertEquals("old", parts[5].text)
        assertEquals(InlineStyleType.STRIKE, parts[5].type)

        assertEquals(" code.", parts[6].text)
        assertNull(parts[6].type)
    }
}
