package net.cutereimu.maplebots.cube

import kotlin.math.ln
import kotlin.math.roundToLong

internal fun getCubeCost(cubeType: String): Long = when (cubeType) {
    "red" -> 12000000
    "black" -> 22000000
    "master" -> 7500000
    else -> 0
}

internal fun getRevealCostConstant(itemLevel: Int): Double = when {
    itemLevel < 30 -> 0.0
    itemLevel <= 70 -> 0.5
    itemLevel <= 120 -> 2.5
    else -> 20.0
}

internal fun cubingCost(cubeType: String, itemLevel: Int, totalCubeCount: Long): Long {
    val cubeCost = getCubeCost(cubeType)
    val revealCostConst = getRevealCostConstant(itemLevel)
    val revealPotentialCost = revealCostConst * itemLevel * itemLevel
    return (cubeCost * totalCubeCount + totalCubeCount * revealPotentialCost).roundToLong()
}

internal fun getTierCosts(currentTier: Int, desireTier: Int, cubeType: String): Result {
    var mean = 0L
    var median = 0L
    var seventyFifth = 0L
    var eightyFifth = 0L
    var ninetyFifth = 0L
    for (i in currentTier..<desireTier) {
        val p = tier_rates[cubeType]!![i]!!
        val stats = getDistrQuantile(p)
        mean += stats.mean
        median += stats.median
        seventyFifth += stats.seventyFifth
        eightyFifth += stats.eightyFifth
        ninetyFifth += stats.ninetyFifth
    }
    return Result(mean, median, seventyFifth, eightyFifth, ninetyFifth)
}

// Nexon rates: https://maplestory.nexon.com/Guide/OtherProbability/cube/strange
// GMS community calculated rates: https://docs.google.com/spreadsheets/d/1od_hep5Y6x2ljfrh4M8zj5RwlpgYDRn5uTymx4iLPyw/pubhtml#
// Nexon rates used when they match close enough to ours.
private val tier_rates = mapOf(
    "occult" to mapOf(
        0 to 0.009901
    ),
    // Community rates are notably higher than nexon rates here. Assuming GMS is different and using those instead.
    "master" to mapOf(
        0 to 0.1184,
        1 to 0.0381
    ),
    // Community rates are notably higher than nexon rates here. Assuming GMS is different and using those instead.
    // The sample size isn't great, but anecdotes from people in twitch chats align with the community data.
    // That being said, take meister tier up rates with a grain of salt.
    "meister" to mapOf(
        0 to 0.1163,
        1 to 0.0879,
        2 to 0.0459
    ),
    // Community rates notably higher than KMS rates, using them.
    "red" to mapOf(
        0 to 0.14,
        1 to 0.06,
        2 to 0.025
    ),
    // Community rates notably higher than KMS rates, using them.
    "black" to mapOf(
        0 to 0.17,
        1 to 0.11,
        2 to 0.05
    )
)

internal class Result(
    val mean: Long,
    val median: Long,
    val seventyFifth: Long,
    val eightyFifth: Long,
    val ninetyFifth: Long
)

internal fun getDistrQuantile(p: Double): Result {
    val mean = 1 / p
    val median = ln(1 - 0.5) / ln(1 - p)
    val seventyFifth = ln(1 - 0.75) / ln(1 - p)
    val eightyFifth = ln(1 - 0.85) / ln(1 - p)
    val ninetyFifth = ln(1 - 0.95) / ln(1 - p)
    return Result(
        mean.roundToLong(),
        median.roundToLong(),
        seventyFifth.roundToLong(),
        eightyFifth.roundToLong(),
        ninetyFifth.roundToLong()
    )
}
