package com.pdfapp.persistence

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.InputStream

/**
 * Unlocks password-protected PDFs so they can be rendered.
 *
 * [android.graphics.pdf.PdfRenderer] cannot open encrypted files (it throws
 * `SecurityException`), so the viewer decrypts to a private cache copy with
 * PdfBox and renders that. The caller owns the destination file's lifecycle
 * and must delete it when the document closes.
 */
class PdfDecryptor {
    sealed interface Result {
        /** [file] now holds an unencrypted copy of the document. */
        data class Unlocked(val file: File) : Result

        /** The password did not open the document. */
        data object WrongPassword : Result
    }

    /**
     * Decrypt the PDF in [input] using [password] and write an unencrypted
     * copy to [destination]. IO + full parse: call on a background dispatcher.
     */
    fun decryptToFile(
        input: InputStream,
        password: String,
        destination: File,
    ): Result =
        try {
            PDDocument.load(input, password).use { document ->
                document.isAllSecurityToBeRemoved = true
                document.save(destination)
            }
            Result.Unlocked(destination)
        } catch (_: InvalidPasswordException) {
            destination.delete()
            Result.WrongPassword
        }
}
