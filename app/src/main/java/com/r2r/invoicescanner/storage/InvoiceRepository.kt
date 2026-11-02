package com.r2r.invoicescanner.storage

import android.content.Context
import com.r2r.invoicescanner.extract.AmountParser
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.Finding
import com.r2r.invoicescanner.model.InvoiceRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The invoice register, kept as a small JSON file in private app storage. */
class InvoiceRepository(context: Context) {
    private val file = File(context.filesDir, "invoice_register.json")
    private val lock = Any()

    fun load(): List<InvoiceRecord> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return try {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun saveAll(records: List<InvoiceRecord>) {
        synchronized(lock) {
            val arr = JSONArray()
            records.forEach { arr.put(toJson(it)) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    private fun toJson(r: InvoiceRecord): JSONObject = JSONObject()
        .put("id", r.id)
        .put("fileName", r.fileName)
        .put("imageUri", r.imageUri)
        .put("invoiceNo", r.invoiceNo)
        .put("date", r.date)
        .put("vendorName", r.vendorName)
        .put("currency", r.currency)
        .put("subtotal", r.subtotal ?: JSONObject.NULL)
        .put("tax", r.tax ?: JSONObject.NULL)
        .put("amount", r.amount)
        .put("read", readToJson(r.read))
        .put("category", r.category.name)
        .put("aiCategory", r.aiCategory?.name ?: JSONObject.NULL)
        .put("categoryReason", r.categoryReason)
        .put("amountConfidence", r.amountConfidence)
        .put("findings", JSONArray(r.findings.map { it.encode() }))
        .put("comment", r.comment)
        .put("engine", r.engine)
        .put("scannedAt", r.scannedAt)
        .put("updatedAt", r.updatedAt ?: JSONObject.NULL)
        .put("businessCode", r.businessCode)
        .put("narration", r.narration)

    private fun readToJson(v: FieldValues): JSONObject = JSONObject()
        .put("invoiceNo", v.invoiceNo)
        .put("date", v.date)
        .put("vendorName", v.vendorName)
        .put("currency", v.currency)
        .put("subtotal", v.subtotal)
        .put("tax", v.tax)
        .put("amount", v.amount)

    private fun readFromJson(o: JSONObject) = FieldValues(
        invoiceNo = o.optString("invoiceNo"),
        date = o.optString("date"),
        vendorName = o.optString("vendorName"),
        currency = o.optString("currency"),
        subtotal = o.optString("subtotal"),
        tax = o.optString("tax"),
        amount = o.optString("amount"),
    )

    /** Unknown names are kept as one-off user categories rather than dropping the row. */
    private fun category(name: String): ExpenseCategory? =
        if (name.isBlank()) null else ExpenseCategory.fromName(name) ?: ExpenseCategory(name)

    private fun fromJson(o: JSONObject): InvoiceRecord? {
        val category = category(o.optString("category")) ?: return null
        val findingsArr = o.optJSONArray("findings") ?: JSONArray()
        val record = InvoiceRecord(
            id = o.getString("id"),
            fileName = o.optString("fileName"),
            imageUri = o.optString("imageUri"),
            invoiceNo = o.optString("invoiceNo"),
            date = o.optString("date"),
            vendorName = o.optString("vendorName"),
            currency = o.optString("currency"),
            subtotal = o.doubleOrNull("subtotal"),
            tax = o.doubleOrNull("tax"),
            amount = o.optDouble("amount", 0.0),
            read = FieldValues(),
            category = category,
            aiCategory = if (o.isNull("aiCategory")) null else category(o.optString("aiCategory")),
            categoryReason = o.optString("categoryReason"),
            amountConfidence = o.optInt("amountConfidence", 0),
            findings = (0 until findingsArr.length()).map { Finding.decode(findingsArr.getString(it)) },
            comment = o.optString("comment"),
            engine = o.optString("engine"),
            scannedAt = o.optLong("scannedAt", 0L),
            updatedAt = if (o.isNull("updatedAt")) null else o.optLong("updatedAt").takeIf { it > 0 },
            businessCode = o.optString("businessCode"),
            narration = o.optString("narration"),
        )
        // Registers from version 1 only kept the amount as read; assume the other fields were not edited.
        val read = o.optJSONObject("read")?.let { readFromJson(it) }
            ?: record.values.copy(amount = AmountParser.plain(o.doubleOrNull("aiAmount")))
        return record.copy(read = read)
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
