package com.cdodi.data.gameoflife

/**
 * A Life-like rule in B/S notation, e.g. `B3/S23` for Conway's Game of Life.
 *
 * Bit `n` of [birth] is set when a dead cell with `n` live neighbours comes alive,
 * bit `n` of [survive] when a live cell with `n` live neighbours stays alive.
 * The GPU compute pass receives exactly these two masks as uniforms.
 */
data class LifeRule(val birth: Int, val survive: Int) {

    init {
        require(birth in 0..ALL_COUNTS && survive in 0..ALL_COUNTS) {
            "Masks only cover neighbour counts 0..8, got birth=$birth survive=$survive"
        }
    }

    fun isAliveNext(isAlive: Boolean, neighbours: Int): Boolean {
        val mask = if (isAlive) survive else birth
        return (mask and (1 shl neighbours)) != 0
    }

    override fun toString(): String = "B${digitsOf(birth)}/S${digitsOf(survive)}"

    companion object {
        private const val ALL_COUNTS = 0b1_1111_1111
        private val NOTATION = Regex("B([0-8]*)/S([0-8]*)")

        val Conway = parse("B3/S23")
        val HighLife = parse("B36/S23")
        val Seeds = parse("B2/S")

        fun parse(notation: String): LifeRule {
            val match = NOTATION.matchEntire(notation.trim().uppercase())
                ?: throw IllegalArgumentException("Not a B/S rule: '$notation'")

            return LifeRule(birth = maskOf(match.groupValues[1]), survive = maskOf(match.groupValues[2]))
        }

        private fun maskOf(digits: String): Int = digits.fold(0) { mask, digit -> mask or (1 shl digit.digitToInt()) }

        private fun digitsOf(mask: Int): String = (0..8).filter { (mask and (1 shl it)) != 0 }.joinToString("")
    }
}
