package net.cutereimu.maplebots

import net.mamoe.mirai.utils.MiraiLogger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 星之力计算器的工具类，相关数据取自:
 *
 * https://strategywiki.org/wiki/MapleStory/Spell_Trace_and_Star_Force#Meso_Cost
 */
object StarForce {
    private fun makeMesoFn(divisor: Int, currentStarExp: Double = 2.7) = { currentStar: Int, itemLevel: Int ->
        100 * Math.round(itemLevel * itemLevel * itemLevel * ((currentStar + 1.0).pow(currentStarExp)) / divisor + 10)
    }

    private fun preSaviorMesoFn(currentStar: Int) = when {
        currentStar >= 15 -> makeMesoFn(20000)
        currentStar >= 10 -> makeMesoFn(40000)
        else -> makeMesoFn(5000, 1.0)
    }

    private fun saviorMesoFn(currentStar: Int) = when (currentStar) {
        11 -> makeMesoFn(22000)
        12 -> makeMesoFn(15000)
        13 -> makeMesoFn(11000)
        14 -> makeMesoFn(7500)
        else -> preSaviorMesoFn(currentStar)
    }

    private fun saviorCost(currentStart: Int, itemLevel: Int) = saviorMesoFn(currentStart)(currentStart, itemLevel)

    private fun attemptCost(currentStart: Int, itemLevel: Int, thirtyOff: Boolean): Long {
        val cost = saviorCost(currentStart, itemLevel)
        if (thirtyOff) return cost * 7 / 10
        return cost
    }

    /**
     * @return either [SUCCESS], [MAINTAIN], [DECREASE], or [BOOM]
     */
    private fun determineOutcome(currentStar: Int, fiveTenFifteen: Boolean): Int {
        if (fiveTenFifteen && (currentStar == 5 || currentStar == 10 || currentStar == 15))
            return SUCCESS
        val outcome = Random.nextDouble()
        var probabilitySuccess = rates[currentStar][SUCCESS]
        var probabilityMaintain = rates[currentStar][MAINTAIN]
        var probabilityDecrease = rates[currentStar][DECREASE]
        var probabilityBoom = rates[currentStar][BOOM]
        // star catch adjustment
        probabilitySuccess *= 1.045
        val leftOver = 1 - probabilitySuccess
        if (probabilityDecrease == 0.0) {
            probabilityMaintain *= leftOver / (probabilityMaintain + probabilityBoom)
            probabilityBoom = leftOver - probabilityMaintain
        } else {
            probabilityDecrease *= leftOver / (probabilityDecrease + probabilityBoom)
            probabilityBoom = leftOver - probabilityDecrease
        }
        if (outcome < probabilitySuccess)
            return SUCCESS
        else if (outcome < probabilitySuccess + probabilityMaintain)
            return MAINTAIN
        else if (outcome < probabilitySuccess + probabilityMaintain + probabilityDecrease)
            return DECREASE
        else if (outcome < probabilitySuccess + probabilityMaintain + probabilityDecrease + probabilityBoom)
            return BOOM
        logger.error("Case not caputured")
        return SUCCESS
    }

    /**
     * @return [Pair](totalMesos, totalBooms)
     */
    private fun performExperiment(
        currentStars: Int,
        desiredStar: Int,
        itemLevel: Int,
        thirtyOff: Boolean,
        fiveTenFifteen: Boolean
    ): Pair<Long, Int> {
        var currentStar = currentStars
        var totalMesos = 0L
        var totalBooms = 0
        var decreaseCount = 0
        while (currentStar < desiredStar) {
            totalMesos += attemptCost(currentStar, itemLevel, thirtyOff)
            if (decreaseCount == 2) { // chance time
                decreaseCount = 0
                currentStar++
            } else {
                when (determineOutcome(currentStar, fiveTenFifteen)) {
                    SUCCESS -> {
                        decreaseCount = 0
                        currentStar++
                    }

                    DECREASE -> {
                        decreaseCount++
                        currentStar--
                    }

                    MAINTAIN -> {
                        decreaseCount = 0
                    }

                    BOOM -> {
                        decreaseCount = 0
                        currentStar = 12
                        totalBooms++
                    }
                }
            }
        }
        return Pair(totalMesos, totalBooms)
    }

