package com.cdodi.gltf

import com.cdodi.math.Mat4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reader against real files from other exporters (`resources/fixtures`, credited in its README). */
class FixtureTest {

    private fun fixture(name: String): ByteArray =
        requireNotNull(FixtureTest::class.java.getResourceAsStream("/fixtures/$name")) { "No fixture $name" }.use { it.readBytes() }

    @Test
    fun khronosTextureCoordinateTest() {
        val model = Gltf.read(fixture("TextureCoordinateTest.glb"))

        // Five nodes, each with its own mesh, in the order the scene lists the nodes (0, 2, 1, 4, 3), not the meshes'.
        assertEquals(listOf("BackPlaneMesh", "BottomRightMesh", "BottomLeftMesh", "TopRightMesh", "TopLeftMesh"), model.meshes.map { it.name })
        assertTrue(model.meshes.all { it.instances == listOf(Mat4.IDENTITY) }, "no transforms")
        assertEquals(10, model.meshes.sumOf { mesh -> mesh.primitives.sumOf { it.indexCount } } / 3, "two triangles each")

        // The top-left square's texture coordinates are the image's top-left 0.4 (the README's example accessor).
        val topLeft = model.meshes.single { it.name == "TopLeftMesh" }.primitives.single()
        val uvs = topLeft.texCoords!!
        assertTrue(uvs.all { it in 0f..0.4f }, uvs.joinToString())
        assertEquals(4, topLeft.vertexCount)
        assertTrue(topLeft.indices!!.all { it in 0 until 4 }, "unsigned-byte indices")
        assertNull(model.meshes.single { it.name == "BackPlaneMesh" }.primitives.single().texCoords, "the back plane is untextured")

        val material = model.materials[topLeft.material!!]
        assertEquals("TopLeftMat", material.name)
        assertContentEquals(floatArrayOf(0.8f, 0.8f, 0f, 1f), material.baseColorFactor)
        assertTrue(material.doubleSided)
        assertEquals(Model.Sampler.DEFAULT, material.baseColorTexture!!.sampler, "an empty sampler leaves the filters to the renderer")

        val image = model.images.single()
        assertEquals("image/png", image.mimeType)
        assertContentEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), image.bytes.copyOf(4))

        val bounds = model.bounds!!
        assertEquals(-1.2f, bounds.min.x, 1e-4f)
        assertEquals(1.2f, bounds.max.y, 1e-4f)
    }
}
