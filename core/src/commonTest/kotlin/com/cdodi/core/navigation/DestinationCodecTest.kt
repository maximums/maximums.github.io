package com.cdodi.core.navigation

import com.cdodi.core.navigation.graph.navGraph
import com.cdodi.core.navigation.transition.Transition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationCodecTest {

    private val codec = DestinationCodec(
        navGraph(start = Home) {
            destination(Home); destination(Life); destination(Poem)
            edge(from = any, to = any, transition = Transition.None)
        },
    )

    @Test
    fun aDestinationWithoutArgumentsIsItsPath() {
        assertEquals("home", codec.encode(Home))
        assertEquals(Home, codec.decode("home"))
        assertEquals(Home, codec.decode("/home/"))
    }

    @Test
    fun argumentsGoInTheQueryAndStayReadable() {
        assertEquals("life?rule=B36/S23", codec.encode(Life("B36/S23")))
        assertEquals(Life("B36/S23"), codec.decode("life?rule=B36/S23"))
        assertEquals(Life(), codec.decode("life"), "a missing argument takes its default")
    }

    @Test
    fun anyTextSurvivesTheRoundTrip() {
        val lines = listOf("rain & fog = autumn", "frunză moartă", "100% grey", "?#/=&", "🍂 leaves", "")

        for (line in lines) {
            val url = codec.encode(Poem(line))
            assertEquals(Poem(line), codec.decode(url), url)
        }
        assertEquals("poem?line=rain%20%26%20fog%20%3D%20autumn", codec.encode(Poem("rain & fog = autumn")))
    }

    @Test
    fun unknownOrMalformedUrlsDecodeToNull() {
        assertNull(codec.decode("nowhere"))
        assertNull(codec.decode("life?rule=nonsense"), "the route rejects the arguments")
        assertNull(codec.decode("poem?line=%G1"), "not a hex escape")
        assertNull(codec.decode("poem?line=%E2%28"), "not UTF-8")
        assertNull(codec.decode("poem?line=%2"), "cut short")
    }
}
