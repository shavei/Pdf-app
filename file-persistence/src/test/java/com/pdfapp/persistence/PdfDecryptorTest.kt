package com.pdfapp.persistence

import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfDecryptorTest {
    private val decryptor = PdfDecryptor()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    /** A one-page PDF containing [SECRET_TEXT], encrypted with [PASSWORD]. */
    private fun encryptedPdf(): ByteArray =
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                content.newLineAtOffset(TEXT_X, TEXT_Y)
                content.showText(SECRET_TEXT)
                content.endText()
            }
            document.protect(
                StandardProtectionPolicy(PASSWORD, PASSWORD, AccessPermission()).apply {
                    encryptionKeyLength = KEY_LENGTH_BITS
                },
            )
            ByteArrayOutputStream().also(document::save).toByteArray()
        }

    @Test
    fun `correct password produces a renderable unencrypted copy with content intact`() {
        val destination = File.createTempFile("decrypted", ".pdf")
        try {
            val result =
                decryptor.decryptToFile(
                    ByteArrayInputStream(encryptedPdf()),
                    PASSWORD,
                    destination,
                )

            assertThat(result).isEqualTo(PdfDecryptor.Result.Unlocked(destination))
            // Loadable WITHOUT a password, with the original text preserved.
            PDDocument.load(destination).use { reopened ->
                assertThat(reopened.isEncrypted).isFalse()
                assertThat(PDFTextStripper().getText(reopened)).contains(SECRET_TEXT)
            }
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `wrong password reports WrongPassword and leaves no file behind`() {
        val destination = File.createTempFile("decrypted", ".pdf")
        try {
            val result =
                decryptor.decryptToFile(
                    ByteArrayInputStream(encryptedPdf()),
                    "not-the-password",
                    destination,
                )

            assertThat(result).isEqualTo(PdfDecryptor.Result.WrongPassword)
            assertThat(destination.exists()).isFalse()
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `source really was encrypted - loading without password fails`() {
        val bytes = encryptedPdf()

        val unprotected = runCatching { PDDocument.load(ByteArrayInputStream(bytes)).close() }

        assertThat(unprotected.isFailure).isTrue()
    }

    private companion object {
        const val PASSWORD = "hunter2"
        const val SECRET_TEXT = "TOP-SECRET-CONTENT"
        const val FONT_SIZE = 12f
        const val TEXT_X = 72f
        const val TEXT_Y = 700f
        const val KEY_LENGTH_BITS = 128
    }
}
