package net.cutereimu.maplebots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.MiraiLogger
import okhttp3.Request
import org.jfree.chart.ChartFactory
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.category.DefaultCategoryDataset
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.ceil

object FindRole2 {
    private val nameRegex = Regex("""<h3 class="card-title text-nowrap">([A-Za-z0-9 ]+)</h3>""")
    private val imgRegex = Regex("""<img src="(.*?)"""")
    private val levelRegex = Regex("""<h5 class="card-text">([A-Za-z0-9.% ()]+)</h5>""")
    private val classRegex = Regex("""<p class="card-text mb-0">([A-Za-z0-9 ]*?) in""")
    private val legionRegex = Regex("""Legion Level <span class="char-stat-right">([0-9,]+)</span>""")
    private val dataRegex = Regex(""""data":\s*\{""")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }

    suspend fun doStuff(group: Group, name: String): Message {
        val request = Request.Builder().url("https://mapleranks.com/u/$name")
            .header("user-agent", PluginMain.ua)
            .header("connection", "close")
            .get().build()
        val resp = PluginMain.client.newCall(request).execute()
        if (resp.code == 404) {
            resp.close()
            return PlainText("${name}已身死道消")
        }
        if (resp.code != 200) {
            resp.close()
            throw Exception("请求错误，错误码：${resp.code}，返回内容：${resp.message}")
        }
        var rawName = ""
        var `class` = ""
        var levelExp = ""
        var legionLevel = "0"
        var imgUrl: String? = null
        var chartData: JsonElement? = null
        resp.body!!.byteStream().use { bs ->
            bs.bufferedReader(Charsets.UTF_8).use {
                var line: String
                while (true) {
                    line = it.readLine() ?: break
                    var index = 0
                    nameRegex.find(line, index)?.let { nameMatch ->
                        rawName = nameMatch.groups[1]!!.value.trim()
                        index = nameMatch.range.last + 1
                    }
                    imgRegex.find(line, index)?.let { imgMatch ->
                        imgUrl = imgMatch.groups[1]!!.value.trim()
                    }
                    levelRegex.find(line, index)?.let { levelMatch ->
                        levelExp = levelMatch.groups[1]!!.value.substringAfter("Lv.").trim()
                    }
                    classRegex.find(line, index)?.let { classMatch ->
                        `class` = classMatch.groups[1]!!.value.trim()
                    }
                    legionRegex.find(line)?.let { legionMatch ->
                        legionLevel = legionMatch.groups[1]!!.value.trim()
                    }
                    index = 0
                    while (true) {
                        val dataMatch = dataRegex.find(line, index) ?: break
                        index = dataMatch.range.last + 1
                        val rawData = "{" + line.substring(index).substringBefore("},") + "}"
                        chartData = json.parseToJsonElement(rawData)
                        if (chartData!!.jsonObject["datasets"]!!.jsonArray.first().jsonObject["label"]!!.jsonPrimitive.content != "Exp") {
                            chartData = null
                        } else {
                            break
                        }
                    }
                }
            }
        }

        var playerImage: Image? = null
        imgUrl?.let { image ->
            try {
                playerImage = PluginMain.getPic(image).use { `is` ->
                    `is`.toExternalResource().use { group.uploadImage(it) }
                }
            } catch (e: Exception) {
                logger.error("获取或上传图片失败", e)
            }
        }

        var s = """
            |角色名：$rawName
            |职业：$`class`
            |等级：$levelExp
            |联盟：$legionLevel
            |""".trimMargin()

        var expImage: Image? = null
        var values: List<Pair<Long, String>> = emptyList() // expDifference, dateLabel
        chartData?.let { data ->
            val datasets = data.jsonObject["datasets"]!!.jsonArray.first().jsonObject["data"]?.jsonArray ?: return@let
            values = data.jsonObject["labels"]!!.jsonArray.withIndex().mapNotNull { (i, v) ->
                val label = v.jsonPrimitive.content
                if (label.isEmpty()) null
                else (datasets[i].jsonPrimitive.longOrNull ?: 0) to label
            }
            values = values.asReversed().take(14)
            if (values.any { it.first != 0L }) {
                val dataset = DefaultCategoryDataset()
                values.asReversed().take(14).forEach { dataset.addValue(it.first / 1000000000.0, "", it.second) }
                val chart = ChartFactory.createBarChart(
                    "",
                    "",
                    "Exp(B)",
                    dataset,
                    PlotOrientation.HORIZONTAL,
                    false,
                    false,
                    false
                )
                val img = BufferedImage(480, 360, BufferedImage.TYPE_INT_RGB)
                chart.draw(img.createGraphics(), Rectangle(480, 360))
                try {
                    val buf = ByteArrayOutputStream()
                    withContext(Dispatchers.IO) {
                        ImageIO.write(img, "png", buf)
                    }
                    expImage = buf.toByteArray().toExternalResource().use {
                        group.uploadImage(it)
                    }
                } catch (e: Exception) {
                    logger.error("上传图片失败", e)
                }
            }
        }

        if (expImage == null) {
            s += "近日无经验变化"
        } else if (values.isNotEmpty()) {
            var sumExp = values.sumOf { it.first }
            if (sumExp > 0) {
                var aveExp = sumExp.toDouble() / values.size
                values.filter { it.first.toDouble() in (aveExp / 20)..(aveExp * 20) }.run {
                    if (isNotEmpty()) {
                        sumExp = sumOf { it.first }
                        if (sumExp > 0) aveExp = sumExp.toDouble() / size
                    }
                }
                runCatching {
                    LevelExpData.data[levelExp.substringBefore("(").trim().toInt()]?.let {
                        val expPercent = levelExp.substringAfter("(").substringBefore("%").toDouble()
                        val days = ceil((it - it / 100.0 * expPercent) / aveExp).toInt()
                        s += "预计还有${days}天升级\n"
                    }
                }
            }
        }

        return listOfNotNull(playerImage, PlainText(s), expImage).toMessageChain()
    }

    private val logger: MiraiLogger by lazy {
        MiraiLogger.Factory.create(this::class, this::class.java.name)
    }
}
