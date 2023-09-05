package net.cutereimu.maplebots

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object StarForceDb : AutoSavePluginData("StarForceDb") {
    var data: Map<Int, CacheData> by value()

    /**
     * @param data [listOf](mesos17, booms17, count17, mesos22, booms22, count22)
     */
    @Serializable
    class CacheData(
        val expire: Long,
        val data: List<Double>,
        val count: Long,
    ) {
        fun average() = data.map { it / count }
    }
}