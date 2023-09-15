package net.cutereimu.maplebots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.MiraiLogger
import org.jfree.chart.ChartFactory
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.statistics.HistogramDataset
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
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

    suspend fun doStuff(group: Group, itemLevel: Int, thirtyOff: Boolean, fiveTenFifteen: Boolean): Message {
        if (itemLevel < 5 || itemLevel > 300) throw Exception("装备等级不合理")
        val maxStar = when {
            itemLevel < 100 -> 5
            itemLevel < 110 -> 8
            itemLevel < 120 -> 10
            itemLevel < 130 -> 15
            itemLevel < 140 -> 20
            else -> 25
        }
        val exp = if (maxStar <= 10) "M" else "B"
        val cur = if (maxStar > 17) 17 else 0
        val des = maxStar.coerceAtMost(22)
        var mesos17 = 0.0
        var booms17 = 0
        var count17 = 0
        var mesos22 = 0.0
        var booms22 = 0
        var count22 = 0
        val cost = ArrayList<Double>()
        repeat(1000) {
            if (maxStar > 17) {
                val (mesos171, booms171, count171) = performExperiment(0, 17, itemLevel, thirtyOff, fiveTenFifteen)
                mesos17 += mesos171
                booms17 += booms171
                count17 += count171
            }
            val (mesos221, booms221, count221) = performExperiment(cur, des, itemLevel, thirtyOff, fiveTenFifteen)
            mesos22 += mesos221
            booms22 += booms221
            count22 += count221
            if (exp == "M") cost.add(mesos221 / 1000000.0)
            else cost.add(mesos221 / 1000000000.0)
        }
        var data = arrayOf(
            (mesos22 / 1000).roundToLong().format(),
            (booms22 / 1000.0).toString(),
            (count22 / 1000.0).roundToInt().toString(),
        )
        if (maxStar > 17) {
            data = arrayOf(
                (mesos17 / 1000).roundToLong().format(),
                (booms17 / 1000.0).toString(),
                (count17 / 1000.0).roundToInt().toString(),
                *data
            )
        }
        val activity = ArrayList<String>()
        if (thirtyOff) activity.add("七折活动")
        if (fiveTenFifteen) activity.add("5/10/15必成活动")
        val activityStr = if (activity.isEmpty()) "" else "在${activity.joinToString(separator = "和")}中"
        val s = ("${activityStr}模拟升星${itemLevel}级装备\n共测试了1000次\n" +
                (if (maxStar > 17) "0-17星，平均花费了%s金币，平均爆炸了%s次，平均点了%s次\n" else "") +
                "$cur-${des}星，平均花费了%s金币，平均炸了%s次，平均点了%s次").format(*data)
        val dataset = HistogramDataset()
        val max = mesos22 / 1000 / (if (exp == "M") 1000000.0 else 1000000000.0) * 3
        dataset.addSeries("", cost.filter { it < max }.toDoubleArray(), 40, cost.min(), max)
        val chart = ChartFactory.createHistogram(
            "",
            "$cur to $des Mesos cost($exp)",
            "",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            false,
            false
        )
        val img = BufferedImage(480, 360, BufferedImage.TYPE_INT_RGB)
        chart.draw(img.createGraphics(), Rectangle(480, 360))
        val image = runCatching {
            val buf = ByteArrayOutputStream()
            withContext(Dispatchers.IO) {
                ImageIO.write(img, "png", buf)
            }
            buf.toByteArray().toExternalResource().use {
                group.uploadImage(it)
            }
        }.getOrNull()
        return if (image == null) PlainText(s) else PlainText("$s\n花费分布直方图：\n") + image
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