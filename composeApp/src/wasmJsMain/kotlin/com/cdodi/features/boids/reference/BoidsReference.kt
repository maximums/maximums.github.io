package com.cdodi.features.boids.reference

import com.cdodi.features.boids.BoidsParams

/**
 * One flocking step on the CPU, the plain way: every boid looks at every other one. It's the reference the GPU
 * kernels are tested against for small flocks, so it favours being obviously right over being fast.
 */
object BoidsReference {

    private val rules: List<Pair<Rule, (BoidsParams) -> Float>> = listOf(
        Separation to { it.separationWeight },
        Alignment to { it.alignmentWeight },
        Cohesion to { it.cohesionWeight },
    )

    fun step(boids: List<Boid>, params: BoidsParams, dt: Float): List<Boid> {
        val world = Vec2(params.worldWidth, params.worldHeight)
        return boids.mapIndexed { index, boid ->
            val neighbours = buildList {
                for ((other, candidate) in boids.withIndex()) {
                    if (other == index) continue
                    val offset = (candidate.position - boid.position).wrappedDelta(world)
                    val distance = offset.magnitude
                    if (distance > 0f && distance < params.perception && size < params.maxNeighbours) add(Neighbour(candidate, offset, distance))
                }
            }

            var acceleration = Vec2.Zero
            for ((rule, weight) in rules) acceleration += rule.steer(boid, neighbours, params) * weight(params)
            acceleration += pointerForce(boid, params, world)

            var velocity = (boid.velocity + acceleration * dt).limited(params.maxSpeed)
            if (velocity.magnitude < params.minSpeed) velocity = velocity.normalized * params.minSpeed
            Boid((boid.position + velocity * dt).wrappedInto(world), velocity)
        }
    }

    /** Towards the pointer (or away from it), strongest at its centre, nothing outside its radius. */
    private fun pointerForce(boid: Boid, params: BoidsParams, world: Vec2): Vec2 {
        if (params.pointerMode == 0f) return Vec2.Zero
        val offset = (Vec2(params.pointerX, params.pointerY) - boid.position).wrappedDelta(world)
        val distance = offset.magnitude
        if (distance >= params.pointerRadius) return Vec2.Zero
        return steerTowards(offset * params.pointerMode, boid, params) * (params.pointerWeight * (1f - distance / params.pointerRadius))
    }
}
