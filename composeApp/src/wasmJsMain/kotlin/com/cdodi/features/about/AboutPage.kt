package com.cdodi.features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.design.components.CornerHint
import com.cdodi.design.components.glass
import com.cdodi.design.theme.Credit
import com.cdodi.design.theme.LocalViewport
import com.cdodi.design.theme.SiteTheme
import com.cdodi.design.theme.settling

/**
 * The About page: the maple (drawn by the scene behind the page, a GPU-backed widget), turned by dragging it as
 * [orbit] says, beside a column of text and contact links. On a phone the maple stands above the text.
 */
@Composable
fun AboutPage(orbit: Orbit) {
    if (LocalViewport.current.compact) {
        Column(Modifier.fillMaxSize()) {
            Maple(orbit, Modifier.fillMaxWidth().weight(0.38f))
            Words(Modifier.fillMaxWidth().weight(0.62f), compact = true)
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Maple(orbit, Modifier.fillMaxHeight().weight(1f))
            Words(Modifier.fillMaxHeight().weight(1f), compact = false)
        }
    }
}

/** The maple, and under it how to turn it: faint until the pointer is on the tree (or a finger holds it). */
@Composable
private fun Maple(orbit: Orbit, modifier: Modifier) {
    var held by remember { mutableStateOf(false) }
    val near by settling(held)
    Box(modifier.padding(24.dp)) {
        Bonsai(Modifier.fillMaxSize().turnsBonsai(orbit) { held = it })
        CornerHint("Drag to turn", Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 0.45f + 0.55f * near })
    }
}

@Composable
private fun Words(modifier: Modifier, compact: Boolean) {
    Box(modifier.padding(if (compact) 8.dp else 24.dp), contentAlignment = if (compact) Alignment.TopCenter else Alignment.CenterStart) {
        SelectionContainer {
            val reading = SiteTheme.glass.reading
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .glass(style = { reading })
                    .verticalScroll(rememberScrollState())
                    .padding(if (compact) 20.dp else 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Content(compact)
            }
        }
    }
}

@Composable
private fun ColumnScope.Content(compact: Boolean) {
    val type = SiteTheme.type
    val colors = SiteTheme.colors
    Text(AboutContent.NAME, style = if (compact) type.title else type.display.copy(fontSize = 56.sp, lineHeight = 56.sp), color = colors.bright)
    Text(AboutContent.ROLE.uppercase(), style = type.label, color = colors.muted)
    for (paragraph in AboutContent.paragraphs) {
        Text(paragraph, style = type.body, color = colors.text)
    }
    Label("Work")
    for (role in AboutContent.work) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    append(role.title)
                    withStyle(SpanStyle(color = colors.muted)) { append(" · ${role.employer}") }
                },
                style = type.body.copy(fontSize = 15.sp),
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(role.years, style = type.readout, color = colors.muted)
        }
    }
    Label("Tools")
    Text(AboutContent.skills.joinToString("  ·  "), style = type.small.copy(lineHeight = 22.sp), color = colors.muted)
    Label("Elsewhere")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (link in AboutContent.links) {
            Text(
                buildAnnotatedString {
                    withLink(LinkAnnotation.Url(link.url, TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)))) {
                        append(link.label)
                    }
                },
                style = type.body.copy(fontSize = 15.sp),
            )
        }
    }
    Text("Write-ups on how this site is built are under Notes.", style = type.verse.copy(fontSize = 17.sp), color = colors.muted, modifier = Modifier.padding(top = 10.dp))
    // The sound outside (the theme's recording) and the maple on this page, credited.
    CreditLine("The ${SiteTheme.copy.sound} you can turn on is", SiteTheme.ambience.credit)
    CreditLine("The maple here is", AboutContent.mapleCredit, changed = "restructured for the web")
}

/**
 * "[lead] “title” by author, from source (licence)", then what was [changed] in it, if anything: CC BY asks for that,
 * and for the licence to be linked. The title links to the work.
 */
@Composable
private fun CreditLine(lead: String, credit: Credit, changed: String? = null) {
    val colors = SiteTheme.colors
    val links = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("$lead ")
            withLink(LinkAnnotation.Url(credit.url, links)) { append("“${credit.title}” by ${credit.author}") }
            append(", from ${credit.source} (")
            val licenceUrl = credit.licenceUrl
            if (licenceUrl != null) withLink(LinkAnnotation.Url(licenceUrl, links)) { append(credit.licence) } else append(credit.licence)
            append(")")
            if (changed != null) append(", $changed")
            append(".")
        },
        style = SiteTheme.type.small,
        color = colors.muted,
    )
}

/** A label over a group (DESIGN.md's Label), bright, to head a section. */
@Composable
private fun Label(text: String) {
    com.cdodi.design.components.Label(text, Modifier.padding(top = 14.dp), color = SiteTheme.colors.bright)
}
