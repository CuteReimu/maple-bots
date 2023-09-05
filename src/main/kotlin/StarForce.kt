package net.cutereimu.maplebots

import net.mamoe.mirai.utils.MiraiLogger
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt
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
     * @return [Pair](totalMesos, totalBooms, totalCount)
     */
    private fun performExperiment(
        currentStars: Int,
        desiredStar: Int,
        itemLevel: Int,
        thirtyOff: Boolean,
        fiveTenFifteen: Boolean
    ): Triple<Long, Int, Int> {
        var currentStar = currentStars
        var totalMesos = 0L
        var totalBooms = 0
        var totalCount = 0
        var decreaseCount = 0
        while (currentStar < desiredStar) {
            totalMesos += attemptCost(currentStar, itemLevel, thirtyOff)
            totalCount++
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
        return Triple(totalMesos, totalBooms, totalCount)
    }

    private fun Long.format(): String = when {
        this < 1000000 -> toString()
        this < 100000000 -> "%.2fM".format(this / 1000000.0)
        else -> "%.2fB".format(this / 1000000000.0)
    }

    private fun calculate(itemLevel: Int, thirtyOff: Boolean, fiveTenFifteen: Boolean): StarForceDb.CacheData {
        if (itemLevel < 5 || itemLevel > 300) throw Exception("装备等级不合理")
        val now = System.currentTimeMillis()
        val key = itemLevel or (if (thirtyOff) 0x1000 else 0) or (if (fiveTenFifteen) 0x2000 else 0)
        val cacheData = StarForceDb.data[key] ?: StarForceDb.CacheData(0, listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 0)
        return if (now < cacheData.expire) {
            cacheData
        } else {
            var mesos17 = cacheData.data[0]
            var booms17 = cacheData.data[1]
            var count17 = cacheData.data[2]
            var mesos22 = cacheData.data[3]
            var booms22 = cacheData.data[4]
            var count22 = cacheData.data[5]
            repeat(100) {
                val (mesos171, booms171, count171) = performExperiment(0, 17, itemLevel, thirtyOff, fiveTenFifteen)
                val (mesos221, booms221, count221) = performExperiment(17, 22, itemLevel, thirtyOff, fiveTenFifteen)
                mesos17 += mesos171
                booms17 += booms171
                count17 += count171
                mesos22 += mesos221
                booms22 += booms221
                count22 += count221
            }
            val d = StarForceDb.CacheData(
                now + 10000,
                listOf(mesos17, booms17, count17, mesos22, booms22, count22),
                cacheData.count + 100
            )
            StarForceDb.data += key to d
            d
        }
    }

    fun doStuff(itemLevel: Int, thirtyOff: Boolean, fiveTenFifteen: Boolean): String {
        val cacheData = calculate(itemLevel, thirtyOff, fiveTenFifteen)
        val data = cacheData.average()
        val count = cacheData.count
        val param = arrayOf(
            data[0].roundToLong().format(),
            data[1].roundToInt().toString(),
            data[2].roundToInt().toString(),
            data[3].roundToLong().format(),
            data[4].roundToInt().toString(),
            data[5].roundToInt().toString(),
        )
        return ("共测试了${count}次\n0-17星，平均花费了%s金币，平均爆炸了%s次，平均点了%s次\n" +
                "17-22星，平均花费了%s金币，平均炸了%s次，平均点了%s次").format(*param)
    }

    fun autoDoStuff() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val r = Random.nextInt(16)
                val itemLevel = (r and 3).let { if (it == 3) 200 else 140 + 10 * it }
                calculate(itemLevel, r and 4 != 0, r and 8 != 0)
            }
        }, 60000, 60000)
    }

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