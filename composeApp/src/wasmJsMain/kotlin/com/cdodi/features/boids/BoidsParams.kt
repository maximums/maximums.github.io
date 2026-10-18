package com.cdodi.features.boids

/**
 * How the flock behaves. Distances are in world units (device pixels of the canvas), speeds per second, forces in
 * units per second². The same numbers drive the CPU reference and the GPU kernels, through [writeTo].
 */
data class BoidsParams(
    val worldWidth: Float = 1280f,
    val worldHeight: Float = 720f,
    /** How far a boid sees its neighbours: for alignment and cohesion, and the size of a grid cell. */
    val perception: Float = 28f,
    /** Closer than this, a neighbour is too close. */
    val separationDistance: Float = 11f,
    val separationWeight: Float = 1.8f,
    val alignmentWeight: Float = 1.0f,
    val cohesionWeight: Float = 0.8f,
    val maxSpeed: Float = 150f,
    /** Birds don't hover: slower than this, a boid speeds up again. */
    val minSpeed: Float = 75f,
    val maxForce: Float = 280f,
    /** At most this many neighbours are counted, so a dense spot costs no more than a sparse one. */
    val maxNeighbours: Int = 48,
    val pointerX: Float = 0f,
    val pointerY: Float = 0f,
    val pointerRadius: Float = 140f,
    /** 1 attracts, -1 repels, 0 leaves the flock alone. */
    val pointerMode: Float = 0f,
    val pointerWeight: Float = 2.5f,
) {
    /**
     * The uniform block `Params` in `shaders/boids/step.wgsl`, all f32 (counts are small enough to be exact):
     * world, count, dt, perception, separationDistance, the three weights, maxSpeed, minSpeed, maxForce, pointer (x, y),
     * pointerRadius, pointerMode, pointerWeight, maxNeighbours, grid (cells across, down), cellSize (x, y), padding.
     */
    fun writeTo(values: FloatArray, count: Int, dt: Float, gridX: Int = 1, gridY: Int = 1) {
        values[0] = worldWidth
        values[1] = worldHeight
        values[2] = count.toFloat()
        values[3] = dt
        values[4] = perception
        values[5] = separationDistance
        values[6] = separationWeight
        values[7] = alignmentWeight
        values[8] = cohesionWeight
        values[9] = maxSpeed
        values[10] = minSpeed
        values[11] = maxForce
        values[12] = pointerX
        values[13] = pointerY
        values[14] = pointerRadius
        values[15] = pointerMode
        values[16] = pointerWeight
        values[17] = maxNeighbours.toFloat()
        values[18] = gridX.toFloat()
        values[19] = gridY.toFloat()
        values[20] = worldWidth / gridX
        values[21] = worldHeight / gridY
    }

    companion object {
        /** Floats in the uniform block, padded to a multiple of 16 bytes. */
        const val UNIFORM_FLOATS = 24
    }
}
