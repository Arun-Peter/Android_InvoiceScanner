package com.r2r.invoicescanner.extract

import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Business_Promotion
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Capital_Drawings
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Capital_Expenditure
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Fuel_Transport
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Membership_Fees
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Mobile_Internet
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Office_Supplies
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Repair_Maintenance
import com.r2r.invoicescanner.model.ExpenseCategory.Companion.Vehicle_Maintenance

/**
 * Keyword-based classifier used when no AI service is configured (or it fails).
 * Each keyword carries a weight; the head of expense with the highest total wins.
 * User-defined categories are matched on their own name (e.g. "Staff_Welfare" -> "staff welfare").
 */
object CategoryClassifier {

    private val KEYWORDS: Map<ExpenseCategory, List<Pair<String, Int>>> = linkedMapOf(
        Fuel_Transport to listOf(
            "petrol" to 3, "diesel" to 3, "fuel" to 3, "filling station" to 3, "petroleum" to 3,
            "hpcl" to 3, "bpcl" to 3, "iocl" to 3, "indian oil" to 3, "nayara" to 3, "shell" to 2,
            "cng" to 2, "litre" to 2, "ltr" to 1, "uber" to 3, "ola" to 2, "rapido" to 3, "taxi" to 3,
            "cab" to 2, "metro" to 2, "irctc" to 3, "railway" to 2, "toll" to 3, "fastag" to 3,
            "parking" to 3, "airlines" to 2, "boarding pass" to 3, "bus" to 1, "travels" to 1,
            "ev charging" to 3
        ),
        Vehicle_Maintenance to listOf(
            "service centre" to 3, "service center" to 3, "workshop" to 2, "tyre" to 3, "tyres" to 3,
            "battery" to 2, "spares" to 2, "spare parts" to 3, "engine oil" to 3, "lubricant" to 2,
            "wheel alignment" to 3, "wheel balancing" to 3, "car wash" to 3, "garage" to 2, "puc" to 3,
            "periodic maintenance" to 3, "automobiles" to 2, "motors" to 1, "chassis" to 2,
            "odometer" to 3, "km reading" to 3, "vehicle no" to 2
        ),
        Office_Supplies to listOf(
            "stationery" to 3, "stationary" to 3, "pen" to 1, "pencil" to 2, "paper" to 2, "a4" to 2,
            "printer" to 2, "toner" to 3, "cartridge" to 3, "ink" to 1, "stapler" to 3, "folder" to 2,
            "notebook" to 2, "envelope" to 2, "marker" to 2, "office supplies" to 4, "xerox" to 2,
            "photocopy" to 2, "pen drive" to 2, "courier" to 1
        ),
        Repair_Maintenance to listOf(
            "repair" to 3, "repairs" to 3, "maintenance" to 1, "plumbing" to 3, "plumber" to 3,
            "electrician" to 3, "electrical" to 2, "carpentry" to 3, "carpenter" to 3, "painting" to 2,
            "ac service" to 3, "air conditioner" to 2, "amc" to 3, "annual maintenance" to 3,
            "hardware" to 2, "pest control" to 3, "civil work" to 3, "fabrication" to 2
        ),
        Mobile_Internet to listOf(
            "airtel" to 3, "jio" to 3, "vodafone" to 3, "bsnl" to 3, "mtnl" to 3, "act fibernet" to 4,
            "hathway" to 3, "broadband" to 3, "fibernet" to 3, "recharge" to 3, "postpaid" to 3,
            "prepaid" to 2, "internet" to 3, "wifi" to 3, "wi-fi" to 3, "data plan" to 3,
            "mobile bill" to 4, "telecom" to 2, "bill period" to 1
        ),
        Membership_Fees to listOf(
            "membership" to 4, "member" to 1, "subscription" to 3, "annual fee" to 3, "club" to 2,
            "association" to 3, "institute" to 2, "chamber of commerce" to 4, "icai" to 4, "icsi" to 4,
            "icmai" to 4, "cii" to 3, "ficci" to 4, "nasscom" to 4, "renewal fee" to 3,
            "enrolment" to 2, "enrollment" to 2
        ),
        Capital_Expenditure to listOf(
            "laptop" to 3, "notebook pc" to 3, "desktop" to 2, "computer" to 2, "monitor" to 2, "server" to 2,
            "furniture" to 3, "office chair" to 3, "workstation" to 2, "machinery" to 3, "machine" to 1,
            "equipment" to 2, "smartphone" to 3, "imei" to 3, "serial no" to 2,
            "warranty" to 1, "capital goods" to 4, "fixed asset" to 4, "asset" to 1
        ),
        Capital_Drawings to listOf(
            "drawings" to 4, "personal use" to 3, "personal expense" to 3
        ),
        Business_Promotion to listOf(
            "restaurant" to 3, "hotel" to 2, "cafe" to 3, "dining" to 3, "food" to 2, "meal" to 2,
            "lunch" to 2, "dinner" to 3, "breakfast" to 2, "catering" to 3, "banquet" to 3, "gift" to 3,
            "gifts" to 3, "sweets" to 3, "bakery" to 2, "event" to 2, "advertising" to 3,
            "advertisement" to 3, "marketing" to 3, "brochure" to 3, "swiggy" to 3, "zomato" to 3,
            "beverages" to 2, "table no" to 3, "covers" to 2, "steward" to 2, "service charge" to 2,
            "kot" to 2
        ),
    )

    private fun pattern(kw: String, weight: Int): Triple<String, Regex, Int> {
        val body = kw.split(" ").joinToString("\\s*") { Regex.escape(it) }
        return Triple(kw, Regex("\\b$body\\b", RegexOption.IGNORE_CASE), weight)
    }

    private val PATTERNS: Map<ExpenseCategory, List<Triple<String, Regex, Int>>> =
        KEYWORDS.mapValues { (_, list) -> list.map { (kw, weight) -> pattern(kw, weight) } }

    data class Classification(val category: ExpenseCategory?, val reason: String)

    fun classify(text: String, categories: List<ExpenseCategory> = ExpenseCategory.BUILT_IN): Classification {
        if (text.isBlank()) return Classification(null, "No text to classify")
        val custom = categories.filter { !it.builtIn }.associateWith { c ->
            listOf(pattern(c.name.replace('_', ' ').lowercase(), 3))
        }
        var best: ExpenseCategory? = null
        var bestScore = 0
        var bestHits: List<String> = emptyList()
        for ((category, patterns) in PATTERNS.filterKeys { it in categories } + custom) {
            val hits = patterns.filter { it.second.containsMatchIn(text) }
            val score = hits.sumOf { it.third }
            if (score > bestScore) {
                best = category
                bestScore = score
                bestHits = hits.map { it.first }
            }
        }
        return if (best == null || bestScore < 2) {
            Classification(null, "No clear keywords found")
        } else {
            Classification(best, "Keywords found: " + bestHits.take(4).joinToString(", "))
        }
    }
}
