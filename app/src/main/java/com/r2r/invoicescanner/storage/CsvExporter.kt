package com.r2r.invoicescanner.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.r2r.invoicescanner.model.InvoiceRecord
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds the register in the requested column order (pure, unit-testable). */
object CsvFormat {
    val HEADER = listOf(
        "FileName", "InvoiceNo", "Date", "VendorName", "Subtotal", "Tax", "Amount",
        "ExpenseType", "AmountConfidence", "AIComment",
        "Currency", "AISuggestedType", "Reclassified", "AmountEditedByUser", "ScannedOn", "EditedFields",
        "BusinessCode", "BusinessName", "ImagePath", "Narration",
    )

    /**
     * [imagePaths]: record id -> path of its image inside the export (blank if not included).
     * [businessNames]: business code -> short name.
     */
    fun toCsv(
        records: List<InvoiceRecord>,
        imagePaths: Map<String, String> = emptyMap(),
        businessNames: Map<String, String> = emptyMap(),
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        records.forEach { r ->
            val row = listOf(
                r.fileName, r.invoiceNo, r.date, r.vendorName,
                money(r.subtotal), money(r.tax), money(r.amount),
                r.category.name, r.amountConfidence.toString(), r.comment,
                r.currency, r.aiCategory?.name.orEmpty(),
                if (r.reclassified) "Yes" else "No",
                if (r.amountEditedByUser) "Yes" else "No",
                stamp.format(Date(r.scannedAt)),
                r.editedFields.joinToString("; "),
                r.businessCode, businessNames[r.businessCode].orEmpty(),
                imagePaths[r.id].orEmpty(),
                r.narration,
            )
            sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** Where an invoice's image goes in an export: "XYZ/Fuel_Transport/20261021_XYZ_Fuel_Transport_HP_1500.jpeg". */
    fun imagePath(r: InvoiceRecord): String = ImageStore.subFolder(r.businessCode, r.category) + "/" + r.fileName

    private fun money(v: Double?): String = if (v == null) "" else String.format(Locale.US, "%.2f", v)

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val v = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$v\"" else v
    }
}

/** Writes the register to Downloads/InvoiceScanner, as a CSV or as a ZIP with the images. */
object CsvExporter {
    private const val FOLDER = "Download/InvoiceScanner"
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) // UTF-8 BOM for Excel

    data class ZipResult(val uri: Uri, val images: Int, val missing: Int)

    /** CSV only. ImagePath shows where each image is on the phone, under Pictures/InvoiceScanner. */
    fun export(context: Context, records: List<InvoiceRecord>, businessNames: Map<String, String> = emptyMap()): Uri {
        val paths = records.associate { it.id to CsvFormat.imagePath(it) }
        return write(context, stamped("csv"), "text/csv") { out ->
            out.write(BOM)
            out.write(CsvFormat.toCsv(records, paths, businessNames).toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * One ZIP for accounting automation: Invoice_Register.csv at the top, and each invoice image in
     * BusinessCode/ExpenseType/ folders. ImagePath in the CSV is the image's path inside the ZIP
     * (blank when the image was deleted or is in the phone's trash).
     */
    fun exportZip(context: Context, records: List<InvoiceRecord>, businessNames: Map<String, String> = emptyMap()): ZipResult {
        val resolver = context.contentResolver
        val paths = mutableMapOf<String, String>()
        val used = mutableSetOf<String>()
        var missing = 0
        val uri = write(context, stamped("zip"), "application/zip") { out ->
            ZipOutputStream(out).use { zip ->
                for (r in records) {
                    val image = Uri.parse(r.imageUri)
                    if (ImageStore.status(context, image) != ImageStore.Status.PRESENT) {
                        missing++
                        continue
                    }
                    val input = try {
                        resolver.openInputStream(image)
                    } catch (e: Exception) {
                        null
                    }
                    if (input == null) {
                        missing++
                        continue
                    }
                    val path = unique(CsvFormat.imagePath(r), used)
                    input.use {
                        zip.putNextEntry(ZipEntry(path).apply { time = r.updatedAt ?: r.scannedAt })
                        it.copyTo(zip)
                        zip.closeEntry()
                    }
                    paths[r.id] = path
                }
                zip.putNextEntry(ZipEntry("Invoice_Register.csv"))
                zip.write(BOM)
                zip.write(CsvFormat.toCsv(records, paths, businessNames).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ZipResult(uri, paths.size, missing)
    }

    private fun unique(path: String, used: MutableSet<String>): String {
        var p = path
        var i = 2
        while (!used.add(p.lowercase())) p = path.removeSuffix(FileNamer.EXTENSION) + "_${i++}" + FileNamer.EXTENSION
        return p
    }

    private fun stamped(ext: String) =
        "Invoice_Register_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext

    private fun write(context: Context, name: String, mime: String, body: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("could not create the export file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("could not write the export file")
            out.use(body)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}
