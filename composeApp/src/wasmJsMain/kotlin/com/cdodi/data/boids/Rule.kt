package com.cdodi.data.boids

interface Rule

object Separation : Rule {
    operator fun invoke(neighbors: Set<Boid>) = Unit
}
