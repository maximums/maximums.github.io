package com.cdodi.data.gameoflife

fun interface GameRule {
    operator fun invoke(isAlive: Boolean, neighborCount: Int): Boolean
}

object ConwayUnderpopulationRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean = isAlive && neighborCount < 2
}

object ConwaySurvivalRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean  = isAlive && neighborCount in 2..3
}

object ConwayOverpopulationRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean =  isAlive && neighborCount > 3
}

object ConwayReproductionRule : GameRule {
    override fun invoke(isAlive: Boolean, neighborCount: Int): Boolean = !isAlive && neighborCount == 3
}
