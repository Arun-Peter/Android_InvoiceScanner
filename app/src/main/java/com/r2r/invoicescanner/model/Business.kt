package com.r2r.invoicescanner.model

/** A business (legal entity) whose invoices are being processed, e.g. "XYZ" — "Example Business". */
data class Business(val code: String, val name: String) {
    val label: String get() = if (name.isBlank()) code else "$code · $name"

    companion object {
        const val CODE_LENGTH = 3

        /** Upper-case letters/digits only, e.g. " r2r " -> "R2R". */
        fun cleanCode(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(CODE_LENGTH)

        /** Problem with a new business, or null if it can be added. */
        fun validate(code: String, name: String, existing: List<Business>): String? = when {
            cleanCode(code).length != CODE_LENGTH -> "The short code must be exactly $CODE_LENGTH letters or digits"
            existing.any { it.code == cleanCode(code) } -> "${cleanCode(code)} is already used"
            name.isBlank() -> "Enter a short name"
            else -> null
        }
    }
}
