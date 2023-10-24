package net.cutereimu.maplebots.cube

import net.cutereimu.maplebots.Cube
import org.junit.Test

class CubeTest {
    @Test
    fun testCubeRates() {
        Cube.getSelection("帽子", 150).map {
            runCalculator(
                "hat", "red", Tier.Legendary.ordinal, 150,
                Tier.Legendary.ordinal, it
            )
        }
    }
}