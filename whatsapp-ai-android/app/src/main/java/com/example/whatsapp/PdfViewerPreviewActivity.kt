package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.chatscreen.NightPdfViewerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

class PdfViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val pdf = File(cacheDir, "night-pdf-preview.pdf")
        if (!pdf.isFile || pdf.length() == 0L) {
            createNightPdfPreview(pdf)
        }

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightPdfViewerScreen(
                    localPath = pdf.absolutePath,
                    onBack = { finish() },
                )
            }
        }
    }

    private fun createNightPdfPreview(output: File) {
        PDFBoxResourceLoader.init(applicationContext)
        PDDocument().use { document ->
            repeat(24) { index ->
                val pageNumber = index + 1
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 22f)
                    stream.newLineAtOffset(72f, 760f)
                    stream.showText("Night PDF preview")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 15f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.showText("Document validation page $pageNumber of 24")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 13f)
                    stream.newLineAtOffset(72f, 680f)
                    stream.showText(
                        if (pageNumber == 17) {
                            "Orion appears only on this page so Night search can jump here."
                        } else {
                            "This page verifies smooth local rendering without prebuilding every page bitmap."
                        }
                    )
                    stream.endText()
                }
            }
            document.save(output)
        }
    }
}
