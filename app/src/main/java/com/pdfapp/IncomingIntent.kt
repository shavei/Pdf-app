package com.pdfapp

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Extracts the PDF [Uri] carried by an incoming intent: the data URI of an
 * `ACTION_VIEW` ("Open with" sheet) or the `EXTRA_STREAM` of an `ACTION_SEND`
 * (share sheet). Returns null for any other launch, such as the plain
 * launcher intent.
 */
fun Intent.pdfUri(): Uri? =
    when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND ->
            IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
