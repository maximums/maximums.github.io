package com.cdodi.features.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.notes.Block
import com.cdodi.notes.Inline
import com.cdodi.notes.Note
import com.cdodi.notes.formatDate
import com.cdodi.notes.plainText
import com.cdodi.ui.components.glass
import com.cdodi.ui.theme.ColorRoles
import com.cdodi.ui.theme.LocalViewport
import com.cdodi.ui.theme.SiteTheme
import kotlinx.browser.window

private val Body: TextStyle @Composable get() = SiteTheme.type.body.copy(color = SiteTheme.colors.text)
private val Meta: TextStyle @Composable get() = SiteTheme.type.readout.copy(color = SiteTheme.colors.muted)
private val Mono: FontFamily @Composable get() = SiteTheme.type.readout.fontFamily!!

/**
 * The write-ups: the list of notes, or the note named [slug], rendered from the same Markdown as the plain pages the
 * build writes. [open] goes to another note (or back to the list).
 */
@Composable
fun NotesPage(slug: String?, open: (NotesDestination) -> Unit) {
    val notes by produceState<Result<List<Note>>?>(null) { value = runCatching { NotesLibrary.all() } }
    val loaded = notes ?: return // a moment, while the page fades in
    val all = loaded.getOrElse {
        Quiet("The notes didn't load. They're also at /notes/ as plain pages.")
        return
    }
    if (slug == null) {
        NoteList(all, open)
    } else {
        val note = all.firstOrNull { it.slug == slug }
        if (note == null) Quiet("There's no note called \"$slug\".") else NoteView(note, open)
    }
}

/** A column of reading width, centred, scrolling, on its reading ground. */
@Composable
private fun Reading(scrollKey: Any?, content: @Composable () -> Unit) {
    val compact = LocalViewport.current.compact
    val scroll = rememberScrollState()
    LaunchedEffect(scrollKey) { scroll.scrollTo(0) }
    // Behind the text: the glass is fogged, but long reading over moving lights needs a steadier ground.
    val reading = SiteTheme.glass.reading
    Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .glass(style = { reading })
                .padding(horizontal = if (compact) 16.dp else 48.dp, vertical = if (compact) 20.dp else 40.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun NoteList(notes: List<Note>, open: (NotesDestination) -> Unit) {
    val compact = LocalViewport.current.compact
    Reading(scrollKey = null) {
        Text("Notes", style = SiteTheme.type.display.copy(fontSize = if (compact) 48.sp else 72.sp, lineHeight = if (compact) 48.sp else 72.sp), color = SiteTheme.colors.bright)
        Text("How the site behind this glass is built.", style = Body.copy(color = SiteTheme.colors.muted), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        for (note in notes) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open(NotesDestination(note.slug)) }
                    .padding(vertical = 18.dp)
            ) {
                Text("${formatDate(note.date)} · ${note.readingMinutes} min read", style = Meta)
                Text(note.title, style = SiteTheme.type.title.copy(fontSize = if (compact) 26.sp else 32.sp, lineHeight = if (compact) 30.sp else 36.sp), color = SiteTheme.colors.bright, modifier = Modifier.padding(vertical = 6.dp))
                Text(note.summary, style = Body)
            }
        }
    }
}

