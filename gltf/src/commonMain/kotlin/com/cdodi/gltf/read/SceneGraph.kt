package com.cdodi.gltf.read

import com.cdodi.gltf.GltfException
import com.cdodi.gltf.schema.Document
import com.cdodi.gltf.schema.Node
import com.cdodi.math.Mat4
import com.cdodi.math.Quat
import com.cdodi.math.Vec3

/**
 * The scene's node trees flattened (the spec's "Nodes and Hierarchy"): every node the scene reaches, with its world
 * matrix (its parents' transforms times its own), in the order the trees are walked. The scene is the document's
 * `scene`, or its first.
 */
internal fun worldMatrices(document: Document): List<Pair<Int, Mat4>> {
    val sceneIndex = document.scene ?: 0
    val scene = document.scenes.getOrNull(sceneIndex) ?: throw GltfException("No scene to draw (scene $sceneIndex of ${document.scenes.size})")
    val out = ArrayList<Pair<Int, Mat4>>()
    val seen = HashSet<Int>()
    fun visit(index: Int, parent: Mat4) {
        val node = document.nodes.getOrNull(index) ?: throw GltfException("Node $index doesn't exist")
        // The nodes form trees: a node reached twice is a cycle, or a child of two parents.
        if (!seen.add(index)) throw GltfException("Node $index is reached twice; nodes must form trees")
        val world = parent * localMatrix(node, index)
        out += index to world
        for (child in node.children) visit(child, world)
    }
    for (root in scene.nodes) visit(root, Mat4.IDENTITY)
    return out
}

/** A node's own transform: its `matrix`, or its translation × rotation × scale (each defaulting to none). */
internal fun localMatrix(node: Node, index: Int): Mat4 {
    node.matrix?.let {
        if (it.size != 16) throw GltfException("Node $index's matrix has ${it.size} values, not 16")
        return Mat4(it.toFloatArray())
    }
    val t = node.translation?.let { vec3(it, "Node $index's translation") } ?: Vec3.ZERO
    val r = node.rotation?.let { quat(it, "Node $index's rotation") } ?: Quat.IDENTITY
    val s = node.scale?.let { vec3(it, "Node $index's scale") } ?: Vec3.ONE
    return Mat4.trs(t, r, s)
}

private fun vec3(values: List<Float>, what: String): Vec3 {
    if (values.size != 3) throw GltfException("$what has ${values.size} values, not 3")
    return Vec3(values[0], values[1], values[2])
}

private fun quat(values: List<Float>, what: String): Quat {
    if (values.size != 4) throw GltfException("$what has ${values.size} values, not 4")
    return Quat(values[0], values[1], values[2], values[3]).normalized()
}
