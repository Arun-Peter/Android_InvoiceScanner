package com.r2r.invoicescanner.extract

import kotlin.math.abs
import kotlin.math.max

/** A line of text found by OCR with its bounding box (pixels). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = max(1, bottom - top)
}

/**
 * OCR engines often return the label ("TOTAL") and its value ("1,520.00") as
 * separate blocks. This rebuilds receipt-style rows by grouping lines whose
 * vertical centres line up, then ordering them left to right.
 */
object RowBuilder {
    fun buildRows(lines: List<OcrLine>): List<String> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.centerY }.average()
                val rowHeight = row.map { it.height }.average()
                if (abs(line.centerY - rowCenter) < 0.5 * max(line.height.toDouble(), rowHeight)) {
                    row.add(line)
                    continue
                }
            }
            rows.add(mutableListOf(line))
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("   ") { it.text.trim() } }
    }
}
