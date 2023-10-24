package net.cutereimu.maplebots.cube

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * These are all the possible kinds of input the probability calculator can look for.
 *
 * @param percStat At least this much % stat including % allstat lines.
 * @param lineStat At least this many lines of % stat including allstat lines.
 * @param percAllStat At least this much % all stat including 1/3rd of % STR, DEX, and LUK. For Xenons.
 * @param lineAllStat At least this many lines of % all stat (does not include).
 * @param percHp At least this much % HP. For DA.
 * @param lineHp At least this many lines of % HP. For DA.
 * @param percAtt At least this much % atk.
 * @param lineAtt At least this many lines of % atk.
 * @param lineMesoOrDrop At least this many lines of meso OR drop.
 * @param secCooldown At least this many seconds of cooldown reduction.
 */
@Serializable
internal class InputObject(
    private var percStat: Int = 0,
    private var lineStat: Int = 0,
    private var percAllStat: Int = 0,
    private var lineAllStat: Int = 0,
    private var percHp: Int = 0,
    private var lineHp: Int = 0,
    private var percAtt: Int = 0,
    private var lineAtt: Int = 0,
    private var percBoss: Int = 0,
    private var lineBoss: Int = 0,
    private var lineIed: Int = 0,
    private var lineCritDamage: Int = 0,
    private var lineMeso: Int = 0,
    private var lineDrop: Int = 0,
    private var lineMesoOrDrop: Int = 0,
    private var secCooldown: Int = 0,
    private var lineAutoSteal: Int = 0,
    private var lineAttOrBoss: Int = 0,
    private var lineAttOrBossOrIed: Int = 0,
) {
    operator fun get(propertyName: String): Int = when (propertyName) {
        "percStat" -> percStat
        "lineStat" -> lineStat
        "percAllStat" -> percAllStat
        "lineAllStat" -> lineAllStat
        "percHp" -> percHp
        "lineHp" -> lineHp
        "percAtt" -> percAtt
        "lineAtt" -> lineAtt
        "percBoss" -> percBoss
        "lineBoss" -> lineBoss
        "lineIed" -> lineIed
        "lineCritDamage" -> lineCritDamage
        "lineMeso" -> lineMeso
        "lineDrop" -> lineDrop
        "lineMesoOrDrop" -> lineMesoOrDrop
        "secCooldown" -> secCooldown
        "lineAutoSteal" -> lineAutoSteal
        "lineAttOrBoss" -> lineAttOrBoss
        "lineAttOrBossOrIed" -> lineAttOrBossOrIed
        else -> throw Exception("unknown property: $propertyName")
    }

    operator fun set(propertyName: String, value: Int) = when (propertyName) {
        "percStat" -> percStat = value
        "lineStat" -> lineStat = value
        "percAllStat" -> percAllStat = value
        "lineAllStat" -> lineAllStat = value
        "percHp" -> percHp = value
        "lineHp" -> lineHp = value
        "percAtt" -> percAtt = value
        "lineAtt" -> lineAtt = value
        "percBoss" -> percBoss = value
        "lineBoss" -> lineBoss = value
        "lineIed" -> lineIed = value
        "lineCritDamage" -> lineCritDamage = value
        "lineMeso" -> lineMeso = value
        "lineDrop" -> lineDrop = value
        "lineMesoOrDrop" -> lineMesoOrDrop = value
        "secCooldown" -> secCooldown = value
        "lineAutoSteal" -> lineAutoSteal = value
        "lineAttOrBoss" -> lineAttOrBoss = value
        "lineAttOrBossOrIed" -> lineAttOrBossOrIed = value
        else -> throw Exception("unknown property: $propertyName")
    }

    override fun toString(): String {
        return json.encodeToString(this)
    }

    companion object {
        val allFields = listOf(
            "percStat",
            "lineStat",
            "percAllStat",
            "lineAllStat",
            "percHp",
            "lineHp",
            "percAtt",
            "lineAtt",
            "percBoss",
            "lineBoss",
            "lineIed",
            "lineCritDamage",
            "lineMeso",
            "lineDrop",
            "lineMesoOrDrop",
            "secCooldown",
            "lineAutoSteal",
            "lineAttOrBoss",
            "lineAttOrBossOrIed",
        )
    }
}

