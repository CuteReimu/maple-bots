package net.cutereimu.maplebots

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object DefaultQunDb : AutoSavePluginConfig("DefaultQunDb") {
    var data: Map<String, List<RepeaterInterruptionConfig>> by value()

    @Serializable
    class RepeaterInterruptionConfig(
        val type: String,
        val text: String,
        val url: String,
    )
}
