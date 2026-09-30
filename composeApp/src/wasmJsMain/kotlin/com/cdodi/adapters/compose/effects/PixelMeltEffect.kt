package com.cdodi.adapters.compose.effects

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.graph.Destination
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * The page being left blows away as dust (an SkSL shader over the page's own pixels, which only Skia can reach).
 *
 * @param direction which way the dust blows for a transition: -1 to the left, 1 to the right.
 */
class PixelMeltEffect(sksl: String, private val direction: (from: Destination, to: Destination) -> Float) : PageEffect {

    override val id = UiEffectIds.PIXEL_MELT

    private val effect = RuntimeEffect.makeForShader(sksl)

    override fun GraphicsLayerScope.apply(state: EffectState, role: PageRole, transition: NavState.Transitioning) {
        if (role != PageRole.Leaving) return
        val travelled = state.travelled?.takeIf { it > 0f } ?: return
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("resolution", size.width, size.height)
            uniform("progress", travelled)
            uniform("direction", direction(transition.from, transition.to), 0.2f)
        }
        renderEffect = ImageFilter.makeRuntimeShader(runtimeShaderBuilder = builder, shaderName = "composable", input = null)
            .asComposeRenderEffect()
    }
}
