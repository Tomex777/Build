package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import com.example.whatsapp.presentation.reader.mihon.MihonLoadedChapter
import com.example.whatsapp.presentation.reader.mihon.MihonPageSpec
import com.example.whatsapp.presentation.reader.mihon.NightMihonArchiveLoader
import com.example.whatsapp.presentation.reader.mihon.NightMihonReaderScreen
import com.example.whatsapp.presentation.reader.mihon.decodeMihonPages
import com.example.whatsapp.presentation.reader.mihon.encodeMihonPages
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NightMihonReaderActivity :
    ComponentActivity() {

    private var volumeKeyHandler: ((Boolean) -> Boolean)? = null

    internal fun setVolumeKeyHandler(
        handler: ((Boolean) -> Boolean)?,
    ) {
        volumeKeyHandler = handler
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val down = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> true
                KeyEvent.KEYCODE_VOLUME_UP -> false
                else -> null
            }
            if (down != null && volumeKeyHandler?.invoke(down) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val archivePath = intent.getStringExtra(EXTRA_ARCHIVE_PATH)
        val title = intent
            .getStringExtra(EXTRA_TITLE)
            .orEmpty()
            .ifBlank { "Manga" }

        setContent {
            WhatsappTheme(darkTheme = true) {
                if (!archivePath.isNullOrBlank()) {
                    ArchiveReaderEntry(
                        archivePath = archivePath,
                        displayName = title,
                        onBack = { finish() },
                    )
                } else {
                    val chapter = intent
                        .getStringExtra(EXTRA_CHAPTER)
                        .orEmpty()
                    val pages = decodeMihonPages(
                        intent.getStringExtra(EXTRA_PAGES),
                    )
                    val initialPage = intent.getIntExtra(
                        EXTRA_INITIAL_PAGE,
                        0,
                    )
                    val readerKey = intent
                        .getStringExtra(EXTRA_READER_KEY)
                        .orEmpty()
                        .ifBlank { title }
                    val progressKey = intent
                        .getStringExtra(EXTRA_PROGRESS_KEY)
                        ?.takeIf { it.isNotBlank() }

                    NightMihonReaderScreen(
                        mangaTitle = title,
                        chapterTitle = chapter,
                        pages = pages,
                        initialPage = initialPage,
                        readerKey = readerKey,
                        progressKey = progressKey,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "mihon.title"
        private const val EXTRA_CHAPTER = "mihon.chapter"
        private const val EXTRA_PAGES = "mihon.pages"
        private const val EXTRA_INITIAL_PAGE = "mihon.initialPage"
        private const val EXTRA_READER_KEY = "mihon.readerKey"
        private const val EXTRA_PROGRESS_KEY = "mihon.progressKey"
        private const val EXTRA_ARCHIVE_PATH = "mihon.archivePath"

        fun intent(
            context: Context,
            title: String,
            chapter: String,
            pages: List<MihonPageSpec>,
            initialPage: Int = 0,
            readerKey: String = title,
            progressKey: String? = null,
        ): Intent =
            Intent(
                context,
                NightMihonReaderActivity::class.java,
            )
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_CHAPTER, chapter)
                .putExtra(EXTRA_PAGES, encodeMihonPages(pages))
                .putExtra(EXTRA_INITIAL_PAGE, initialPage)
                .putExtra(EXTRA_READER_KEY, readerKey)
                .apply {
                    progressKey?.let { putExtra(EXTRA_PROGRESS_KEY, it) }
                }

        fun archiveIntent(
            context: Context,
            localPath: String,
            displayName: String,
        ): Intent =
            Intent(
                context,
                NightMihonReaderActivity::class.java,
            )
                .putExtra(EXTRA_TITLE, displayName)
                .putExtra(EXTRA_ARCHIVE_PATH, localPath)
    }
}

@Composable
private fun ArchiveReaderEntry(
    archivePath: String,
    displayName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var chapter by remember(archivePath) {
        mutableStateOf<MihonLoadedChapter?>(null)
    }
    var error by remember(archivePath) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(archivePath) {
        NightMihonArchiveLoader
            .load(
                context = context.applicationContext,
                archivePath = archivePath,
                displayName = displayName,
            )
            .onSuccess {
                chapter = it
                error = null
            }
            .onFailure {
                error = it.message ?: "Could not open this manga archive."
            }
    }

    val loaded = chapter
    when {
        loaded != null -> NightMihonReaderScreen(
            mangaTitle = loaded.mangaTitle,
            chapterTitle = loaded.chapterTitle,
            pages = loaded.pages,
            readerKey = loaded.progressKey,
            progressKey = loaded.progressKey,
            onBack = onBack,
        )

        error != null -> MihonReaderStatus(
            message = error ?: "Could not open this manga archive.",
        )

        else -> MihonReaderStatus(message = "Opening chapter…")
    }
}

@Composable
private fun MihonReaderStatus(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            color = ComposeColor.White,
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

        val archive =
            File(
                cacheDir,
                "mihon-reader-preview.cbz",
            )

        if (!archive.exists() || archive.length() == 0L) {
            val bitmap =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.bilal,
                )

            ZipOutputStream(
                FileOutputStream(archive),
            ).use { zip ->
                repeat(8) { index ->
                    zip.putNextEntry(
                        ZipEntry(
                            (index + 1)
                                .toString()
                                .padStart(3, '0') +
                                ".jpg",
                        ),
                    )
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        zip,
                    )
                    zip.closeEntry()
                }
            }
            bitmap.recycle()
        }

        setContent {
            WhatsappTheme(darkTheme = true) {
                ArchiveReaderEntry(
                    archivePath = archive.absolutePath,
                    displayName =
                        "Chainsaw Man - Chapter 173.cbz",
                    onBack = {},
                )
            }
        }
    }
}
