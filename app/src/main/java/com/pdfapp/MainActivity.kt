package com.pdfapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pdfapp.ui.PdfEditorScreen
import com.pdfapp.ui.theme.PdfAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PdfEditorScreen()
                }
            }
        }
    }
}
