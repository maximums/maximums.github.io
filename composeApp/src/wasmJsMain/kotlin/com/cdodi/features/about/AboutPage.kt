package com.cdodi.features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.gpuBacked
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.design.components.glass
import com.cdodi.design.theme.LocalViewport
import com.cdodi.design.theme.SiteTheme

/**
 * The About page: the sphere (drawn by the scene behind the page, a GPU-backed widget) beside a column of text and
 * contact links. On a phone the sphere sits above the text.
 */
@Composable
fun AboutPage() {
    if (LocalViewport.current.compact) {
        Column(Modifier.fillMaxSize()) {
            Sphere(Modifier.fillMaxWidth().weight(0.38f))
            Words(Modifier.fillMaxWidth().weight(0.62f), compact = true)
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Sphere(Modifier.fillMaxHeight().weight(1f))
            Words(Modifier.fillMaxHeight().weight(1f), compact = false)
        }
    }
}

@Composable
private fun Sphere(modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).gpuBacked(GpuWidgets.Kind.Sphere))
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
    // The sound outside, credited: the theme's recording.
    val credit = SiteTheme.ambience.credit
    Text(
        buildAnnotatedString {
            append("The ${SiteTheme.copy.sound} you can turn on is ")
            withLink(LinkAnnotation.Url(credit.url, TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)))) {
                append("“${credit.title}” by ${credit.author}")
            }
            append(", from ${credit.source} (${credit.licence}).")
        },
        style = type.small,
        color = colors.muted,
    )
}

/** A label over a group (DESIGN.md's Label), bright, to head a section. */
@Composable
private fun Label(text: String) {
    com.cdodi.design.components.Label(text, Modifier.padding(top = 14.dp), color = SiteTheme.colors.bright)
}
