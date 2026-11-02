package com.r2r.invoicescanner.extract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.InvoiceFields
import com.r2r.invoicescanner.model.ReviewDraft
import com.r2r.invoicescanner.model.bandOf
import com.r2r.invoicescanner.settings.AiSettings
import com.r2r.invoicescanner.settings.ImageStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min

/**
 * Pipeline: cropped scan -> (optional) grayscale -> on-device OCR -> rule-based
 * reading -> (optional) AI reading -> amount confidence score + reviewer comment
 * -> editable draft.
 */
class InvoiceProcessor(private val context: Context) {

    suspend fun process(
        source: Uri,
        settings: AiSettings,
        categories: List<ExpenseCategory>,
        style: ImageStyle,
        onStep: (String) -> Unit,
    ): ReviewDraft {
        onStep("Preparing the cropped image…")
        val file = withContext(Dispatchers.IO) { copyToPending(source, style) }
        return analyse(file, settings, categories, onStep)
    }

    /** OCR + rules + (optional) AI on an image file that is already prepared; used for new scans and "read again". */
    suspend fun analyse(
        file: File,
        settings: AiSettings,
        categories: List<ExpenseCategory>,
        onStep: (String) -> Unit,
    ): ReviewDraft {
        onStep("Reading text on the device…")
        val ocr = try {
            OcrEngine.read(context, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OcrEngine.OcrResult(emptyList())
        }
        val heuristic = withContext(Dispatchers.Default) { HeuristicExtractor.extract(ocr.rows, categories = categories) }

        var ai: AiResult? = null
        var aiError: String? = null
        if (settings.isConfigured) {
            onStep("AI is reading the invoice…")
            try {
                ai = AiExtractor(settings).extract(file, ocr.text, categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError = AiExtractor.friendlyError(e)
            }
        }

        onStep("Checking the amount…")
        return buildDraft(file, ocr, heuristic, ai, aiError, settings)
    }

    private fun buildDraft(
        file: File,
        ocr: OcrEngine.OcrResult,
        heuristic: HeuristicExtractor.Result,
        ai: AiResult?,
        aiError: String?,
        settings: AiSettings,
    ): ReviewDraft {
        val notes = mutableListOf<String>()
        val fields: InvoiceFields
        val baseConfidence: Int
        val baseReason: String
        var secondOpinion: Double? = null
        val category: ExpenseCategory?
        val categoryReason: String
        val engine: String

        if (ai != null) {
            engine = "${settings.provider.label} (${settings.modelLabel}) + on-device OCR"
            if (ai.comment.isNotBlank()) notes += ai.comment.trim()
            if (!ai.isInvoice) notes += "This does not look like an invoice or receipt."
            val a = ai.fields
            val h = heuristic.fields
            fields = a.copy(
                invoiceNo = a.invoiceNo ?: h.invoiceNo?.also { notes += "Invoice no. taken from the on-device reading." },
                date = a.date ?: h.date?.also { notes += "Date taken from the on-device reading." },
                vendorName = a.vendorName ?: h.vendorName,
                currency = a.currency ?: h.currency,
                amount = a.amount ?: h.amount?.also { notes += "AI could not read the amount; the on-device reading was used." },
            )
            if (a.amount != null) {
                baseConfidence = ai.amountConfidence
                baseReason = "AI certainty for the amount: ${ai.amountConfidence}%"
                secondOpinion = h.amount
            } else {
                baseConfidence = min(heuristic.amountBaseConfidence, 50)
                baseReason = heuristic.amountEvidence
            }
            category = ai.category ?: heuristic.category
            categoryReason = ai.categoryReason.ifBlank { heuristic.categoryReason }
        } else {
            engine = if (aiError != null) "On-device OCR (AI service unavailable)" else "On-device OCR"
            fields = heuristic.fields
            baseConfidence = heuristic.amountBaseConfidence
            baseReason = heuristic.amountEvidence
            category = heuristic.category
            categoryReason = heuristic.categoryReason
            when {
                aiError != null -> notes += "AI service unavailable ($aiError). Values were read on the device — please check them."
                !settings.isConfigured -> notes += "Read on the device only (no AI service set up) — please check the values."
            }
        }
        if (ocr.rows.isEmpty() && ai == null) {
            notes += "No text could be read. Retake the photo flat, in good light, with the whole receipt in the frame."
        }

        // Amount confidence
        val scored = AmountConfidenceScorer.score(
            AmountConfidenceScorer.Input(
                amount = fields.amount,
                subtotal = fields.subtotal,
                tax = fields.tax,
                baseConfidence = baseConfidence,
                baseReason = baseReason,
                ocrText = ocr.text,
                secondOpinionAmount = secondOpinion,
            )
        )
        var score = scored.score
        if (ai != null && !ai.isInvoice) score = min(score, 20)

        // Fill in derivable values for the register (after scoring, so they don't inflate it)
        var subtotal = fields.subtotal
        var tax = fields.tax
        val amount = fields.amount
        if (amount != null && subtotal == null && tax != null && tax < amount) {
            subtotal = Math.round((amount - tax) * 100) / 100.0
            notes += "Subtotal not printed — calculated as Amount − Tax."
        }
        if (amount != null && subtotal != null && tax == null && abs(amount - subtotal) < 0.005) {
            tax = 0.0
        }

        if (amount == null) notes += "The total amount could not be found — enter it manually or retake the photo."
        if (fields.date == null) notes += "Invoice date not found — please pick the date."
        if (category == null) notes += "Expense type could not be determined — please choose one."
        if (amount != null && score < 85) {
            val reasons = scored.findings.filter { !it.positive }.take(3)
                .joinToString("; ") { it.text.replaceFirstChar { c -> c.lowercase() } }
            if (reasons.isNotBlank()) notes += "Amount confidence is ${bandOf(score).label.lowercase()} because: $reasons."
        }

        val read = FieldValues(
            invoiceNo = fields.invoiceNo.orEmpty(),
            date = fields.date?.toString().orEmpty(),
            vendorName = fields.vendorName.orEmpty(),
            currency = fields.currency.orEmpty(),
            subtotal = AmountParser.plain(subtotal),
            tax = AmountParser.plain(tax),
            amount = AmountParser.plain(amount),
        )
        return ReviewDraft(
            imageUri = Uri.fromFile(file).toString(),
            pendingFilePath = file.absolutePath,
            invoiceNo = read.invoiceNo,
            date = read.date,
            vendorName = read.vendorName,
            currency = read.currency,
            subtotal = read.subtotal,
            tax = read.tax,
            amount = read.amount,
            read = read,
            category = category,
            aiCategory = category,
            categoryReason = categoryReason,
            amountConfidence = score,
            findings = scored.findings,
            comment = notes.distinct().joinToString(" ").ifBlank { "All key fields were read clearly." },
            engine = engine,
        )
    }

    /** A temporary copy of a saved image (unchanged), for reading it again. Delete it afterwards. */
    fun copyForRetry(source: Uri): File = copyToPending(source, ImageStyle.COLOUR)

    private fun copyToPending(source: Uri, style: ImageStyle): File {
        val dir = File(context.filesDir, PENDING_DIR).apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("the scanned image could not be opened")
        input.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
        if (style == ImageStyle.BLACK_WHITE) toGrayscale(file)
        return file
    }

    /** Grayscale with a mild contrast boost (keeps the original if the image can't be decoded). */
    private fun toGrayscale(file: File) {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val gray = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.15f
        val shift = 128f * (1 - contrast)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, shift,
                    0f, contrast, 0f, 0f, shift,
                    0f, 0f, contrast, 0f, shift,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        Canvas(gray).drawBitmap(original, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        original.recycle()
        file.outputStream().use { gray.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        gray.recycle()
    }

    companion object {
        const val PENDING_DIR = "pending"

        /** Removes abandoned scans older than a day. */
        fun cleanupPending(context: Context) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(context.filesDir, PENDING_DIR).listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        }
    }
}
