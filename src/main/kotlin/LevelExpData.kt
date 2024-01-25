package net.cutereimu.maplebots

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object LevelExpData : AutoSavePluginData("LevelExpData") {
    var data: Map<Int, Long> by value()
}