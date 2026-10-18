package com.cdodi.features.boids.reference

import com.cdodi.features.boids.BoidsParams

data class Boid(val position: Vec2, val velocity: Vec2)

/** Another boid as [boid] sees it: [offset] points from the boid to it, across an edge if that's shorter. */
class Neighbour(val boid: Boid, val offset: Vec2, val distance: Float)

/**
 * One of Reynolds' flocking rules: a steering force for a boid from the neighbours within its perception radius.
 * Each rule says where the boid would like to head, and [steerTowards] turns that into a force no stronger than
 * `maxForce`. The GPU kernels (`shaders/boids/step.wgsl`) do the same arithmetic in the same order.
 */
interface Rule {
    fun steer(boid: Boid, neighbours: List<Neighbour>, params: BoidsParams): Vec2
}

/** Keep away from the boids that are too close, the closest most of all. */
object Separation : Rule {
    override fun steer(boid: Boid, neighbours: List<Neighbour>, params: BoidsParams): Vec2 {
        var away = Vec2.Zero
        for (neighbour in neighbours) {
            if (neighbour.distance < params.separationDistance) away -= neighbour.offset / (neighbour.distance * neighbour.distance)
        }
        return steerTowards(away, boid, params)
    }
}

/** Fly the way the neighbours fly. */
object Alignment : Rule {
    override fun steer(boid: Boid, neighbours: List<Neighbour>, params: BoidsParams): Vec2 {
        if (neighbours.isEmpty()) return Vec2.Zero
        var heading = Vec2.Zero
        for (neighbour in neighbours) heading += neighbour.boid.velocity
        return steerTowards(heading / neighbours.size.toFloat(), boid, params)
    }
}

/** Fly towards the middle of the neighbours. */
object Cohesion : Rule {
    override fun steer(boid: Boid, neighbours: List<Neighbour>, params: BoidsParams): Vec2 {
        if (neighbours.isEmpty()) return Vec2.Zero
        var centre = Vec2.Zero
        for (neighbour in neighbours) centre += neighbour.offset
        return steerTowards(centre / neighbours.size.toFloat(), boid, params)
    }
}

/** The force that turns [boid] towards [desired] at full speed, at most `maxForce`; nothing if there's nowhere to go. */
fun steerTowards(desired: Vec2, boid: Boid, params: BoidsParams): Vec2 {
    if (desired.magnitude == 0f) return Vec2.Zero
    return (desired.normalized * params.maxSpeed - boid.velocity).limited(params.maxForce)
}
