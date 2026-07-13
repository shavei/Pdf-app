package com.pdfapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.pdfapp.ui.PdfEditorScreen
import com.pdfapp.ui.theme.SignetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignetTheme {
                Surface {
                    PdfEditorScreen()
                }
            }
        }
    }
}