    private fun Long.format(): String = when {
        this < 1000000 -> toString()
        this < 100000000 -> "%.2fM".format(this / 1000000.0)
        else -> "%.2fB".format(this / 1000000000.0)
    }

    fun doStuff(itemLevel: Int, thirtyOff: Boolean, fiveTenFifteen: Boolean): String {
        if (itemLevel < 5 || itemLevel > 300) throw Exception("装备等级不合理")
        val now = System.currentTimeMillis()
        val key = itemLevel or (if (thirtyOff) 0x1000 else 0) or (if (fiveTenFifteen) 0x2000 else 0)
        val cacheData = cache[key]
        val data =
            if (cacheData != null && now < cacheData.expire) {
                cacheData.data
            } else {
                var mesos17 = 0.0
                var booms17 = 0.0
                var mesos22 = 0.0
                var booms22 = 0.0
                repeat(100) {
                    val (mesos171, booms171) = performExperiment(0, 17, itemLevel, thirtyOff, fiveTenFifteen)
                    val (mesos221, booms221) = performExperiment(17, 22, itemLevel, thirtyOff, fiveTenFifteen)
                    mesos17 += mesos171
                    booms17 += booms171
                    mesos22 += mesos221
                    booms22 += booms221
                }
                val d =
                    CacheData(now + 60000, doubleArrayOf(mesos17 / 100, booms17 / 100, mesos22 / 100, booms22 / 100))
                cache[key] = d
                d.data
            }
        return "共测试了100次\n0-17星，平均花费了${data[0].roundToLong().format()}金币，平均爆炸了${data[1]}次\n" +
                "17-22星，平均花费了${data[2].roundToLong().format()}金币，平均爆炸了${data[3]}次\n"
    }

    /**
     * @param data [doubleArrayOf](mesos17, booms17, mesos22, booms22)
     */
    private class CacheData(val expire: Long, val data: DoubleArray)

    private val cache = ConcurrentHashMap<Int, CacheData>()

    private const val SUCCESS = 0
    private const val MAINTAIN = 1
    private const val DECREASE = 2
    private const val BOOM = 3
    private val rates = arrayOf(
        doubleArrayOf(0.95, 0.05, 0.0, 0.0),
        doubleArrayOf(0.9, 0.1, 0.0, 0.0),
        doubleArrayOf(0.85, 0.15, 0.0, 0.0),
        doubleArrayOf(0.85, 0.15, 0.0, 0.0),
        doubleArrayOf(0.80, 0.2, 0.0, 0.0),
        doubleArrayOf(0.75, 0.25, 0.0, 0.0),
        doubleArrayOf(0.7, 0.3, 0.0, 0.0),
        doubleArrayOf(0.65, 0.35, 0.0, 0.0),
        doubleArrayOf(0.6, 0.4, 0.0, 0.0),
        doubleArrayOf(0.55, 0.45, 0.0, 0.0),
        doubleArrayOf(0.5, 0.5, 0.0, 0.0),
        doubleArrayOf(0.45, 0.55, 0.0, 0.0),
        doubleArrayOf(0.4, 0.6, 0.0, 0.0),
        doubleArrayOf(0.35, 0.65, 0.0, 0.0),
        doubleArrayOf(0.3, 0.7, 0.0, 0.0),
        doubleArrayOf(0.3, 0.679, 0.0, 0.021),
        doubleArrayOf(0.3, 0.0, 0.679, 0.021),
        doubleArrayOf(0.3, 0.0, 0.679, 0.021),
        doubleArrayOf(0.3, 0.0, 0.672, 0.028),
        doubleArrayOf(0.3, 0.0, 0.672, 0.028),
        doubleArrayOf(0.3, 0.63, 0.0, 0.07),
        doubleArrayOf(0.3, 0.0, 0.63, 0.07), // 只算到22星
    )

    private val logger: MiraiLogger by lazy {
        MiraiLogger.Factory.create(this::class, this::class.java.name)
    }
}