internal fun getProbability(
    desiredTier: Int,
    probabilityInput: InputObject,
    itemType: String,
    cubeType: String,
    itemLevel: Int,
): Double {
    logger.info("tier=$desiredTier, item=$itemType, cube=$cubeType")
    logger.info("probability input $probabilityInput")

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
    logger.info("usefulCategories ${usefulCategories.toTypedArray().contentToString()}")
    val consolidatedCubeData = Triple(
        getConsolidatedRates(cubeData.first, usefulCategories),
        getConsolidatedRates(cubeData.second, usefulCategories),
        getConsolidatedRates(cubeData.third, usefulCategories)
    )

    // loop through all possible outcomes for 1st, 2nd, and 3rd line using consolidated cube data
    // sum up the rate of outcomes that satisfied the input to determine final probability
    var totalChance = 0.0
    var totalCount = 0
    var countUseful = 0
    var countInvalid = 0
    logger.info("=== Generating all possible outcomes ===")
    for (line1 in consolidatedCubeData.first) {
        for (line2 in consolidatedCubeData.second) {
            for (line3 in consolidatedCubeData.third) {
                // check if this outcome meets our needs
                val outcome = listOf(line1, line2, line3)
                if (satisfiesInput(outcome, probabilityInput)) {
                    // calculate chance of this outcome occurring
                    logger.info("Outcome #${totalCount + 1} matches input")
                    val result = calculateRate(outcome, consolidatedCubeData)
                    totalChance += result

                    if (result == 0.0) {
                        countInvalid++
                    } else {
                        countUseful++
                    }
                }
                totalCount++
            }
        }
    }
    logger.info("=== RESULTS ===")
    logger.info("Total chance: $totalChance (without rounding: $totalChance)")

    return totalChance / 100.0
}

