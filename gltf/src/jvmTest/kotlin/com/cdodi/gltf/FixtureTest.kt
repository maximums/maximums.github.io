package com.cdodi.gltf

import com.cdodi.math.Mat4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reader against real files: from other exporters (`resources/fixtures`, credited in its README), and the models
 * the site ships (`composeApp`'s `files/models`, on this test's classpath).
 */
class FixtureTest {

    private fun resource(path: String): ByteArray =
        requireNotNull(FixtureTest::class.java.getResourceAsStream(path)) { "No resource $path" }.use { it.readBytes() }

    private fun fixture(name: String): ByteArray = resource("/fixtures/$name")

    @Test
    fun theMapleTheSiteShips() {
        val model = Gltf.read(resource("/maple-bonsai.glb"))

        // tools/models/prepare-maple.sh: the trunk and the pot once, the leaf stored once and drawn 958 times.
        val (trunk, pot, leaf) = model.meshes
        assertEquals(listOf(716, 200, 236), listOf(trunk, pot, leaf).map { it.primitives.single().indexCount / 3 }, "triangles each")
        assertEquals(listOf(1, 1, 958), model.meshes.map { it.instances.size }, "drawn how many times")
        assertEquals(227_004, model.meshes.sumOf { mesh -> mesh.primitives.sumOf { it.indexCount } * mesh.instances.size } / 3, "as many triangles as the download drew")

        val primitive = leaf.primitives.single()
        assertTrue(primitive.normals != null && primitive.tangents != null && primitive.texCoords != null, "lit and normal-mapped")
        val material = model.materials.single()
        assertEquals(Model.AlphaMode.Opaque, material.alphaMode, "real leaf shapes: nothing see-through")
        assertTrue(material.doubleSided)
        assertTrue(material.baseColorTexture != null && material.normalTexture != null && material.metallicRoughnessTexture != null)
        assertEquals(listOf("image/png", "image/png", "image/png"), model.images.map { it.mimeType })

        // The download's bounds, as the reader saw them before the preparation: every leaf is where it was.
        val bounds = model.bounds!!
        assertClose(-0.010303685f, bounds.min.x)
        assertClose(0.0008210861f, bounds.min.y)
        assertClose(-0.0071108025f, bounds.min.z)
        assertClose(0.016768588f, bounds.max.x)
        assertClose(0.03071015f, bounds.max.y)
        assertClose(0.009606925f, bounds.max.z)
    }

    private fun assertClose(expected: Float, actual: Float) = assertEquals(expected, actual, 1e-7f)

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
