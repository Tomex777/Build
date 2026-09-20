package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.reader.mihon.MihonPageSpec
import com.example.whatsapp.presentation.reader.mihon.NightMihonReaderScreen
import com.example.whatsapp.presentation.reader.mihon.decodeMihonPages
import com.example.whatsapp.presentation.reader.mihon.encodeMihonPages
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream

class NightMihonReaderActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val title =
            intent
                .getStringExtra(EXTRA_TITLE)
                .orEmpty()
                .ifBlank { "Manga" }

        val chapter =
            intent
                .getStringExtra(EXTRA_CHAPTER)
                .orEmpty()

        val pages =
            decodeMihonPages(
                intent.getStringExtra(EXTRA_PAGES),
            )

        val initialPage =
            intent.getIntExtra(
                EXTRA_INITIAL_PAGE,
                0,
            )

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMihonReaderScreen(
                    mangaTitle = title,
                    chapterTitle = chapter,
                    pages = pages,
                    initialPage = initialPage,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE =
            "mihon.title"
        private const val EXTRA_CHAPTER =
            "mihon.chapter"
        private const val EXTRA_PAGES =
            "mihon.pages"
        private const val EXTRA_INITIAL_PAGE =
            "mihon.initialPage"

        fun intent(
            context: Context,
            title: String,
            chapter: String,
            pages: List<MihonPageSpec>,
            initialPage: Int = 0,
        ): Intent =
            Intent(
                context,
                NightMihonReaderActivity::class.java,
            )
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_CHAPTER, chapter)
                .putExtra(
                    EXTRA_PAGES,
                    encodeMihonPages(pages),
                )
                .putExtra(
                    EXTRA_INITIAL_PAGE,
                    initialPage,
                )
    }
}

class MihonReaderPreviewActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val preview =
            File(
                cacheDir,
                "mihon-reader-preview.jpg",
            )

        if (!preview.exists()) {
            val bitmap =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.bilal,
                )

            FileOutputStream(preview).use {
                output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    output,
                )
            }
            bitmap.recycle()
        }

        val pages =
            List(8) { index ->
                MihonPageSpec(
                    index = index,
                    source = preview.absolutePath,
                )
            }

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightMihonReaderScreen(
                    mangaTitle = "Chainsaw Man",
                    chapterTitle = "Chapter 173",
                    pages = pages,
                    initialPage = 0,
                    onBack = {},
                )
            }
        }
    }
}
