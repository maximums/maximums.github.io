package com.cdodi.components

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
    Rhombus(0.5f, 0.5f, 0.5f, 0.5f);

    fun toVertexesProgress() = QuadVertexProgress(topStart, topEnd, bottomEnd, bottomStart)
}

/** The quad [fraction] of the way from [from] to [to], corner by corner. */
fun lerp(from: QuadVertexProgress, to: QuadVertexProgress, fraction: Float) = QuadVertexProgress(
    topStart = lerp(from.topStart, to.topStart, fraction),
    topEnd = lerp(from.topEnd, to.topEnd, fraction),
    bottomEnd = lerp(from.bottomEnd, to.bottomEnd, fraction),
    bottomStart = lerp(from.bottomStart, to.bottomStart, fraction),
)
