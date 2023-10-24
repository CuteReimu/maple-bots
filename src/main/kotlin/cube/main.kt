package net.cutereimu.maplebots.cube

import kotlinx.serialization.json.Json
import net.mamoe.mirai.utils.MiraiLogger

internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    allowStructuredMapKeys = true
}

internal val cubeRates = Cube.cubeRates
internal val logger = Cube.logger

private object Cube {
    val cubeRates = javaClass.getResourceAsStream("/cubeRates.js")!!.use { `is` ->
        json.parseToJsonElement(String(`is`.readAllBytes()).replaceFirst("const cubeRates = ", ""))
    }

    val logger: MiraiLogger by lazy {
        MiraiLogger.Factory.create(this::class, this::class.java.name)
    }
}

fun runCalculator(
    itemType: String,
    cubeType: String,
    currentTier: Int,
    itemLevel: Int,
    desiredTier: Int,
    desiredStat: String
) {
    val anyStats = desiredStat == "any"
    val probabilityInputObject = translateInputToObject(desiredStat)
    val p = if (anyStats) 1.0 else getProbability(desiredTier, probabilityInputObject, itemType, cubeType, itemLevel)
    val tierUp = getTierCosts(currentTier, desiredTier, cubeType)
    val stats = if (anyStats) Result(0, 0, 0, 0, 0) else getDistrQuantile(p)

    val mean = stats.mean + tierUp.mean
    val median = stats.median + tierUp.median
    val seventyFifth = stats.seventyFifth + tierUp.seventyFifth
    val eightyFifth = stats.eightyFifth + tierUp.eightyFifth
    val ninetyFifth = stats.ninetyFifth + tierUp.ninetyFifth

    val meanCost = cubingCost(cubeType, itemLevel, mean)
    val medianCost = cubingCost(cubeType, itemLevel, median)
    val seventyFifthCost = cubingCost(cubeType, itemLevel, seventyFifth)
    val eightyFifthCost = cubingCost(cubeType, itemLevel, eightyFifth)
    val ninetyFifthCost = cubingCost(cubeType, itemLevel, ninetyFifth)

    logger.info("averageCubeCount: $mean")
    logger.info("averageCost: $meanCost")
    logger.info("medianCost: $medianCost")
    logger.info("medianCubeCount: $median")

    logger.info("costSevenFive: $seventyFifthCost")
    logger.info("costEightFive: $eightyFifthCost")
    logger.info("costNineFive: $ninetyFifthCost")
    logger.info("cubeSevenFive: $seventyFifth")
    logger.info("cubeEightFive: $eightyFifth")
    logger.info("cubeNineFive: $ninetyFifth")
}
