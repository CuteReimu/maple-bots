package net.cutereimu.maplebots

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

internal object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "net.cutereimu.maplebots",
        name = "MapleStory Bot",
        version = "1.0.1"
    )
) {
    override fun onEnable() {
        Config.reload()
        DefaultQunDb.reload()
        QunDb.reload()

        val addDbQQList = ConcurrentHashMap<Long, String>()

        globalEventChannel().subscribeAlways(
            GroupMessageEvent::class,
            CoroutineExceptionHandler { _, throwable ->
                logger.error(throwable)
            },
            priority = EventPriority.MONITOR,
        ) {
            if (group.id !in Config.qqGroups)
                return@subscribeAlways
            launch {
                val content = message.contentToString()
                val lastKey = addDbQQList.remove(sender.id)
                if (lastKey != null) {
                    val message2 = message.filterNot { it is MessageSource }.toMessageChain()
                    QunDb.data += lastKey to message2.serializeToJsonString()
                    group.sendMessage("添加词条成功")
                } else if (content == "ping") {
                    group.sendMessage(At(sender) + "pong")
                } else if (content == "roll") {
                    group.sendMessage("${sender.nameCardOrNick} roll: ${Random.nextInt(0, 100)}")
                } else if (content.startsWith("添加词条 ")) {
                    val addDbKey = content.substring(5)
                    if (addDbKey in QunDb.data) {
                        group.sendMessage("词条已存在")
                    } else {
                        group.sendMessage("请输入要添加的内容")
                        addDbQQList[sender.id] = addDbKey
                    }
                } else if (content.startsWith("删除词条 ")) {
                    QunDb.data -= content.substring(5)
                    group.sendMessage("删除词条成功")
                } else if (content.startsWith("查询词条 ") || content.startsWith("搜索词条 ")) {
                    val key = content.substring(5)
                    val res = QunDb.data.keys.filter { key in it }
                    if (res.isNotEmpty())
                        group.sendMessage(res.joinToString(separator = "\n", prefix = "查询到以下词条：\n"))
                    else
                        group.sendMessage("查询不到词条($key)")
                } else {
                    val value = QunDb.data[content]
                    if (value != null) {
                        group.sendMessage(MessageChain.deserializeFromJsonString(value))
                    } else {
                        lookUpInDefaultQunDb(group, content)?.let {
                            QunDb.data += content to it.serializeToJsonString()
                            group.sendMessage(it)
                        }
                    }
                }
            }
        }
    }

    private suspend fun lookUpInDefaultQunDb(group: Group, key: String): MessageChain? =
        DefaultQunDb.data[key]?.mapNotNull { v ->
            when (v.type) {
                "plain" ->
                    v.text.toPlainText()

                "image" -> {
                    runCatching {
                        getPic(v.url).use { `is` ->
                            `is`.toExternalResource().use { group.uploadImage(it) }
                        }
                    }.getOrElse {
                        logger.error("获取或上传图片失败", it)
                        null
                    }
                }

                else -> null
            }
        }?.toMessageChain()

    private const val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36 Edg/97.0.1072.69"
    private val client = OkHttpClient().newBuilder().followRedirects(false)
        .connectTimeout(Duration.ofMillis(20000)).build()

    private fun getPic(url: String): InputStream {
        val request = Request.Builder().url(url)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("user-agent", ua)
            .get().build()
        val resp = client.newCall(request).execute()
        if (resp.code != 200)
            throw Exception("请求错误，错误码：${resp.code}，返回内容：${resp.message}")
        return resp.body!!.byteStream()
    }
}
