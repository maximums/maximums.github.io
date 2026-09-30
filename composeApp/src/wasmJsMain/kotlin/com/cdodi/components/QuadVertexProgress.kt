package com.cdodi.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.util.lerp

data class QuadVertexProgress(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
)

enum class MorphingShape(
    private val topStart: Float,
    private val topEnd: Float,
    private val bottomEnd: Float,
    private val bottomStart: Float,
) {
    Rectangle(0f, 1f, 1f, 0f),
    Rhombus(0.5f, 0.5f, 0.5f, 0.5f),
    TriangleTopStart(0f, 1f, 0f, 0f),
    TriangleTopEnd(0f, 1f, 1f, 1f),
    TriangleBottomStart(0f, 0f, 1f, 0f),
    TriangleBottomEnd(1f, 1f, 1f, 0f);

    fun toVertexesProgress() = QuadVertexProgress(topStart, topEnd, bottomEnd, bottomStart)

    val contentAlignment: Alignment
        get() = when (this) {
            Rectangle, Rhombus -> Alignment.Center
            TriangleTopStart -> Alignment.TopStart
            TriangleTopEnd -> Alignment.TopEnd
            TriangleBottomStart -> Alignment.BottomStart
            TriangleBottomEnd -> Alignment.BottomEnd
        }
}

/** The quad [fraction] of the way from [from] to [to], corner by corner. */
fun lerp(from: QuadVertexProgress, to: QuadVertexProgress, fraction: Float) = QuadVertexProgress(
    topStart = lerp(from.topStart, to.topStart, fraction),
    topEnd = lerp(from.topEnd, to.topEnd, fraction),
    bottomEnd = lerp(from.bottomEnd, to.bottomEnd, fraction),
    bottomStart = lerp(from.bottomStart, to.bottomStart, fraction),
)
