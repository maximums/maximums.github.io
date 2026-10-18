package com.cdodi.notes

import com.cdodi.notes.Inline.Code
import com.cdodi.notes.Inline.Emphasis
import com.cdodi.notes.Inline.Link
import com.cdodi.notes.Inline.Strong
import com.cdodi.notes.Inline.Text
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {

    @Test
    fun blocksAreSplitByKind() {
        val blocks = Markdown.parse(
            """
            ## The grammar

            A paragraph that runs
            over two lines.

            ```kotlin
            val x = 1
              indented()
            ```

            - one
            - two
              carries on

            1. first
            2. second

            > a quote
            > on two lines

            ---
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Block.Heading(2, listOf(Text("The grammar"))),
                Block.Paragraph(listOf(Text("A paragraph that runs over two lines."))),
                Block.CodeBlock("kotlin", "val x = 1\n  indented()"),
                Block.ListBlock(ordered = false, listOf(listOf(Text("one")), listOf(Text("two carries on")))),
                Block.ListBlock(ordered = true, listOf(listOf(Text("first")), listOf(Text("second")))),
                Block.Quote(listOf(Text("a quote on two lines"))),
                Block.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun aListOrHeadingEndsAParagraph() {
        val blocks = Markdown.parse("Text\n- item\n# Title")
        assertEquals(
            listOf(
                Block.Paragraph(listOf(Text("Text"))),
                Block.ListBlock(ordered = false, listOf(listOf(Text("item")))),
                Block.Heading(1, listOf(Text("Title"))),
            ),
            blocks,
        )
    }

    @Test
    fun aBlankLineBetweenItemsKeepsTheList() {
        assertEquals(1, Markdown.parse("- a\n\n- b").size)
    }

    @Test
    fun codeKeepsItsContentAsIs() {
        val blocks = Markdown.parse("```\n# not a heading\n- not a list\n```")
        assertEquals(listOf(Block.CodeBlock(null, "# not a heading\n- not a list")), blocks)
    }

    @Test
    fun anUnclosedFenceRunsToTheEnd() {
        assertEquals(listOf(Block.CodeBlock("wgsl", "a\nb")), Markdown.parse("```wgsl\na\nb"))
    }

    @Test
    fun tablesHaveAHeaderAndRows() {
        val blocks = Markdown.parse(
            """
            | Quality | GPU time |
            |---|---:|
            | full | `0.86` ms |
            | a \| b | |
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Block.Table(
                    header = listOf(listOf(Text("Quality")), listOf(Text("GPU time"))),
                    rows = listOf(
                        listOf(listOf(Text("full")), listOf(Code("0.86"), Text(" ms"))),
                        listOf(listOf(Text("a | b")), emptyList()),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun inlineMarkup() {
        assertEquals(
            listOf(Text("a "), Strong(listOf(Text("bold"))), Text(" and "), Emphasis(listOf(Text("soft"))), Text(" "), Code("x * y")),
            Markdown.parseInline("a **bold** and *soft* `x * y`"),
        )
    }

    @Test
    fun strongInsideEmphasis() {
        assertEquals(
            listOf(Emphasis(listOf(Text("very "), Strong(listOf(Text("much")))))),
            Markdown.parseInline("*very **much***"),
        )
    }

    @Test
    fun linksHoldInlineMarkup() {
        assertEquals(
            listOf(Text("see "), Link("/#/life?rule=B36/S23", listOf(Text("High"), Emphasis(listOf(Text("Life"))))), Text(".")),
            Markdown.parseInline("see [High*Life*](/#/life?rule=B36/S23)."),
        )
    }

    @Test
    fun aCodeSpanInALinkMayHoldBrackets() {
        assertEquals(listOf(Link("u", listOf(Code("a[0]")))), Markdown.parseInline("[`a[0]`](u)"))
    }

    @Test
    fun longerBacktickRunsHoldBackticks() {
        assertEquals(listOf(Code("`triangle-list`")), Markdown.parseInline("`` `triangle-list` ``"))
    }

    @Test
    fun strayMarkersStayText() {
        assertEquals(listOf(Text("2 * 3 = 6, [not a link], a ` tick and **")), Markdown.parseInline("2 * 3 = 6, [not a link], a ` tick and **"))
    }

    @Test
    fun escapes() {
        assertEquals(listOf(Text("*not emphasis* and [x](y)")), Markdown.parseInline("""\*not emphasis\* and \[x\](y)"""))
    }

    @Test
    fun anchorsAreReadableIds() {
        assertEquals("1m-boids-in-the-browser", anchorOf(Block.Heading(2, Markdown.parseInline("1M boids in the *browser*!"))))
    }
}
