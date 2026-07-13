package com.pdfapp

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Extraction of the PDF URI from "Open with" / share-sheet launch intents. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingIntentTest {
    private val uri: Uri = Uri.parse("content://com.example.files/document/42")

    @Test
    fun view_intent_yields_its_data_uri() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        assertThat(intent.pdfUri()).isEqualTo(uri)
    }

    @Test
    fun send_intent_yields_the_stream_extra() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
        assertThat(intent.pdfUri()).isEqualTo(uri)
    }

    @Test
    fun launcher_intent_yields_null() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertThat(intent.pdfUri()).isNull()
    }

    @Test
    fun view_intent_without_data_yields_null() {
        assertThat(Intent(Intent.ACTION_VIEW).pdfUri()).isNull()
    }

    @Test
    fun send_intent_without_stream_yields_null() {
        assertThat(Intent(Intent.ACTION_SEND).setType("application/pdf").pdfUri()).isNull()
    }
}
