package net.cutereimu.maplebots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
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

object FindRole {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }

    private inline fun <reified T> JsonElement.decode(): T =
        json.decodeFromJsonElement(
            json.serializersModule.serializer(),
            this
        )

    suspend fun doStuff(group: Group, name: String): Message {
        val request = Request.Builder().url("https://api.maplestory.gg/v2/public/character/gms/$name")
            .header("Content-Type", "application/x-www-form-urlencoded")
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
        val body = resp.body!!.string()
        val data = json.parseToJsonElement(body).jsonObject["CharacterData"]!!.decode<CharacterData>()

        var playerImage: Image? = null
        data.image?.let { image ->
            try {
                playerImage = PluginMain.getPic(image).use { `is` ->
                    `is`.toExternalResource().use { group.uploadImage(it) }
                }
            } catch (e: Exception) {
                logger.error("获取或上传图片失败", e)
            }
        }

        var s = """
            |角色名：${data.name}
            |职业：${data.`class`}
            |等级：${data.level}(${data.expPercent}%)
            |联盟：${data.legionLevel}
            |""".trimMargin()

        var expImage: Image? = null
        val values = ArrayList<Pair<Long, String>>() // expDifference, dateLabel
        if (data.graphData != null) {
            for (i in data.graphData.indices) {
                val levelExp = data.graphData[i].currentExp + data.graphData[i].expToNextLevel
                if (LevelExpData.data[data.graphData[i].level] != levelExp)
                    LevelExpData.data += data.graphData[i].level to levelExp
                if (i > 0 && !data.graphData[i].dateLabel.isNullOrEmpty()) {
                    val expDifference =
                        if (data.graphData[i].level > data.graphData[i - 1].level) {
                            var exp = data.graphData[i - 1].expToNextLevel + data.graphData[i].currentExp
                            if (data.graphData[i].level - data.graphData[i - 1].level > 1) {
                                LevelExpData.data.let {
                                    for (j in data.graphData[i - 1].level + 1 until data.graphData[i].level) {
                                        exp += it.getOrDefault(j, 0)
                                    }
                                }
                            }
                            exp
                        } else {
                            data.graphData[i - 1].expDifference
                        }
                    values.add(expDifference to data.graphData[i].dateLabel!!)
                }
            }
            if (values.any { it.first != 0L }) {
                val dataset = DefaultCategoryDataset()
                values.asReversed().forEach { dataset.addValue(it.first / 1000000000.0, "", it.second) }
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
            val sumExp = values.sumOf { it.first }
            if (sumExp > 0) {
                LevelExpData.data[data.level]?.let {
                    val days = ceil((it - data.exp) / (sumExp.toDouble() / values.size)).toInt()
                    s += "预计还有${days}天升级\n"
                }
            }
        }

        return listOfNotNull(playerImage, PlainText(s), expImage).toMessageChain()
    }

    private val logger: MiraiLogger by lazy {
        MiraiLogger.Factory.create(this::class, this::class.java.name)
    }
}

@Serializable
class CharacterData(
    @SerialName("Class")
    val `class`: String = "",

    @SerialName("EXPPercent")
    val expPercent: Double = 0.0,

    @SerialName("EXP")
    val exp: Long = 0,

    @SerialName("Level")
    val level: Int = 0,

    @SerialName("LegionLevel")
    val legionLevel: Int = 0,

    @SerialName("Name")
    val name: String = "",

    @SerialName("CharacterImageURL")
    val image: String? = null,

    @SerialName("GraphData")
    val graphData: List<GraphData>? = null,
)

@Serializable
class GraphData(
    @SerialName("CurrentEXP")
    val currentExp: Long = 0,

    @SerialName("DateLabel")
    val dateLabel: String? = null,

    @SerialName("EXPDifference")
    val expDifference: Long = 0,

    @SerialName("EXPToNextLevel")
    val expToNextLevel: Long = 0,

    @SerialName("Level")
    val level: Int = 0,
)