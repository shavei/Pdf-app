package com.pdfapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Initialises PdfBox-Android once at process start. PdfBox loads font metrics
 * and other resources via this loader; without this call, flattening text would
 * fail at runtime. Flagged in the architecture as a must-not-miss step.
 */
class PdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