@Composable
private fun NoteView(note: Note, open: (NotesDestination) -> Unit) {
    val compact = LocalViewport.current.compact
    Reading(scrollKey = note.slug) {
        Text(
            "ALL NOTES",
            style = SiteTheme.type.label,
            color = SiteTheme.colors.muted,
            modifier = Modifier.clickable { open(NotesDestination()) }.padding(vertical = 8.dp),
        )
        SelectionContainer {
            Column {
                Text("${formatDate(note.date)} · ${note.readingMinutes} min read", style = Meta, modifier = Modifier.padding(top = 20.dp))
                Text(
                    note.title,
                    style = SiteTheme.type.display.copy(fontSize = if (compact) 38.sp else 56.sp, lineHeight = if (compact) 40.sp else 58.sp),
                    color = SiteTheme.colors.bright,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                for (block in note.blocks) NoteBlock(block)
            }
        }
        val links = InlineStyle(Mono, SiteTheme.colors).links
        Text(
            text = buildAnnotatedString {
                append("Also as a ")
                withLink(linkTo("/notes/${note.slug}.html", links)) { append("plain page") }
                append(", for sharing.")
            },
            style = Body.copy(color = SiteTheme.colors.muted, fontSize = 14.sp),
            modifier = Modifier.padding(top = 40.dp),
        )
    }
}

@Composable
private fun NoteBlock(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            text = if (block.level <= 2) annotated(block.content) else AnnotatedString(plainText(block.content).uppercase()),
            style = if (block.level <= 2) SiteTheme.type.title.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal) else SiteTheme.type.label.copy(fontSize = 12.sp),
            color = SiteTheme.colors.bright,
            modifier = Modifier.padding(top = if (block.level <= 2) 40.dp else 28.dp, bottom = 8.dp),
        )
        is Block.Paragraph -> Text(annotated(block.content), style = Body, modifier = Modifier.padding(vertical = 8.dp))
        is Block.CodeBlock -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .background(SiteTheme.colors.groundRaised)
                .border(1.dp, SiteTheme.colors.hairline)
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(block.code, style = SiteTheme.type.readout.copy(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal), color = SiteTheme.colors.text, softWrap = false)
        }
        is Block.ListBlock -> Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(if (block.ordered) "${index + 1}." else "·", style = Body.copy(color = if (block.ordered) SiteTheme.colors.muted else SiteTheme.colors.accent), modifier = Modifier.width(28.dp))
                    Text(annotated(item), style = Body)
                }
            }
        }
        is Block.Quote -> Row(Modifier.padding(vertical = 16.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(SiteTheme.colors.accent))
            Spacer(Modifier.width(20.dp))
            Text(annotated(block.content), style = Body.copy(color = SiteTheme.colors.bright, fontStyle = FontStyle.Italic))
        }
        is Block.Table -> Column(Modifier.padding(vertical = 16.dp).fillMaxWidth()) {
            TableRow(block.header, header = true)
            block.rows.forEach { TableRow(it, header = false) }
        }
        Block.Rule -> Box(Modifier.padding(vertical = 32.dp).fillMaxWidth().height(1.dp).background(SiteTheme.colors.hairline))
    }
}

@Composable
private fun TableRow(cells: List<List<Inline>>, header: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (cell in cells) {
                Text(
                    annotated(cell),
                    style = Body.copy(fontSize = 15.sp, color = if (header) SiteTheme.colors.bright else SiteTheme.colors.text, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SiteTheme.colors.hairline))
    }
}

@Composable
private fun Quiet(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = SiteTheme.colors.muted, fontSize = 16.sp, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun annotated(inlines: List<Inline>): AnnotatedString {
    val inline = InlineStyle(Mono, SiteTheme.colors)
    return buildAnnotatedString { appendInlines(inlines, inline) }
}

/** What inline Markdown is drawn with: code in the mono face, strong and code text bright, links in the accent. */
private class InlineStyle(val mono: FontFamily, val colors: ColorRoles) {
    val links = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline))
}

private fun AnnotatedString.Builder.appendInlines(inlines: List<Inline>, style: InlineStyle) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Code -> withStyle(SpanStyle(fontFamily = style.mono, fontSize = 14.sp, background = style.colors.groundRaised, color = style.colors.bright)) { append(inline.code) }
            is Inline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(inline.content, style) }
            is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = style.colors.bright)) { appendInlines(inline.content, style) }
            is Inline.Link -> withLink(linkTo(inline.href, style.links)) { appendInlines(inline.content, style) }
        }
    }
}

/**
 * A link in a note. A link into the site (`/#/boids`) navigates here, through the browser's history like any other
 * navigation; anything else opens in a new tab.
 */
private fun linkTo(href: String, styles: TextLinkStyles): LinkAnnotation {
    val route = href.removePrefix("/").takeIf { it.startsWith("#/") }
    return if (route != null) {
        LinkAnnotation.Clickable(href, styles) { window.location.hash = route.removePrefix("#") }
    } else {
        LinkAnnotation.Url(href, styles)
    }
}
