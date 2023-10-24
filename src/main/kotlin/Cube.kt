package net.cutereimu.maplebots

object Cube {
    private val nameMap = mapOf(
        "首饰" to ("accessory" to 150),
        "腰带" to ("belt" to 150),
        "副手" to ("secondary" to 140),
        "上衣" to ("top" to 150),
        "下衣" to ("bottom" to 150),
        "披风" to ("cape" to 200),
        "纹章" to ("emblem" to 100),
        "手套" to ("gloves" to 200),
        "帽子" to ("hat" to 150),
        "心脏" to ("heart" to 100),
        "套服" to ("overall" to 200),
        "鞋子" to ("shoes" to 200),
        "护肩" to ("shoulder" to 200),
        "武器" to ("weapon" to 200),
    )

    private val statMap = mapOf(
        "percStat" to { v: String -> "$v%+属性" },
        "lineStat" to { v: String -> "${v}条属性" },
        "percAtt" to { v: String -> "$v%+攻" },
        "lineAtt" to { v: String -> "${v}条攻" },
        "percBoss" to { v: String -> "$v%+BD" },
        "lineBoss" to { v: String -> "${v}条BD" },
        "lineIed" to { v: String -> "${v}条无视" },
        "lineCritDamage" to { v: String -> "${v}条爆伤" },
        "lineMeso" to { v: String -> "${v}条钱" },
        "lineDrop" to { v: String -> "${v}条爆" },
        "lineMesoOrDrop" to { v: String -> "${v}条钱爆" },
        "secCooldown" to { v: String -> "${v}秒CD" },
    )

    private val defaultSelections = IntProgression.fromClosedRange(18, 36, 3).map { "percStat+$it" }
    private val defaultSelections160 = defaultSelections + "percStat+39"
    private val accessorySelections = defaultSelections + listOf(
        "lineMeso+1", "lineDrop+1", "lineMesoOrDrop+1",
        "lineMeso+2", "lineDrop+2", "lineMesoOrDrop+2",
        "lineMeso+3", "lineMeso+1&lineStat+1", "lineDrop+1&lineStat+1", "lineMesoOrDrop+1&lineStat+1",
    )
    private val hatSelections = listOf(
        defaultSelections,
        (2..6).map { "secCooldown+$it" },
        listOf("secCooldown+2&lineStat+2"),
        (2..4).map { "secCooldown+$it&lineStat+1" },
    ).flatten()
    private val gloveSelections160 = listOf(
        defaultSelections160,
        (1..3).map { "lineCritDamage+$it" },
        listOf("lineCritDamage+1&lineStat+1", "lineCritDamage+1&lineStat+2", "lineCritDamage+2&lineStat+1")
    ).flatten()
    private val wsSelections = listOf(
        IntProgression.fromClosedRange(18, 36, 3).map { "percAtt+$it" },
        IntProgression.fromClosedRange(18, 24, 3).map { "lineIed+1&percAtt+$it" },
        listOf("lineAtt+1&lineBoss+1", "lineAtt+1&lineBoss+2", "lineAtt+2&lineBoss+1"),
        IntProgression.fromClosedRange(30, 40, 5).map { "percAtt+21&percBoss+$it" },
        listOf("percAtt+24&percBoss+30"),
    ).flatten()
    private val wsSelections160 = listOf(
        IntProgression.fromClosedRange(18, 39, 3).map { "percAtt+$it" },
        IntProgression.fromClosedRange(20, 26, 3).map { "lineIed+1&percAtt+$it" },
        listOf("lineAtt+1&lineBoss+1", "lineAtt+1&lineBoss+2", "lineAtt+2&lineBoss+1"),
        IntProgression.fromClosedRange(30, 40, 5).map { "percAtt+23&percBoss+$it" },
        listOf("percAtt+26&percBoss+30"),
    ).flatten()
    private val eSelections = IntProgression.fromClosedRange(18, 36, 3).map { "percAtt+$it" } +
            IntProgression.fromClosedRange(18, 24, 3).map { "lineIed+1&percAtt+$it" }

    fun getSelection(name: String, itemLevel: Int) = when (name) {
        "纹章" -> eSelections // 纹章没160以上的，先不管
        "武器", "副手" -> if (itemLevel < 160) wsSelections else wsSelections160
        "首饰" -> accessorySelections // 首饰现在只算150的，先不管
        "帽子" -> hatSelections // 帽子现在只算150的，先不管
        "手套" -> gloveSelections160 // 手套现在只算200的，先不管
        else -> if (itemLevel < 160) defaultSelections else defaultSelections160
    }

