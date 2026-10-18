package com.cdodi.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NoteTest {

    private val source = """
        ---
        title: A note
        date: 2026-10-18
        summary: What it says: in short.
        ---
        ## Start

        Body text.
    """.trimIndent()

    @Test
    fun frontMatterAndBody() {
        val note = Note.parse("a-note", source)
        assertEquals("A note", note.title)
        assertEquals("2026-10-18", note.date)
        assertEquals("What it says: in short.", note.summary, "only the first colon separates the key")
        assertEquals(2, note.blocks.size)
        assertEquals(1, note.readingMinutes)
    }

    @Test
    fun quotedValuesLoseTheirQuotes() {
        assertEquals("1M boids: a note", Note.parse("x", source.replace("title: A note", "title: \"1M boids: a note\"")).title)
    }

    @Test
    fun missingFieldsAreNamed() {
        val error = assertFailsWith<IllegalArgumentException> { Note.parse("draft", "---\ntitle: Draft\n---\nText") }
        assertTrue("draft" in error.message!! && "date, summary" in error.message!!, error.message)
    }

    @Test
    fun theDateIsChecked() {
        assertFailsWith<IllegalArgumentException> { Note.parse("x", source.replace("2026-10-18", "18/10/2026")) }
    }

    @Test
    fun aNoteNeedsFrontMatter() {
        assertFailsWith<IllegalArgumentException> { Note.parse("x", "## Just a body") }
        assertFailsWith<IllegalArgumentException> { Note.parse("x", "---\ntitle: never closed") }
    }

    @Test
    fun readingTimeCountsCodeSlower() {
        val prose = "word ".repeat(439) // plus the heading: 440 words, two minutes
        val code = "word ".repeat(110) // counts as 220: a third minute
        assertEquals(2, Note.parse("x", source.replace("Body text.", prose)).readingMinutes)
        assertEquals(3, Note.parse("x", source.replace("Body text.", "$prose\n\n```\n$code\n```")).readingMinutes)
    }

    @Test
    fun theIndexSkipsCommentsAndBlankLines() {
        assertEquals(listOf("first", "second"), Note.index("# order of the notes\nfirst\n\n  second  # newest last\n"))
    }

    @Test
    fun datesReadAsWords() {
        assertEquals("8 October 2026", formatDate("2026-10-08"))
    }
}
