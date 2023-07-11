package net.cutereimu.maplebots

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

object Config : AutoSavePluginConfig("Config") {
    @ValueDescription("生效的QQ群")
    @ValueName("qq_groups")
    var qqGroups: List<Long> by value(listOf(12345678L))

    @ValueDescription("管理员QQ号")
    val admin: Long by value(12345678L)

    @ValueDescription("图片超时时间（单位：小时）")
    @ValueName("image_expire_hours")
    val imageExpireHours: Long by value(72L)
}