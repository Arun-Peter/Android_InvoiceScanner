package com.r2r.invoicescanner.extract

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** On-device text recognition (Google ML Kit, bundled model — works offline). */
object OcrEngine {

    data class OcrResult(val rows: List<String>) {
        val text: String get() = rows.joinToString("\n")
    }

    suspend fun read(context: Context, file: File): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).await()
            val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
            OcrResult(RowBuilder.buildRows(lines))
        } finally {
            recognizer.close()
        }
    }
}
