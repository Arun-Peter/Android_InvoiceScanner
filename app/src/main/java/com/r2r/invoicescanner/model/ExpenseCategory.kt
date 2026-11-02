package com.r2r.invoicescanner.model

/**
 * A head of expense. The name is used verbatim in the saved file name, the
 * image sub-folder and the exported register, e.g. "Fuel_Transport".
 *
 * The built-in heads are below; users can add their own (kept as "regular"
 * categories in [com.r2r.invoicescanner.settings.CategoryStore], or used once).
 * Two categories are equal when their names match ignoring case and symbols.
 */
class ExpenseCategory(val name: String, val description: String = "", val builtIn: Boolean = false) {

    private val key: String get() = normalise(name)

    override fun equals(other: Any?): Boolean = other is ExpenseCategory && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = name

    @Suppress("ObjectPropertyName", "MemberVisibilityCanBePrivate")
    companion object {
        val Fuel_Transport = ExpenseCategory("Fuel_Transport", "Fuel, taxi/cab, tolls, parking, train/bus/flight tickets", true)
        val Business_Promotion = ExpenseCategory("Business_Promotion", "Client meals & entertainment, gifts, events, marketing", true)
        val Vehicle_Maintenance = ExpenseCategory("Vehicle_Maintenance", "Vehicle servicing, tyres, battery, spares, car wash", true)
        val Office_Supplies = ExpenseCategory("Office_Supplies", "Stationery, printer ink/toner, paper, small office items", true)
        val Repair_Maintenance = ExpenseCategory("Repair_Maintenance", "Repairs to office, premises or equipment (not vehicles)", true)
        val Mobile_Internet = ExpenseCategory("Mobile_Internet", "Mobile bills/recharges, broadband, data, Wi-Fi", true)
        val Membership_Fees = ExpenseCategory("Membership_Fees", "Professional bodies, clubs, associations, subscriptions", true)
        val Capital_Expenditure = ExpenseCategory(
            "Capital_Expenditure",
            "Purchase of long-lived assets: computers, laptops, phones, furniture, machinery, equipment",
            true,
        )
        val Capital_Drawings = ExpenseCategory(
            "Capital_Drawings",
            "Owner's / partner's personal spending paid from business funds (not a business expense)",
            true,
        )
        val Miscellaneous = ExpenseCategory("Miscellaneous", "Anything that does not fit any other head of expense", true)

        val BUILT_IN: List<ExpenseCategory> = listOf(
            Fuel_Transport, Business_Promotion, Vehicle_Maintenance, Office_Supplies, Repair_Maintenance,
            Mobile_Internet, Membership_Fees, Capital_Expenditure, Capital_Drawings, Miscellaneous,
        )

        /** Lenient lookup: accepts "Fuel_Transport", "fuel transport", "FUEL-TRANSPORT"... */
        fun fromName(value: String?, among: List<ExpenseCategory> = BUILT_IN): ExpenseCategory? {
            if (value.isNullOrBlank()) return null
            val key = normalise(value)
            return among.firstOrNull { it.key == key }
        }

        /** A known category with this name, otherwise a new user-defined one ("Staff welfare" -> "Staff_welfare"). */
        fun of(value: String, among: List<ExpenseCategory> = BUILT_IN): ExpenseCategory? {
            fromName(value, among)?.let { return it }
            val name = sanitize(value)
            return if (name.isEmpty()) null else ExpenseCategory(name)
        }

        /** Letters/digits kept, words capitalised and joined with "_" like the built-in names ("staff welfare" -> "Staff_Welfare"). */
        fun sanitize(value: String): String =
            value.split(Regex("[^\\p{L}\\p{N}\\p{M}]+"))
                .filter { it.isNotEmpty() }
                .joinToString("_") { w -> w.replaceFirstChar { it.uppercase() } }
                .take(30)
                .trimEnd('_')

        private fun normalise(s: String): String =
            s.lowercase().filter { it.isLetterOrDigit() }
    }
}