internal enum class Tier(val tierName: String) {
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
 * This function translates the string that comes from the select element to the object that the probability calculator
 * uses. To make it simple to add more options to the calculator I'm going with the following system for "select"
 * "option" values:
 *
 * Each "option"'s value will be a string that looks like v1&v2&v3, where each v looks like "s+n". s is the name of the
 * stat and n is a number for how much of it we want. Each v is separated by & to make it easy to parse. s and n are
 * likewise separated by + so they are easy to parse and unambiguous.
 *
 * For what the possible stats are, see emptyInputObject in cube.getProbability.js
 *
 * @param webInput The value from the HTML element.
 */
internal fun translateInputToObject(webInput: String): InputObject {
    val vals = webInput.split("&")
    val output = InputObject()
    for (`val` in vals) {
        val arr = `val`.split("+")
        output[arr[0]] += arr[1].toInt()
    }
    return output
}


/**
 * calculate chance for an outcome to occur
 * obtained by multiplying of the rates of the item rolled on the 1st, 2nd, and 3rd line with each other
 * rates of lines 2 and/or 3 get adjusted if there are "special" lines rolled prior that affect their probability
 */
private fun calculateRate(
    outcome: List<JsonArray>,
    filteredRates: Triple<List<JsonArray>, List<JsonArray>, List<JsonArray>>
): Double {
    /**
     * calculate the adjusted rate for a line in the outcome based on previous special lines, current pool of possibilities
     * calculation method (from Nexon's website):
     * display probability / (100% - the sum of the display probabilities of the excluded options)
     * reference: https://maplestory.nexon.com/Guide/OtherProbability/cube/strange
     */
    fun getAdjustedRate(currentLine: JsonArray, previousLines: List<JsonArray>, currentPool: List<JsonArray>): Double {
        val currentCategory = currentLine[0].jsonPrimitive
        val currentRate = currentLine[2].jsonPrimitive.double
        val currentLineNum = previousLines.size + 1

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
                logger.info("Outcome is invalid. Exceeded count for $spCat.")
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
            val `val` = a[1]
            val rate = a[2].jsonPrimitive.double
            if (cat.isString && cat.content in toBeRemoved) {
                adjustedTotal -= rate
                adjustedFlag = true
                logger.info("Line $currentLineNum: Removed [$cat: $`val`] from pool. new adjusted_total for this line is: $adjustedTotal")
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

    logger.info("chance for this outcome to occur $chance")
    return chance
}

/**
 * check if an outcome meets our the input requirements
 */
private fun satisfiesInput(outcome: List<JsonArray>, probabilityInput: InputObject) =
    InputObject.allFields.all { field ->
        probabilityInput[field] <= 0 || OUTCOME_MATCH_FUNCTION_MAP[field]!!(outcome, probabilityInput[field])
    }

/**
 * consolidate number of entries in the rates list to only the lines we care about
 * all other categories we don't care about get lumped into a single entry for junk lines
 * Note(ming) we still keep around "special" lines which can impact the probability of 2nd or 3rd lines even
 * if we don't want them
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
 * generate a list of relevant categories based on the input
 * this will be used to consolidate entries from the list of rates prior to generating all the possible outcomes to
 * calculate
 */
private fun getUsefulCategories(probabilityInput: InputObject): List<String> {
    val usefulCategories = ArrayList<String>()
    for ((field, `val`) in INPUT_CATEGORY_MAP) {
        if (probabilityInput[field] > 0)
            usefulCategories.addAll(`val`)
    }
    return usefulCategories.toSet().toList()
}

/**
 * modify cube data based on item level if needed (for items over lvl 160)
 * HACK(ming): KMS does not have adjusted Stat % based on item level, so we are making the assumption that
 * for lvl 160+ items, value of stat percentage categories are increased by 1% (e.g. 12% STR becomes 13% STR)
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
                    logger.info("Found affected category ${cat}: $`val` -> $adjustedVal")
                    break
                }
            }

            JsonArray(listOf(cat, adjustedVal, rate))
        }
    )
    return Triple(f(cubeData.first), f(cubeData.second), f(cubeData.third))
}

/**
 * type of calculation can be total number of lines or a value sum (e.g. stat %, seconds of CDR)
 */
private enum class CalcType {
    Line, Val
}

/**
 * calculate "effective" All Stats %
 * where STR, DEX or LUK % each count as 1/3 All Stats %
 */
private fun checkPercAllStat(outcome: List<JsonArray>, requiredVal: Int): Boolean {
    var actualVal = 0.0
    for (a in outcome) {
        val category = a[0].jsonPrimitive
        if (!category.isString) continue
        val `val` = a[1]
        if (category.content == CATEGORY.ALLSTATS_PERC)
            actualVal += `val`.jsonPrimitive.int
        else if (category.content in arrayOf(CATEGORY.STR_PERC, CATEGORY.DEX_PERC, CATEGORY.LUK_PERC))
            actualVal += `val`.jsonPrimitive.int / 3
    }
    return actualVal >= requiredVal
}

/**
 * get the total number of lines or total value of a specific category in this outcome
 * calcType: specifies whether we are calculating number of lines or total value (defaults to lines if not specified)
 */
private fun calculateTotal(
    outcome: List<JsonArray>,
    desiredCategory: String,
    calcType: CalcType = CalcType.Line
): Int {
    var actualVal = 0
    for (a in outcome) {
        val category = a[0].jsonPrimitive
        val `val` = a[1]
        if (category.isString && category.content == desiredCategory) {
            if (calcType == CalcType.Val)
                actualVal += `val`.jsonPrimitive.int
            else if (calcType == CalcType.Line)
                actualVal += 1
        }
    }
    return actualVal
}

/**
 * map each input to a function that checks if it has been satisfied
 * where "outcome" refers to the set of potential lines that were rolled
 */
private val OUTCOME_MATCH_FUNCTION_MAP: Map<String, (List<JsonArray>, Int) -> Boolean> = mapOf(
    "percStat" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.STR_PERC, CalcType.Val) +
                calculateTotal(outcome, CATEGORY.ALLSTATS_PERC, CalcType.Val) >= requiredVal
    },
    "lineStat" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.STR_PERC) +
                calculateTotal(outcome, CATEGORY.ALLSTATS_PERC) >= requiredVal
    },
    "percAllStat" to ::checkPercAllStat,
    "lineAllStat" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.ALLSTATS_PERC) >= requiredVal
    },
    "percHp" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.MAXHP_PERC, CalcType.Val) >= requiredVal
    },
    "lineHp" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.MAXHP_PERC) >= requiredVal
    },
    "percAtt" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.ATT_PERC, CalcType.Val) >= requiredVal
    },
    "lineAtt" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.ATT_PERC) >= requiredVal
    },
    "percBoss" to { outcome, requiredVal ->
        calculateTotal(outcome, CATEGORY.BOSSDMG_PERC, CalcType.Val) >= requiredVal
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
        calculateTotal(outcome, CATEGORY.CDR_TIME, CalcType.Val) >= requiredVal
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

/** labels for categories used in json data reference and calculations */
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
 * map inputs to categories in json data that could contribute to a match
 * using STR % to represent stat % for STR, LUK, INT, DEX since they all have the same rates
 * using ATT % to represent both ATT and MATT % for the same reason
 * Assumptions used in calculations:
 * - All Stats % counts as 1 line of stat %
 * - STR, DEX or LUK % each count as 1/3 All Stats %
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
 * Mapping of "special" categories and the maximum occurrence they can have per item.
 * we can only have max of 2 of these lines:
 * * IED
 * * chance to ignore % damage
 * * drop rate
 * * boss damage
 * * invincible for a short period of time with a certain probability when attacked
 *
 * we can only have 1 of these lines:
 * * any decent skill
 * * increased invincibility time after being hit
 *
 * if we reach the maximum number of occurrences for a category, that category is excluded for the next line(s)
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