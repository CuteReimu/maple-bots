package net.cutereimu.maplebots

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object FindRoleData : AutoSavePluginData("FindRoleData") {
    var data: Map<Long, String> by value()
}