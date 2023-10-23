package net.cutereimu.maplebots.cube

import org.junit.Test

class CubeTest {
    @Test
    fun testCubeRates() {
        runCalculator(
            "bottom", "red", Tier.legendary.ordinal, 150,
            Tier.legendary.ordinal, "percStat+30"
        )
    }
}