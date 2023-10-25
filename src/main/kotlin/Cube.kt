package net.cutereimu.maplebots

import kotlinx.serialization.json.*
import kotlin.math.ln
import kotlin.math.roundToLong

object Cube {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowStructuredMapKeys = true
    }

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
        intArrayOf(18, 21, 24, 30, 33, 36).map { "percAtt+$it" },
        IntProgression.fromClosedRange(18, 24, 3).map { "lineIed+1&percAtt+$it" },
        listOf("lineAtt+1&lineBoss+1", "lineAtt+1&lineBoss+2", "lineAtt+2&lineBoss+1"),
        IntProgression.fromClosedRange(30, 40, 5).map { "percAtt+21&percBoss+$it" },
        listOf("percAtt+24&percBoss+30"),
    ).flatten()
    private val wsSelections160 = listOf(
        intArrayOf(18, 21, 24, 33, 36, 39).map { "percAtt+$it" },
        IntProgression.fromClosedRange(20, 26, 3).map { "lineIed+1&percAtt+$it" },
        listOf("lineAtt+1&lineBoss+1", "lineAtt+1&lineBoss+2", "lineAtt+2&lineBoss+1"),
        IntProgression.fromClosedRange(30, 40, 5).map { "percAtt+23&percBoss+$it" },
        listOf("percAtt+26&percBoss+30"),
    ).flatten()
    private val eSelections = intArrayOf(18, 21, 24, 30, 33, 36).map { "percAtt+$it" } +
            IntProgression.fromClosedRange(18, 24, 3).map { "lineIed+1&percAtt+$it" }

    private fun getSelection(name: String, itemLevel: Int) = when (name) {
        "纹章" -> eSelections // 纹章现在只算100的
        "武器", "副手" -> if (itemLevel < 160) wsSelections else wsSelections160
        "首饰" -> accessorySelections // 首饰现在只算150的
        "帽子" -> hatSelections // 帽子现在只算150的
        "手套" -> gloveSelections160 // 手套现在只算200的
        else -> if (itemLevel < 160) defaultSelections else defaultSelections160
    }

    fun doStuff(s: String): String? {
        val (name, level) = nameMap[s] ?: return null
        val (_, eToUR) = runCalculator(name, "red", Tier.Epic.ordinal, level, Tier.Legendary.ordinal, "")
        val (_, eToUB) = runCalculator(name, "black", Tier.Epic.ordinal, level, Tier.Legendary.ordinal, "")
        val (eToUCube, eToUCost) = if (eToUR < eToUB) "红" to eToUR else "黑" to eToUB
        val prefix = "以下是${level}级${s}的数学期望：\n紫洗绿，${eToUCube}魔方，${eToUCost.format()}"
        return getSelection(s, level).joinToString("", prefix) {
            val (_, red) = runCalculator(name, "red", Tier.Legendary.ordinal, level, Tier.Legendary.ordinal, it)
            val (_, black) = runCalculator(name, "black", Tier.Legendary.ordinal, level, Tier.Legendary.ordinal, it)
            val (color, cost) = if (red <= black) "红" to red else "黑" to black
            if (cost < eToUCost) return@joinToString ""
            val target = it.split("&").joinToString("与") { stat ->
                val arr = stat.split("+")
                statMap[arr[0]]!!(arr[1])
            }
            "\n${target}，${color}魔方，${cost.format()}"
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

    val cubeRates = javaClass.getResourceAsStream("/cubeRates.js")!!.use { `is` ->
        json.parseToJsonElement(String(`is`.readAllBytes()).replaceFirst("const cubeRates = ", ""))
    }

    private fun getCubeCost(cubeType: String): Long = when (cubeType) {
        "red" -> 12000000
        "black" -> 22000000
        "master" -> 7500000
        else -> 0
    }

    private fun getRevealCostConstant(itemLevel: Int): Double = when {
        itemLevel < 30 -> 0.0
        itemLevel <= 70 -> 0.5
        itemLevel <= 120 -> 2.5
        else -> 20.0
    }

    private fun cubingCost(cubeType: String, itemLevel: Int, totalCubeCount: Double): Double {
        val cubeCost = getCubeCost(cubeType)
        val revealCostConst = getRevealCostConstant(itemLevel)
        val revealPotentialCost = revealCostConst * itemLevel * itemLevel
        return cubeCost * totalCubeCount + totalCubeCount * revealPotentialCost
    }

    private fun getTierCosts(currentTier: Int, desireTier: Int, cubeType: String): Result {
        var mean = 0.0
        var median = 0.0
        var seventyFifth = 0.0
        var eightyFifth = 0.0
        var ninetyFifth = 0.0
        for (i in currentTier..<desireTier) {
            val p = tierRates[cubeType]!![i]!!
            val stats = getDistrQuantile(p)
            mean += stats.mean
            median += stats.median
            seventyFifth += stats.seventyFifth
            eightyFifth += stats.eightyFifth
            ninetyFifth += stats.ninetyFifth
        }
        return Result(mean, median, seventyFifth, eightyFifth, ninetyFifth)
    }

    // Nexon rates: https://maplestory.nexon.com/Guide/OtherProbability/cube/strange
    // GMS community calculated rates: https://docs.google.com/spreadsheets/d/1od_hep5Y6x2ljfrh4M8zj5RwlpgYDRn5uTymx4iLPyw/pubhtml#
    // Nexon rates used when they match close enough to ours.
    private val tierRates = mapOf(
        "occult" to mapOf(Tier.Rare.ordinal to 0.009901),
        // Community rates are notably higher than nexon rates here. Assuming GMS is different and using those instead.
        "master" to mapOf(Tier.Rare.ordinal to 0.1184, Tier.Epic.ordinal to 0.0381),
        // Community rates are notably higher than nexon rates here. Assuming GMS is different and using those instead.
        // The sample size isn't great, but anecdotes from people in twitch chats align with the community data.
        // That being said, take meister tier up rates with a grain of salt.
        "meister" to mapOf(Tier.Rare.ordinal to 0.1163, Tier.Epic.ordinal to 0.0879, Tier.Unique.ordinal to 0.0459),
        // Community rates notably higher than KMS rates, using them.
        "red" to mapOf(Tier.Rare.ordinal to 0.14, Tier.Epic.ordinal to 0.06, Tier.Unique.ordinal to 0.025),
        // Community rates notably higher than KMS rates, using them.
        "black" to mapOf(Tier.Rare.ordinal to 0.17, Tier.Epic.ordinal to 0.11, Tier.Unique.ordinal to 0.05)
    )

    private class Result(
        val mean: Double,
        val median: Double,
        val seventyFifth: Double,
        val eightyFifth: Double,
        val ninetyFifth: Double
    )

    private fun getDistrQuantile(p: Double): Result {
        val mean = 1 / p
        val median = ln(1 - 0.5) / ln(1 - p)
        val seventyFifth = ln(1 - 0.75) / ln(1 - p)
        val eightyFifth = ln(1 - 0.85) / ln(1 - p)
        val ninetyFifth = ln(1 - 0.95) / ln(1 - p)
        return Result(mean, median, seventyFifth, eightyFifth, ninetyFifth)
    }

    private fun runCalculator(
        itemType: String,
        cubeType: String,
        currentTier: Int,
        itemLevel: Int,
        desiredTier: Int,
        desiredStat: String
    ): Pair<Long, Long> {
        val anyStats = desiredStat.isEmpty()
        val probabilityInputObject = translateInputToObject(desiredStat)
        val p =
            if (anyStats) 1.0 else getProbability(desiredTier, probabilityInputObject, itemType, cubeType, itemLevel)
        val tierUp = getTierCosts(currentTier, desiredTier, cubeType)
        val stats = if (anyStats) Result(0.0, 0.0, 0.0, 0.0, 0.0) else getDistrQuantile(p)

        val mean = stats.mean + tierUp.mean
        val meanCost = cubingCost(cubeType, itemLevel, mean)
        return mean.roundToLong() to meanCost.roundToLong()
    }

    private fun getProbability(
        desiredTier: Int,
        probabilityInput: JsonObject,
        itemType: String,
        cubeType: String,
        itemLevel: Int,
    ): Double {
        // convert parts of input for easier mapping to keys in cubeRates
        val tier = Tier.forNumber(desiredTier).tierName
        val itemLabel = when (itemType) {
            "accessory" -> "ring"
            "badge" -> "heart"
            else -> itemType
        }

        // get the cubing data for this input criteria from cubeRates (which is based on json data)
        val rawCubedata = Triple(
            cubeRates.jsonObject["lvl120to200"]!!.jsonObject[itemLabel]!!.jsonObject[cubeType]!!.jsonObject[tier]!!.jsonObject["first_line"]!!.jsonArray,
            cubeRates.jsonObject["lvl120to200"]!!.jsonObject[itemLabel]!!.jsonObject[cubeType]!!.jsonObject[tier]!!.jsonObject["second_line"]!!.jsonArray,
            cubeRates.jsonObject["lvl120to200"]!!.jsonObject[itemLabel]!!.jsonObject[cubeType]!!.jsonObject[tier]!!.jsonObject["third_line"]!!.jsonArray
        )

        // make adjustments to stat values if needed (for items lvl 160 or above)
        val cubeData = convertCubeDataForLevel(rawCubedata, itemLevel)

        // generate consolidated version of cubing data that group any lines not relevant to the calculation into a single
        // Junk entry
        val usefulCategories = getUsefulCategories(probabilityInput)
        val consolidatedCubeData = Triple(
            getConsolidatedRates(cubeData.first, usefulCategories),
            getConsolidatedRates(cubeData.second, usefulCategories),
            getConsolidatedRates(cubeData.third, usefulCategories)
        )

        // loop through all possible outcomes for 1st, 2nd, and 3rd line using consolidated cube data
        // sum up the rate of outcomes that satisfied the input to determine final probability
        var totalChance = 0.0
        for (line1 in consolidatedCubeData.first) {
            for (line2 in consolidatedCubeData.second) {
                for (line3 in consolidatedCubeData.third) {
                    // check if this outcome meets our needs
                    val outcome = listOf(line1, line2, line3)
                    if (probabilityInput.all { (field, input) ->
                            OUTCOME_MATCH_FUNCTION_MAP[field]!!(outcome, input.jsonPrimitive.int)
                        }) // calculate chance of this outcome occurring
                        totalChance += calculateRate(outcome, consolidatedCubeData)
                }
            }
        }
        return totalChance / 100.0
    }

    private enum class Tier(val tierName: String) {
        Rare("rare"), Epic("epic"), Unique("unique"), Legendary("legendary");

        companion object {
            fun forNumber(n: Int) = when (n) {
                3 -> Legendary
                2 -> Unique
                1 -> Epic
                0 -> Rare
                else -> throw RuntimeException("unknown tier: $n")
            }
        }
    }

    /**
     * 将“lineMeso+1&lineStat+1”格式的[String]输入转化为[JsonObject]
     */
    private fun translateInputToObject(input: String): JsonObject {
        val output = HashMap<String, JsonPrimitive>()
        if (input.isNotEmpty()) {
            val vals = input.split("&")
            for (`val` in vals) {
                val arr = `val`.split("+")
                output.compute(arr[0]) { _, v -> JsonPrimitive((v?.int ?: 0) + arr[1].toInt()) }
            }
        }
        return JsonObject(output)
    }


    /**
     * 计算概率
     */
    private fun calculateRate(
        outcome: List<JsonArray>,
        filteredRates: Triple<List<JsonArray>, List<JsonArray>, List<JsonArray>>
    ): Double {
        /**
         * 对特殊的潜能条目进行调整。
         *
         * 参考： https://maplestory.nexon.com/Guide/OtherProbability/cube/strange
         */
        fun getAdjustedRate(
            currentLine: JsonArray,
            previousLines: List<JsonArray>,
            currentPool: List<JsonArray>
        ): Double {
            val currentCategory = currentLine[0].jsonPrimitive
            val currentRate = currentLine[2].jsonPrimitive.double

            // the first line will never have its rates adjusted
            if (previousLines.isEmpty()) {
                return currentRate
            }

            // determine special categories that we've reached the limit on in previous lines which need to be removed from
            // the current pool
            val toBeRemoved = ArrayList<String>()
            val prevSpecialLinesCount = HashMap<String, Int>()
            for (a in previousLines) {
                val cat = a[0].jsonPrimitive
                if (cat.isString && MAX_CATEGORY_COUNT.containsKey(cat.content))
                    prevSpecialLinesCount.compute(cat.content) { _, v -> (v ?: 0) + 1 }
            }

            // populate the list of special lines to be removed from the current pool
            // exit early with rate of 0 if this set of lines is not valid (exceeds max category count)
            for ((spCat, count) in prevSpecialLinesCount) {
                if (count > MAX_CATEGORY_COUNT[spCat]!! ||
                    currentCategory.isString && spCat == currentCategory.content && (count + 1) > MAX_CATEGORY_COUNT[spCat]!!
                ) {
                    return 0.0
                } else if (count == MAX_CATEGORY_COUNT[spCat]) {
                    toBeRemoved.add(spCat)
                }
            }

            // deduct total rate for each item that is removed from the pool
            var adjustedTotal = 100.0
            // avoid doing math operations if the rate is not changing (due to floating point issues)
            var adjustedFlag = false
            for (a in currentPool) {
                val cat = a[0].jsonPrimitive
                val rate = a[2].jsonPrimitive.double
                if (cat.isString && cat.content in toBeRemoved) {
                    adjustedTotal -= rate
                    adjustedFlag = true
                }
            }

            return if (adjustedFlag) currentRate / adjustedTotal * 100 else currentRate
        }

        val adjustedRates = listOf(
            getAdjustedRate(outcome[0], listOf(), filteredRates.first),
            getAdjustedRate(outcome[1], listOf(outcome[0]), filteredRates.second),
            getAdjustedRate(outcome[2], listOf(outcome[0], outcome[1]), filteredRates.third)
        )

        // calculate probability for this specific set of lines to occur
        var chance = 100.0
        for (rate in adjustedRates)
            chance *= rate / 100

        return chance
    }

    /**
     * 计算联合概率
     */
    private fun getConsolidatedRates(ratesList: JsonArray, usefulCategories: List<String>): List<JsonArray> {
        val consolidatedRates = ArrayList<JsonArray>()
        var junkRate = 0.0
        val junkCategories = ArrayList<JsonElement>()

        for (e in ratesList) {
            val item = e.jsonArray
            val category = item[0].jsonPrimitive
            val `val` = item[1]
            val rate = item[2]

            if (category.isString && category.content in usefulCategories || MAX_CATEGORY_COUNT.containsKey(category.content)) {
                consolidatedRates.add(item)
            } else if (category.isString && category.content == CATEGORY.JUNK) {
                // using concat here since "Junk" is already a category that exists in the json data.
                // we're expanding it here with additional "contextual junk" based on the user input, so we want to preserve
                // the old list of junk categories too
                junkRate += rate.jsonPrimitive.double
                junkCategories.addAll(`val`.jsonArray)
            } else {
                junkRate += rate.jsonPrimitive.double
                junkCategories.add(JsonPrimitive("$category ($`val`)"))
            }
        }

        consolidatedRates.add(
            JsonArray(listOf(JsonPrimitive(CATEGORY.JUNK), JsonArray(junkCategories), JsonPrimitive(junkRate)))
        )
        return consolidatedRates
    }

    /**
     * 筛选有用的属性
     */
    private fun getUsefulCategories(probabilityInput: JsonObject): List<String> {
        val usefulCategories = ArrayList<String>()
        for ((field, `val`) in INPUT_CATEGORY_MAP) {
            val input = probabilityInput[field] ?: continue
            if (input.jsonPrimitive.int > 0)
                usefulCategories.addAll(`val`)
        }
        return usefulCategories.toSet().toList()
    }

    /**
     * 针对160+的装备，12%会变成13%
     */
    private fun convertCubeDataForLevel(
        cubeData: Triple<JsonArray, JsonArray, JsonArray>,
        itemLevel: Int
    ): Triple<JsonArray, JsonArray, JsonArray> {
        // don't need to make adjustments to items lvl <160
        if (itemLevel < 160)
            return cubeData

        val affectedCategories = listOf(
            CATEGORY.STR_PERC, CATEGORY.LUK_PERC, CATEGORY.DEX_PERC, CATEGORY.INT_PERC,
            CATEGORY.ALLSTATS_PERC, CATEGORY.ATT_PERC, CATEGORY.MATT_PERC
        )

        fun f(cubeDataLine: JsonArray) = JsonArray(
            cubeDataLine.map { e ->
                val arr = e.jsonArray
                val cat = arr[0].jsonPrimitive
                val `val` = arr[1]
                val rate = arr[2]
                var adjustedVal = `val`

                // adjust the value if this is an affected category
                for (affectedCategory in affectedCategories) {
                    if (cat.isString && affectedCategory == cat.content) {
                        adjustedVal = JsonPrimitive(`val`.jsonPrimitive.int + 1)
                        break
                    }
                }

                JsonArray(listOf(cat, adjustedVal, rate))
            }
        )
        return Triple(f(cubeData.first), f(cubeData.second), f(cubeData.third))
    }

    /**
     * 计算属性的总值
     *
     * @param calcVal false-只算条数，true-算值之和
     */
    private fun calculateTotal(outcome: List<JsonArray>, desiredCategory: String, calcVal: Boolean = false): Int {
        return if (calcVal) {
            outcome.fold(0) { actualVal, a ->
                val category = a[0].jsonPrimitive
                val `val` = a[1]
                actualVal + if (category.isString && category.content == desiredCategory) `val`.jsonPrimitive.int else 0
            }
        } else {
            outcome.count { a ->
                val category = a[0].jsonPrimitive
                category.isString && category.content == desiredCategory
            }
        }
    }

    /**
     * 判断是否满足条件的函数
     */
    private val OUTCOME_MATCH_FUNCTION_MAP: Map<String, (List<JsonArray>, Int) -> Boolean> = mapOf(
        "percStat" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.STR_PERC, true) +
                    calculateTotal(outcome, CATEGORY.ALLSTATS_PERC, true) >= requiredVal
        },
        "lineStat" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.STR_PERC) +
                    calculateTotal(outcome, CATEGORY.ALLSTATS_PERC) >= requiredVal
        },
        "percAllStat" to { outcome, requiredVal ->
            outcome.fold(0.0) { actualVal, a ->
                val category = a[0].jsonPrimitive
                actualVal + if (category.isString) {
                    val `val` = a[1]
                    when (category.content) { // （尖兵）力量、敏捷、运气都算作1/3全属性
                        CATEGORY.ALLSTATS_PERC -> `val`.jsonPrimitive.double
                        CATEGORY.STR_PERC, CATEGORY.DEX_PERC, CATEGORY.LUK_PERC -> `val`.jsonPrimitive.double / 3
                        else -> 0.0
                    }
                } else 0.0
            } >= requiredVal
        },
        "lineAllStat" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.ALLSTATS_PERC) >= requiredVal
        },
        "percHp" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.MAXHP_PERC, true) >= requiredVal
        },
        "lineHp" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.MAXHP_PERC) >= requiredVal
        },
        "percAtt" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.ATT_PERC, true) >= requiredVal
        },
        "lineAtt" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.ATT_PERC) >= requiredVal
        },
        "percBoss" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.BOSSDMG_PERC, true) >= requiredVal
        },
        "lineBoss" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.BOSSDMG_PERC) >= requiredVal
        },
        "lineIed" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.IED_PERC) >= requiredVal
        },
        "lineCritDamage" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.CRITDMG_PERC) >= requiredVal
        },
        "lineMeso" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.MESO_PERC) >= requiredVal
        },
        "lineDrop" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.DROP_PERC) >= requiredVal
        },
        "lineMesoOrDrop" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.MESO_PERC) + calculateTotal(outcome, CATEGORY.DROP_PERC) >= requiredVal
        },
        "secCooldown" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.CDR_TIME, true) >= requiredVal
        },
        "lineAutoSteal" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.AUTOSTEAL_PERC) >= requiredVal
        },
        "lineAttOrBoss" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.ATT_PERC) +
                    calculateTotal(outcome, CATEGORY.BOSSDMG_PERC) >= requiredVal
        },
        "lineAttOrBossOrIed" to { outcome, requiredVal ->
            calculateTotal(outcome, CATEGORY.ATT_PERC) +
                    calculateTotal(outcome, CATEGORY.BOSSDMG_PERC) +
                    calculateTotal(outcome, CATEGORY.IED_PERC) >= requiredVal
        },
    )

    @Suppress("UNUSED")
    private object CATEGORY {
        const val STR_PERC = "STR %"
        const val DEX_PERC = "DEX %"
        const val INT_PERC = "INT %"
        const val LUK_PERC = "LUK %"
        const val MAXHP_PERC = "Max HP %"
        const val MAXMP_PERC = "Max MP %"
        const val ALLSTATS_PERC = "All Stats %"
        const val ATT_PERC = "ATT %"
        const val MATT_PERC = "MATT %"
        const val BOSSDMG_PERC = "Boss Damage"
        const val IED_PERC = "Ignore Enemy Defense %"
        const val MESO_PERC = "Meso Amount %"
        const val DROP_PERC = "Item Drop Rate %"
        const val AUTOSTEAL_PERC = "Chance to auto steal %"
        const val CRITDMG_PERC = "Critical Damage %"
        const val CDR_TIME = "Skill Cooldown Reduction"
        const val JUNK = "Junk"

        // only used for special line probability adjustment calculation
        const val DECENT_SKILL = "Decent Skill"
        const val INVINCIBLE_PERC = "Chance of being invincible for seconds when hit"
        const val INVINCIBLE_TIME = "Increase invincibility time after being hit"
        const val IGNOREDMG_PERC = "Chance to ignore % damage when hit"
    }

    /**
     * 有用的属性，例如：
     * * 如果需要力量%，则力量%和全属性%都是有用的属性
     * * 如果需要全属性%（尖兵），则除了全属性%以外，力量%|敏捷%|运气%都有用（算作1/3全属性%）
     */
    private val INPUT_CATEGORY_MAP = mapOf(
        "percStat" to listOf(CATEGORY.STR_PERC, CATEGORY.ALLSTATS_PERC),
        "lineStat" to listOf(CATEGORY.STR_PERC, CATEGORY.ALLSTATS_PERC),
        "percAllStat" to listOf(CATEGORY.ALLSTATS_PERC, CATEGORY.STR_PERC, CATEGORY.DEX_PERC, CATEGORY.LUK_PERC),
        "lineAllStat" to listOf(CATEGORY.ALLSTATS_PERC),
        "percHp" to listOf(CATEGORY.MAXHP_PERC),
        "lineHp" to listOf(CATEGORY.MAXHP_PERC),
        "percAtt" to listOf(CATEGORY.ATT_PERC),
        "lineAtt" to listOf(CATEGORY.ATT_PERC),
        "percBoss" to listOf(CATEGORY.BOSSDMG_PERC),
        "lineBoss" to listOf(CATEGORY.BOSSDMG_PERC),
        "lineIed" to listOf(CATEGORY.IED_PERC),
        "lineCritDamage" to listOf(CATEGORY.CRITDMG_PERC),
        "lineMeso" to listOf(CATEGORY.MESO_PERC),
        "lineDrop" to listOf(CATEGORY.DROP_PERC),
        "lineMesoOrDrop" to listOf(CATEGORY.DROP_PERC, CATEGORY.MESO_PERC),
        "secCooldown" to listOf(CATEGORY.CDR_TIME),
        "lineAutoSteal" to listOf(CATEGORY.AUTOSTEAL_PERC),
        "lineAttOrBoss" to listOf(CATEGORY.ATT_PERC, CATEGORY.BOSSDMG_PERC),
        "lineAttOrBossOrIed" to listOf(CATEGORY.ATT_PERC, CATEGORY.BOSSDMG_PERC, CATEGORY.IED_PERC),
    )

    /**
     * 以下属性最多出现1条或者2条
     */
    private val MAX_CATEGORY_COUNT = mapOf(
        CATEGORY.DECENT_SKILL to 1,
        CATEGORY.INVINCIBLE_TIME to 1,
        CATEGORY.IED_PERC to 2,
        CATEGORY.BOSSDMG_PERC to 2,
        CATEGORY.DROP_PERC to 2,
        CATEGORY.IGNOREDMG_PERC to 2,
        CATEGORY.INVINCIBLE_PERC to 2,
    )
}