package com.cdodi.data.gameoflife

fun interface GameRule {
    operator fun invoke(isAlive: Boolean, neighborCount: Int): Boolean
}

class ConwaySurvivalRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean  = isAlive && neighborCount in 2..3
}

class ConwayReproductionRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean = !isAlive && neighborCount == 3
}
