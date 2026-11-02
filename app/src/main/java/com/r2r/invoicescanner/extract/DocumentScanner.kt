package com.r2r.invoicescanner.extract

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.r2r.invoicescanner.settings.ImageStyle

/**
 * Google's document scanner: camera capture with automatic edge detection,
 * crop to the document and perspective correction. The user can also adjust
 * the corners manually or import a photo from the gallery.
 *
 * Colour mode uses the basic scanner, which applies no filters, so the image
 * keeps its original colours. Black & white mode uses the full scanner, which
 * adds its filters and ML clean-up (stains, fingers); the app then converts the
 * result to grayscale.
 */
object DocumentScanner {
    fun client(style: ImageStyle): GmsDocumentScanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(
                if (style == ImageStyle.COLOUR) GmsDocumentScannerOptions.SCANNER_MODE_BASE
                else GmsDocumentScannerOptions.SCANNER_MODE_FULL
            )
            .build()
    )
}