    fun doStuff(s: String) = data[s]?.let { (level, result) ->
        "以下是以${level}级${s}计算的理论结果：\n" + result.joinToString(separator = "\n") {
            val (color, cost) =
                if (it.second[0] <= it.second[1]) "红" to it.second[0]
                else "黑" to it.second[1]
            "想要达到${it.first}的目标，使用${color}魔方，预计消耗${cost.format()}"
        }
    }

    private fun Long.format(): String = when {
        this < 1000000L -> toString()
        this < 100000000L -> "%.2fM".format(this / 1000000.0)
        this < 10000000000L -> "%.2fB".format(this / 1000000000.0)
        this < 100000000000L -> "%.1fB".format(this / 1000000000.0)
        this < 1000000000000L -> "%.0fB".format(this / 1000000000.0)
        this < 10000000000000L -> "%.2fT".format(this / 1000000000000.0)
        this < 100000000000000L -> "%.1fT".format(this / 1000000000000.0)
        else -> "%.0fT".format(this / 1000000000000.0)
    }

    private val data = mapOf(
        "首饰" to (150 to listOf(
            "24%+属性" to listOf(1369500000L, 2200100000L),
            "27%+属性" to listOf(2265900000L, 3951200000L),
            "30%+属性" to listOf(6548700000L, 9900450000L),
            "33%+属性" to listOf(85556400000L, 66519350000L),
            "36%+属性" to listOf(11535833850000L, 2080868050000L),
            "2条钱" to listOf(18986250000L, 14682300000L),
            "2条爆" to listOf(18986250000L, 14682300000L),
            "2条钱爆" to listOf(4755900000L, 3681800000L),
            "3条钱" to listOf(27361975050000L, 4932354800000L),
        )),
        "腰带" to (150 to listOf(
            "24%+属性" to listOf(1494000000L, 2177650000L),
            "27%+属性" to listOf(2614500000L, 4287950000L),
            "30%+属性" to listOf(7208550000L, 9990250000L),
            "33%+属性" to listOf(72770250000L, 54778000000L),
            "36%+属性" to listOf(6991695900000L, 1260545050000L),
        )),
        "下衣" to (150 to listOf(
            "24%+属性" to listOf(1680750000L, 2379700000L),
            "27%+属性" to listOf(3012900000L, 4894100000L),
            "30%+属性" to listOf(8241900000L, 11157650000L),
            "33%+属性" to listOf(78621750000L, 58639400000L),
            "36%+属性" to listOf(6991695900000L, 1260545050000L),
        )),
        "披风" to (200 to listOf(
            "24%+属性" to listOf(1433600000L, 2120400000L),
            "27%+属性" to listOf(1830400000L, 3146400000L),
            "30%+属性" to listOf(2688000000L, 4354800000L),
            "33%+属性" to listOf(7411200000L, 10146000000L),
            "36%+属性" to listOf(74816000000L, 55632000000L),
            "39%+属性" to listOf(7188249600000L, 1280197200000L),
        )),
        "纹章" to (100 to listOf(
            "24%+攻" to listOf(18542550000L, 20218950000L),
            "30%+攻" to listOf(38419875000L, 72814650000L),
            "33%+攻" to listOf(451514700000L, 378477600000L),
            "36%+攻" to listOf(64497915300000L, 11804144575000L),
            "18%+攻与1条无视" to listOf(9403550000L, 17333675000L),
            "21%+攻与1条无视" to listOf(17027400000L, 28192000000L),
            "24%+攻与1条无视" to listOf(436242950000L, 326190250000L),
        )),
        "手套" to (200 to listOf(
            "24%+属性" to listOf(2329600000L, 3351600000L),
            "27%+属性" to listOf(3033600000L, 5266800000L),
            "30%+属性" to listOf(4454400000L, 7296000000L),
            "33%+属性" to listOf(12390400000L, 17100000000L),
            "36%+属性" to listOf(128409600000L, 95851200000L),
            "39%+属性" to listOf(12800000000000L, 2280000000000L),
            "2条爆伤" to listOf(11545600000L, 8823600000L),
            "3条爆伤" to listOf(12800000000000L, 2280000000000L),
            "1条爆伤与2条属性" to listOf(4390400000L, 6817200000L),
            "2条爆伤与1条属性" to listOf(71577600000L, 54013200000L),
        )),
        "帽子" to (150 to listOf(
            "24%+属性" to listOf(2228550000L, 3300150000L),
            "27%+属性" to listOf(3896850000L, 6577850000L),
            "30%+属性" to listOf(10968450000L, 15737450000L),
            "33%+属性" to listOf(122221650000L, 93279750000L),
            "36%+属性" to listOf(13402063950000L, 2417662950000L),
            "3秒CD" to listOf(11790150000L, 9114700000L),
            "4秒CD" to listOf(46998750000L, 35897550000L),
            "5秒CD" to listOf(19494035700000L, 3516500650000L),
            "6秒CD" to listOf(107217569850000L, 19341528100000L),
            "2秒CD与2条属性" to listOf(7694100000L, 12369950000L),
            "3秒CD与1条属性" to listOf(68113950000L, 52488100000L),
            "4秒CD与1条属性" to listOf(272430900000L, 209997300000L),
        )),
        "心脏" to (100 to listOf(
            "27%+属性" to listOf(1430975000L, 2378700000L),
            "30%+属性" to listOf(3932175000L, 5506250000L),
            "33%+属性" to listOf(39189475000L, 29887925000L),
            "36%+属性" to listOf(3699407075000L, 677400900000L),
        )),
        "套服" to (200 to listOf(
            "24%+属性" to listOf(2624000000L, 3602400000L),
            "27%+属性" to listOf(3558400000L, 6064800000L),
            "30%+属性" to listOf(5196800000L, 8299200000L),
            "33%+属性" to listOf(14195200000L, 18924000000L),
            "36%+属性" to listOf(134553600000L, 99043200000L),
            "39%+属性" to listOf(11860134400000L, 2113309200000L),
        )),
        "上衣" to (150 to listOf(
            "24%+属性" to listOf(2714100000L, 3681800000L),
            "27%+属性" to listOf(5054700000L, 8171800000L),
            "30%+属性" to listOf(13807050000L, 18633500000L),
            "33%+属性" to listOf(130874400000L, 97522800000L),
            "36%+属性" to listOf(11535833850000L, 2080868050000L),
        )),
        "副手" to (140 to listOf(
            "24%+攻" to listOf(37324704000L, 38536632000L),
            "30%+攻" to listOf(86793568000L, 163237680000L),
            "33%+攻" to listOf(1070024416000L, 886073832000L),
            "36%+攻" to listOf(160657893232000L, 29054314152000L),
            "18%+攻与1条无视" to listOf(21264672000L, 38962080000L),
            "21%+攻与1条无视" to listOf(38675432000L, 63817200000L),
            "24%+攻与1条无视" to listOf(1035537480000L, 768851712000L),
            "1条攻与2条BD" to listOf(11028880000L, 18540576000L),
            "2条攻与1条BD" to listOf(16815944000L, 30341160000L),
            "21%+攻与30%+BD" to listOf(36060720000L, 55666512000L),
            "21%+攻与35%+BD" to listOf(177750848000L, 145458432000L),
            "21%+攻与40%+BD" to listOf(533240152000L, 436397688000L),
            "24%+攻与30%+BD" to listOf(1015908552000L, 712311912000L),
        )),
        "鞋子" to (200 to listOf(
            "24%+属性" to listOf(1817600000L, 2644800000L),
            "27%+属性" to listOf(2342400000L, 4058400000L),
            "30%+属性" to listOf(3443200000L, 5608800000L),
            "33%+属性" to listOf(9510400000L, 13041600000L),
            "36%+属性" to listOf(96486400000L, 71774400000L),
            "39%+属性" to listOf(9332236800000L, 1662006000000L),
        )),
        "护肩" to (200 to listOf(
            "24%+属性" to listOf(1433600000L, 2120400000L),
            "27%+属性" to listOf(1830400000L, 3146400000L),
            "30%+属性" to listOf(2688000000L, 4354800000L),
            "33%+属性" to listOf(7411200000L, 10146000000L),
            "36%+属性" to listOf(74816000000L, 55632000000L),
            "39%+属性" to listOf(7188249600000L, 1280197200000L),
        )),
        "武器" to (200 to listOf(
            "24%+攻" to listOf(26918400000L, 28614000000L),
            "33%+攻" to listOf(55744000000L, 103740000000L),
            "36%+攻" to listOf(709299200000L, 579507600000L),
            "39%+攻" to listOf(110231718400000L, 19643066400000L),
            "20%+攻与1条无视" to listOf(13670400000L, 24783600000L),
            "23%+攻与1条无视" to listOf(24934400000L, 40812000000L),
            "26%+攻与1条无视" to listOf(687193600000L, 505020000000L),
            "1条攻与2条BD" to listOf(7065600000L, 11787600000L),
            "2条攻与1条BD" to listOf(10803200000L, 19311600000L),
            "23%+攻与30%+BD" to listOf(23283200000L, 35704800000L),
            "23%+攻与35%+BD" to listOf(117836800000L, 95190000000L),
            "23%+攻与40%+BD" to listOf(353510400000L, 285547200000L),
            "26%+攻与30%+BD" to listOf(674572800000L, 468859200000L),
        )),
    )
}