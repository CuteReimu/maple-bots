package net.cutereimu.maplebots

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

object Config : AutoSavePluginConfig("Config") {
    @ValueDescription("生效的QQ群")
    @ValueName("qq_groups")
    val qqGroups: List<Long> by value(listOf(12345678L))
}