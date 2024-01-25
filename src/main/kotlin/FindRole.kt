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
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.message.data.toPlainText
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
        val ret = ArrayList<Message>()

        try {
            PluginMain.getPic(data.image).use { `is` ->
                `is`.toExternalResource().use { group.uploadImage(it) }
            }.let { ret.add(it) }
        } catch (e: Exception) {
            logger.error("获取或上传图片失败", e)
        }

        ret.add(
            """
            |角色名：${data.name}
            |等级：${data.level}(${data.expPercent}%)
            |联盟：${data.legionLevel}
            |""".trimMargin()
                .let { s -> if (data.graphData != null && data.graphData.any { it.expDifference != 0L }) s else s + "近日无经验变化\n" }
                .toPlainText()
        )

        if (data.graphData != null) {
            val values = ArrayList<Pair<Long, String>>()
            for (i in data.graphData.indices) {
                val levelExp = data.graphData[i].currentExp + data.graphData[i].expToNextLevel
                if (LevelExpData.data[data.graphData[i].level] != levelExp)
                    LevelExpData.data += data.graphData[i].level to levelExp
                if (i > 0) {
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
                    values.add(expDifference to data.graphData[i].dateLabel)
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
                    buf.toByteArray().toExternalResource().use {
                        group.uploadImage(it)
                    }.let { ret.add(it) }
                } catch (e: Exception) {
                    logger.error("上传图片失败", e)
                }
            }
        }

        return ret.toMessageChain()
    }

    private val logger: MiraiLogger by lazy {
        MiraiLogger.Factory.create(this::class, this::class.java.name)
    }
}

@Serializable
class CharacterData(
    @SerialName("EXPPercent")
    val expPercent: Double,

    @SerialName("Level")
    val level: Int,

    @SerialName("LegionLevel")
    val legionLevel: Int,

    @SerialName("Name")
    val name: String,

    @SerialName("CharacterImageURL")
    val image: String,

    @SerialName("GraphData")
    val graphData: List<GraphData>? = null,
)

@Serializable
class GraphData(
    @SerialName("CurrentEXP")
    val currentExp: Long,

    @SerialName("DateLabel")
    val dateLabel: String,

    @SerialName("EXPDifference")
    val expDifference: Long,

    @SerialName("EXPToNextLevel")
    val expToNextLevel: Long,

    @SerialName("Level")
    val level: Int,
)