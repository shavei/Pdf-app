package com.pdfapp.persistence

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.OutputStream

/**
 * Writes a (flattened) [PDDocument] to a destination. Kept free of flatten logic
 * so it stays a thin, easily tested I/O boundary.
 */
class PdfSaver {
    /** Write [document] to an arbitrary [output] stream, which is closed afterwards. */
    fun writeTo(
        document: PDDocument,
        output: OutputStream,
    ) {
        output.use { document.save(it) }
    }

    /**
     * Save [document] to a user-chosen SAF [uri]. No file paths or storage
     * permissions are used — the URI comes from the system document picker.
     */
    fun saveToUri(
        resolver: ContentResolver,
        uri: Uri,
        document: PDDocument,
    ) {
        val output =
            resolver.openOutputStream(uri)
                ?: error("Unable to open output stream for $uri")
        writeTo(document, output)
    }
}
