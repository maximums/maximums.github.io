package com.cdodi.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesSiteTest {

    private val note = Note(
        slug = "webidl",
        title = "Types <from> \"IDL\"",
        date = "2026-10-18",
        summary = "A summary & more.",
        blocks = Markdown.parse("## Part one\n\nSee `GPUDevice` and [the spec](https://www.w3.org/TR/webgpu/)."),
    )
    private val pages = NotesSite("https://example.github.io", "Some Author").pages(listOf(note))

    @Test
    fun aPagePerNotePlusTheIndexSitemapAndRobots() {
        assertEquals(setOf("notes/index.html", "notes/webidl.html", "sitemap.xml", "robots.txt"), pages.keys)
    }

    @Test
    fun aNotePageCarriesItsMetadata() {
        val page = pages.getValue("notes/webidl.html")
        assertTrue("<title>Types &lt;from&gt; &quot;IDL&quot; · Some Author</title>" in page)
        assertTrue("""<meta name="description" content="A summary &amp; more.">""" in page)
        assertTrue("""<link rel="canonical" href="https://example.github.io/notes/webidl.html">""" in page)
        assertTrue("""<meta property="og:type" content="article">""" in page)
        assertTrue("""<meta property="og:image" content="https://example.github.io/og.png">""" in page)
        assertTrue("""<h2 id="part-one">Part one</h2>""" in page)
        assertTrue("""<code>GPUDevice</code>""" in page)
        assertTrue("""<a href="/#/notes?note=webidl">""" in page, "links to the same note on the live site")
    }

    @Test
    fun structuredDataCannotCloseItsScript() {
        val page = pages.getValue("notes/webidl.html")
        val data = page.substringAfter("<script type=\"application/ld+json\">").substringBefore("</script>")
        assertTrue("\"headline\":\"Types \\u003cfrom> \\\"IDL\\\"\"" in data, data)
        assertTrue("\"author\":{\"@type\":\"Person\",\"name\":\"Some Author\"" in data, data)
    }

    @Test
    fun theSitemapListsEveryPage() {
        val sitemap = pages.getValue("sitemap.xml")
        listOf("https://example.github.io/", "https://example.github.io/notes/", "https://example.github.io/notes/webidl.html").forEach {
            assertTrue("<loc>$it</loc>" in sitemap, it)
        }
        assertTrue("Sitemap: https://example.github.io/sitemap.xml" in pages.getValue("robots.txt"))
    }
}
