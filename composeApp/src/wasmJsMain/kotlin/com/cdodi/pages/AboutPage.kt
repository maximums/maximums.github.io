package com.cdodi.pages

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.gpuBacked
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.features.about.AboutContent
import com.cdodi.shell.LocalMenuLayout

// DESIGN.md tokens.
private val Night = Color(0xFF0D1013)
private val Mist = Color(0xFFD6D2C8)
private val Parchment = Color(0xFFEDE6D6)
private val Fog = Color(0xFF8C9499)
private val Lamp = Color(0xFFD8A15A)

/**
 * The About page: the sphere (drawn by the scene behind the page, a GPU-backed widget) beside a column of text and
 * contact links. On a phone the sphere sits above the text.
 */
@Composable
fun AboutPage() {
    if (LocalMenuLayout.current.compact) {
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
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .background(Night.copy(alpha = 0.72f))
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
    Text(AboutContent.NAME, color = Parchment, fontSize = if (compact) 34.sp else 44.sp, lineHeight = if (compact) 38.sp else 48.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light)
    for (paragraph in AboutContent.paragraphs) {
        Text(paragraph, color = Mist, fontSize = 16.sp, lineHeight = 26.sp)
    }
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (link in AboutContent.links) {
            Text(
                buildAnnotatedString {
                    withLink(LinkAnnotation.Url(link.url, TextLinkStyles(SpanStyle(color = Lamp, textDecoration = TextDecoration.Underline)))) {
                        append(link.label)
                    }
                },
                fontSize = 15.sp,
            )
        }
    }
    Text("Write-ups on how this site is built are under Notes.", color = Fog, fontSize = 14.sp, fontStyle = FontStyle.Italic)
}
