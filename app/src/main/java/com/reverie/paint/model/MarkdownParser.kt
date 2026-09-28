/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.model

sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val ordered: Boolean, val number: Int?, val indent: Int, val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

enum class InlineStyleType {
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    CODE,
    STRIKE,
    LINK,
}

data class InlinePart(
    val type: InlineStyleType?,
    val text: String,
    val linkUrl: String? = null,
)

object MarkdownParser {

    private val headerRegex = Regex("""^(#{1,6})\s+(.*)$""")
    private val hrRegex = Regex("""^(\-{3,}|\*{3,}|_{3,})\s*$""")
    private val quoteRegex = Regex("""^>\s?(.*)$""")
    private val unorderedListRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
    private val orderedListRegex = Regex("""^(\s*)(\d+)\.\s+(.*)$""")

    private val inlineCodeRegex = Regex("""`([^`\n]+)`""")
    private val linkRegex = Regex("""\[([^\]\n]+)\]\(((?:https?://|mailto:)[^\s)]+)\)""")
    private val boldItalicRegex = Regex("""\*\*\*([^*\n]+)\*\*\*""")
    private val boldRegex = Regex("""(\*\*([^*\n]+)\*\*|__([^_\n]+)__)""")
    private val italicRegex = Regex("""(\*([^*\n]+)\*|(?<!\w)_([^_\n]+)_(?!\w))""")
    private val strikeRegex = Regex("""~~([^~\n]+)~~""")

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        var inCodeBlock = false
        var codeLang = ""
        val codeLines = mutableListOf<String>()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            if (inCodeBlock) {
                if (trimmed.startsWith("```")) {
                    blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
                    codeLines.clear()
                    inCodeBlock = false
                } else {
                    codeLines.add(rawLine)
                }
                continue
            }

            if (trimmed.startsWith("```")) {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
                codeLines.clear()
                continue
            }

            if (trimmed.isEmpty()) {
                continue
            }

            val hrMatch = hrRegex.matchEntire(trimmed)
            if (hrMatch != null) {
                blocks.add(MarkdownBlock.HorizontalRule)
                continue
            }

            val headerMatch = headerRegex.matchEntire(trimmed)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val title = headerMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Header(level, title))
                continue
            }

            val quoteMatch = quoteRegex.matchEntire(rawLine.trimStart())
            if (quoteMatch != null) {
                blocks.add(MarkdownBlock.BlockQuote(quoteMatch.groupValues[1].trim()))
                continue
            }

            val uListMatch = unorderedListRegex.matchEntire(rawLine)
            if (uListMatch != null) {
                val spaces = uListMatch.groupValues[1].length
                val content = uListMatch.groupValues[3].trim()
                blocks.add(MarkdownBlock.ListItem(ordered = false, number = null, indent = spaces / 2, text = content))
                continue
            }

            val oListMatch = orderedListRegex.matchEntire(rawLine)
            if (oListMatch != null) {
                val spaces = oListMatch.groupValues[1].length
                val number = oListMatch.groupValues[2].toIntOrNull()
                val content = oListMatch.groupValues[3].trim()
                blocks.add(MarkdownBlock.ListItem(ordered = true, number = number, indent = spaces / 2, text = content))
                continue
            }

            blocks.add(MarkdownBlock.Paragraph(trimmed))
        }

        if (inCodeBlock) {
            blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
        }

        return blocks
    }

    private data class MatchCandidate(
        val range: IntRange,
        val type: InlineStyleType,
        val text: String,
        val url: String? = null,
    )

    fun parseInline(text: String): List<InlinePart> {
        if (text.isEmpty()) return emptyList()

        val parts = mutableListOf<InlinePart>()
        var cursor = 0

        while (cursor < text.length) {
            var earliest: MatchCandidate? = null

            fun checkCandidate(
                match: MatchResult?,
                type: InlineStyleType,
                textExtractor: (MatchResult) -> String,
                urlExtractor: ((MatchResult) -> String)? = null,
            ) {
                if (match != null) {
                    val range = match.range
                    if (range.first >= cursor) {
                        if (earliest == null || range.first < earliest!!.range.first) {
                            earliest = MatchCandidate(
                                range = range,
                                type = type,
                                text = textExtractor(match),
                                url = urlExtractor?.invoke(match),
                            )
                        }
                    }
                }
            }

            checkCandidate(inlineCodeRegex.find(text, cursor), InlineStyleType.CODE, { it.groupValues[1] })
            checkCandidate(linkRegex.find(text, cursor), InlineStyleType.LINK, { it.groupValues[1] }, { it.groupValues[2] })
            checkCandidate(boldItalicRegex.find(text, cursor), InlineStyleType.BOLD_ITALIC, { it.groupValues[1] })
            checkCandidate(boldRegex.find(text, cursor), InlineStyleType.BOLD, { m ->
                if (m.groupValues[2].isNotEmpty()) m.groupValues[2] else m.groupValues[3]
            })
            checkCandidate(italicRegex.find(text, cursor), InlineStyleType.ITALIC, { m ->
                if (m.groupValues[2].isNotEmpty()) m.groupValues[2] else m.groupValues[3]
            })
            checkCandidate(strikeRegex.find(text, cursor), InlineStyleType.STRIKE, { it.groupValues[1] })

            if (earliest == null) {
                parts.add(InlinePart(null, text.substring(cursor)))
                break
            } else {
                val candidate = earliest!!
                if (candidate.range.first > cursor) {
                    parts.add(InlinePart(null, text.substring(cursor, candidate.range.first)))
                }
                parts.add(InlinePart(candidate.type, candidate.text, candidate.url))
                cursor = candidate.range.last + 1
            }
        }

        return parts
    }
}
