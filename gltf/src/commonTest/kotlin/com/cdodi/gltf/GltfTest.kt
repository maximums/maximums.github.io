package com.cdodi.gltf

import com.cdodi.math.Bounds
import com.cdodi.math.Mat4
import com.cdodi.math.Quat
import com.cdodi.math.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GltfTest {

    /**
     * A `.glb` with one triangle (mesh 0, and an unused copy as mesh 1), the given [nodes] and [scenes], and whatever
     * else [more] adds to the top level. Accessor 0 is the positions, 1 the indices; [extraAccessors] come after.
     */
    private fun triangle(
        nodes: String,
        scenes: String = """[{"nodes":[0]}]""",
        more: String = "",
        extraAccessors: String = "",
        extraViews: String = "",
        glb: TestGlb = TestGlb(),
        primitive: String = """"attributes":{"POSITION":0},"indices":1""",
    ): ByteArray {
        val positions = glb.floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val indices = glb.int16s(0, 1, 2)
        val json = """{
            "asset":{"version":"2.0"},
            "scenes":$scenes, "nodes":$nodes,
            "meshes":[{"name":"triangle","primitives":[{$primitive}]},{"name":"unused","primitives":[{$primitive}]}],
            "buffers":[{"byteLength":${glb.binLength}}],
            "bufferViews":[{"buffer":0,"byteOffset":$positions,"byteLength":36},{"buffer":0,"byteOffset":$indices,"byteLength":6}$extraViews],
            "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}$extraAccessors]
            $more
        }"""
        return glb.build(json)
    }

    @Test
    fun aMeshIsDrawnWhereItsNodeIs() {
        val model = Gltf.read(triangle("""[{"mesh":0,"translation":[1,2,3],"scale":[2,2,2]}]"""))
        val mesh = model.meshes.single()
        assertEquals("triangle", mesh.name, "the unused mesh isn't read")
        assertEquals(Mat4.trs(Vec3(1f, 2f, 3f), Quat.IDENTITY, Vec3(2f, 2f, 2f)), mesh.instances.single())
        val primitive = mesh.primitives.single()
        assertContentEquals(intArrayOf(0, 1, 2), primitive.indices)
        assertEquals(3, primitive.vertexCount)
        assertNull(primitive.normals)
        assertNull(primitive.material, "the default material")
        assertEquals(Bounds(Vec3(1f, 2f, 3f), Vec3(3f, 4f, 3f)), model.bounds, "the declared range, where the node puts it")
    }

    @Test
    fun aChildIsPlacedInsideItsParent() {
        val nodes = """[{"translation":[10,0,0],"children":[1]},{"mesh":0,"rotation":[0,0.7071068,0,0.7071068]}]"""
        val world = Gltf.read(triangle(nodes)).meshes.single().instances.single()
        // Turned a quarter about Y, then moved 10 along X: +X ends up at (10, 0, −1).
        val corner = world.transformPoint(Vec3.X)
        assertTrue((corner - Vec3(10f, 0f, -1f)).length < 1e-5f, "$corner")
    }

    @Test
    fun aMatrixNodeIsTakenAsItIs() {
        val m = Mat4.trs(Vec3(1f, 0f, 0f), Quat.axisAngle(Vec3.Z, (PI / 3).toFloat()), Vec3(1f, 3f, 1f))
        val nodes = """[{"mesh":0,"matrix":[${m.values.joinToString()}]}]"""
        assertTrue(Gltf.read(triangle(nodes)).meshes.single().instances.single().isCloseTo(m))
    }

    @Test
    fun nodesSharingAMeshAreItsInstances() {
        val nodes = """[{"mesh":0},{"mesh":0,"translation":[0,0,5]},{"mesh":0,"translation":[0,0,-5]}]"""
        val model = Gltf.read(triangle(nodes, scenes = """[{"nodes":[0,1,2]}]"""))
        val mesh = model.meshes.single()
        assertEquals(listOf(0f, 5f, -5f), mesh.instances.map { it[2, 3] })
        assertEquals(Bounds(Vec3(0f, 0f, -5f), Vec3(1f, 1f, 5f)), model.bounds)
    }

    @Test
    fun gpuInstancesAreAppliedInsideTheirNode() {
        val glb = TestGlb()
        val translations = glb.floats(1f, 0f, 0f, 0f, 2f, 0f)
        val scales = glb.floats(1f, 1f, 1f, 3f, 3f, 3f)
        val node = """[{"mesh":0,"translation":[0,0,7],"extensions":{"EXT_mesh_gpu_instancing":{"attributes":{"TRANSLATION":2,"SCALE":3}}}}]"""
        val model = Gltf.read(
            triangle(
                node,
                glb = glb,
                more = ""","extensionsUsed":["EXT_mesh_gpu_instancing"],"extensionsRequired":["EXT_mesh_gpu_instancing"]""",
                extraViews = """,{"buffer":0,"byteOffset":$translations,"byteLength":24},{"buffer":0,"byteOffset":$scales,"byteLength":24}""",
                extraAccessors = """,{"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"},{"bufferView":3,"componentType":5126,"count":2,"type":"VEC3"}""",
            )
        )
        val instances = model.meshes.single().instances
        assertEquals(2, instances.size)
        assertEquals(Mat4.translation(Vec3(0f, 0f, 7f)) * Mat4.translation(Vec3.X), instances[0])
        assertEquals(Mat4.translation(Vec3(0f, 0f, 7f)) * Mat4.trs(Vec3(0f, 2f, 0f), Quat.IDENTITY, Vec3(3f, 3f, 3f)), instances[1])
    }

    @Test
    fun instanceAttributesMustAgreeOnTheCount() {
        val glb = TestGlb()
        val translations = glb.floats(1f, 0f, 0f, 0f, 2f, 0f)
        val scales = glb.floats(1f, 1f, 1f)
        val node = """[{"mesh":0,"extensions":{"EXT_mesh_gpu_instancing":{"attributes":{"TRANSLATION":2,"SCALE":3}}}}]"""
        assertRefused("different counts") {
            Gltf.read(
                triangle(
                    node,
                    glb = glb,
                    extraViews = """,{"buffer":0,"byteOffset":$translations,"byteLength":24},{"buffer":0,"byteOffset":$scales,"byteLength":12}""",
                    extraAccessors = """,{"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"},{"bufferView":3,"componentType":5126,"count":1,"type":"VEC3"}""",
                )
            )
        }
    }

    @Test
    fun materialsTexturesAndImagesAreResolved() {
        val glb = TestGlb()
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2)
        val image = glb.bytes(png)
        val more = """,
            "materials":[{"name":"leaf","pbrMetallicRoughness":{"baseColorFactor":[1,0,0,1],"baseColorTexture":{"index":0},"roughnessFactor":0.5},
                          "normalTexture":{"index":0,"scale":0.8},"alphaMode":"MASK","alphaCutoff":0.3,"doubleSided":true},{}],
            "textures":[{"source":0,"sampler":0}],
            "samplers":[{"magFilter":9728,"minFilter":9985,"wrapS":33071}],
            "images":[{"bufferView":2,"mimeType":"image/png"}]"""
        val model = Gltf.read(
            triangle(
                """[{"mesh":0}]""",
                glb = glb,
                more = more,
                extraViews = """,{"buffer":0,"byteOffset":$image,"byteLength":${png.size}}""",
                primitive = """"attributes":{"POSITION":0},"indices":1,"material":0""",
            )
        )
        assertEquals(0, model.meshes.single().primitives.single().material)
        val leaf = model.materials[0]
        assertEquals("leaf", leaf.name)
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 1f), leaf.baseColorFactor)
        assertEquals(0.5f, leaf.roughnessFactor)
        assertEquals(1f, leaf.metallicFactor, "the spec's default")
        assertEquals(0.8f, leaf.normalScale)
        assertEquals(Model.AlphaMode.Mask, leaf.alphaMode)
        assertEquals(0.3f, leaf.alphaCutoff)
        assertTrue(leaf.doubleSided)
        val texture = leaf.baseColorTexture!!
        assertEquals(0, texture.image)
        assertEquals(Model.Sampler(Model.Filter.Nearest, Model.Filter.Linear, Model.Filter.Nearest, Model.Wrap.ClampToEdge, Model.Wrap.Repeat), texture.sampler)
        assertContentEquals(png, model.images.single().bytes)
        assertEquals("image/png", model.images.single().mimeType)
        val plain = model.materials[1]
        assertContentEquals(floatArrayOf(1f, 1f, 1f, 1f), plain.baseColorFactor, "defaults")
        assertEquals(Model.AlphaMode.Opaque, plain.alphaMode)
    }

    @Test
    fun aFileRequiringWhatIsntReadIsRefused() {
        assertRefused("doesn't support: KHR_draco_mesh_compression") {
            Gltf.read(triangle("""[{"mesh":0}]""", more = ""","extensionsRequired":["KHR_draco_mesh_compression"]"""))
        }
        // Used but not required: read, and ignored.
        Gltf.read(triangle("""[{"mesh":0}]""", more = ""","extensionsUsed":["KHR_materials_clearcoat"]"""))
    }

    @Test
    fun brokenDocumentsAreRefusedWithAReason() {
        assertRefused("only 2.x") { Gltf.read(TestGlb().build("""{"asset":{"version":"1.0"}}""")) }
        assertRefused("isn't a glTF document") { Gltf.read(TestGlb().build("""{"nodes":[]}""")) }
        assertRefused("No scene") { Gltf.read(TestGlb().build("""{"asset":{"version":"2.0"}}""")) }
        assertRefused("reached twice") { Gltf.read(triangle("""[{"children":[1]},{"children":[0],"mesh":0}]""")) }
        assertRefused("only triangles") { Gltf.read(triangle("""[{"mesh":0}]""", primitive = """"attributes":{"POSITION":0},"mode":1""")) }
        assertRefused("has no POSITION") { Gltf.read(triangle("""[{"mesh":0}]""", primitive = """"attributes":{}""")) }
        assertRefused("translation has 2 values") { Gltf.read(triangle("""[{"mesh":0,"translation":[1,2]}]""")) }
        assertRefused("only images inside the .glb") {
            Gltf.read(triangle("""[{"mesh":0}]""", more = ""","images":[{"uri":"leaf.png"}]"""))
        }
    }

    private fun assertRefused(reason: String, block: () -> Unit) {
        val e = assertFailsWith<GltfException> { block() }
        assertTrue(reason in e.message!!, "expected \"$reason\" in \"${e.message}\"")
    }
}
