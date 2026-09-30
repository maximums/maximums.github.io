package com.cdodi.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.util.fastRoundToInt
import com.cdodi.uniformData
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.node.currentValueOf
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.compose.ambient
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Subscription
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect

@Immutable
value class RuntimeShader(val value: String)

class RuntimeShaderModifierNode(
    private var shader: RuntimeShader,
): DrawModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private lateinit var runtimeEffect: RuntimeEffect
    private val cachedPaint = Paint()
    private var time = 0f
    private var subscription: Subscription? = null

    fun updateShader(newShader: RuntimeShader) {
        if (shader == newShader) return

        shader = newShader
        runtimeEffect = RuntimeEffect.makeForShader(shader.value)

        invalidateDraw()
    }

    override fun onAttach() {
        super.onAttach()

        runtimeEffect = RuntimeEffect.makeForShader(shader.value)

        val clock = currentValueOf(LocalHeartbeat).ambient
        subscription = clock.subscribe(FramePhase.Ui) {
            time = clock.elapsedFolded(SHADER_TIME_PERIOD)
            invalidateDraw()
        }
    }

    override fun onDetach() {
        subscription?.close()
        subscription = null
        super.onDetach()
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

        drawContext.canvas.skiaCanvas.drawPaint(cachedPaint)
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
