package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chatscreen.NightPdfEditorScreen
import com.example.whatsapp.presentation.chatscreen.NightPdfViewerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

class PdfEditorPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val original = File(filesDir, "night-pdf-editor-original.pdf")
        val draft = File(filesDir, "night-pdf-editor-draft.pdf")
        createPreviewPdf(original)
        original.copyTo(draft, overwrite = true)
        getSharedPreferences("night_pdf_editor_preview", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        setContent {
            WhatsappTheme(darkTheme = true) {
                var caption by remember { mutableStateOf("") }
                var sentPath by remember { mutableStateOf<String?>(null) }

                val sent = sentPath
                if (sent == null) {
                    NightPdfEditorScreen(
                        localPath = draft.absolutePath,
                        fileName = "Night PDF editor.pdf",
                        caption = caption,
                        onCaptionChange = { caption = it },
                        onCancel = { finish() },
                        onPreparedSend = { path, name ->
                            getSharedPreferences("night_pdf_editor_preview", MODE_PRIVATE)
                                .edit()
                                .putBoolean("sent", true)
                                .putString("path", path)
                                .putString("name", name)
                                .putString("caption", caption)
                                .apply()
                            sentPath = path
                        },
                    )
                } else {
                    NightPdfViewerScreen(
                        localPath = sent,
                        displayName = "Night PDF editor.pdf",
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    private fun createPreviewPdf(output: File) {
        PDFBoxResourceLoader.init(applicationContext)
        PDDocument().use { document ->
            repeat(3) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 22f)
                    stream.newLineAtOffset(72f, 760f)
                    stream.showText("Night PDF editor")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.showText("Editable validation page " + (index + 1) + " of 3")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 680f)
                    stream.showText("The original file must stay byte-for-byte unchanged.")
                    stream.endText()
                }
            }
            document.save(output)
        }
    }
}
