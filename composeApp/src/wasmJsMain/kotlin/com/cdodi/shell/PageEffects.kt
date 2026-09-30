package com.cdodi.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.effects.FadeEffect
import com.cdodi.adapters.compose.effects.PageEffect
import com.cdodi.adapters.compose.effects.PixelMeltEffect
import com.cdodi.core.navigation.graph.Destination

/** The page effects the site plays. The melt joins once its SkSL has loaded; until then it simply isn't drawn. */
@Composable
fun rememberPageEffects(): List<PageEffect> {
    val melt by produceState<PageEffect?>(null) {
        value = PixelMeltEffect(Res.readBytes("files/sksl/pixel_melt.sksl").decodeToString(), ::meltDirection)
    }
    return remember(melt) { listOfNotNull(FadeEffect, melt) }
}

/** The page melts towards the left when the next one comes later in the menu. */
private fun meltDirection(from: Destination, to: Destination): Float =
    if (menuOrder.indexOf(to.route) > menuOrder.indexOf(from.route)) -1f else 1f
