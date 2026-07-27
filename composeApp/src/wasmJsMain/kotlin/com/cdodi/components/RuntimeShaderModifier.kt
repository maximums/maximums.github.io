package com.cdodi.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.util.fastRoundToInt
import com.cdodi.uniformData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.node.currentValueOf
import com.cdodi.buses.LocalTimeBus
import kotlinx.coroutines.flow.scan
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect

@Immutable
value class RuntimeShader(val value: String)

class RuntimeShaderModifierNode(
    private var shader: RuntimeShader,
): DrawModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private lateinit var runtimeEffect: RuntimeEffect
    private val cachedPaint = Paint()
    private var time by mutableStateOf(0f)

    fun updateShader(newShader: RuntimeShader) {
        if (shader == newShader) return

        shader = newShader
        runtimeEffect = RuntimeEffect.makeForShader(shader.value)

        invalidateDraw()
    }

    override fun onAttach() {
        super.onAttach()

        val timeBus = currentValueOf(LocalTimeBus)
        val accumulatedTimeFlow = timeBus.ticks.scan(initial = 0f) { acc, tick -> (acc + tick) % 10000f }

        runtimeEffect = RuntimeEffect.makeForShader(shader.value)

        coroutineScope.launch(CoroutineName("ShaderBackground")) {
            accumulatedTimeFlow.collect { time = it }
        }
    }

    override fun ContentDrawScope.draw() {
        cachedPaint.shader = runtimeEffect.makeShader(
            uniforms = uniformData(
                size.width.fastRoundToInt(),
                size.height.fastRoundToInt(),
                time
            ),
            children = null,
            localMatrix = null,
        )

        drawContext.canvas.nativeCanvas.drawPaint(cachedPaint)
        drawContent()
    }
}

data class RuntimeShaderModifierNodeElement(
    private val shader: RuntimeShader,
): ModifierNodeElement<RuntimeShaderModifierNode>() {
    override fun create() = RuntimeShaderModifierNode(shader)

    override fun update(node: RuntimeShaderModifierNode) = node.updateShader(newShader = shader)
}

fun Modifier.backgroundShader(shader: RuntimeShader) = this then(RuntimeShaderModifierNodeElement(shader))
