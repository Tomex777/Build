package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericDownloadMetadataTest {
    @Test fun arbitraryExtensionsArePreservedWithoutAMediaAllowlist() {
        listOf("apk", "epub", "zip", "7z", "rar", "cbz", "pdf", "docx", "xlsx", "pptx", "srt", "ttf", "js", "db", "rom", "blorp").forEach { extension ->
            assertEquals("sample.$extension", DownloadFileMetadata.filename(null, null, "https://files.example/sample.$extension?token=secret", null))
            assertEquals(extension, AnnieDownloadNaming.extensionFor(null, "https://files.example/sample.$extension"))
        }
    }

    @Test fun dispositionThenPackageThenResolvedUrlThenMimeThenFallback() {
        assertEquals("server.epub", DownloadFileMetadata.filename("attachment; filename=\"server.epub\"", "package.zip", "https://files.example/source.pdf", "application/pdf"))
        assertEquals("package.blorp", DownloadFileMetadata.filename(null, "package.blorp", "https://files.example/source.pdf", "application/pdf"))
        assertEquals("source.pdf", DownloadFileMetadata.filename(null, null, "https://files.example/source.pdf", "application/octet-stream"))
        assertEquals("download.pdf", DownloadFileMetadata.filename(null, null, "https://files.example/", "application/pdf"))
        assertEquals("download.bin", DownloadFileMetadata.filename(null, null, "https://files.example/", null))
    }

    @Test fun utf8DispositionAndSanitizingKeepMeaningfulExtension() {
        assertEquals("日本語+guide.pdf", DownloadFileMetadata.filename("attachment; filename=\"fallback.pdf\"; filename*=UTF-8''%E6%97%A5%E6%9C%AC%E8%AA%9E+guide.pdf", null, "https://files.example/", null))
        val name = DownloadFileMetadata.filename("attachment; filename=\"../../unsafe.blorp\"", null, "https://files.example/", null)
        assertFalse(name.contains('/'))
        assertEquals("blorp", DownloadFileMetadata.extension(name))
    }

    @Test fun longAndUnicodeNamesKeepTheirExtensionWithinFilesystemLimits() {
        assertEquals("sample.未知", DownloadFileMetadata.filename(null, "sample.未知", "https://files.example/", "application/pdf"))
        val name = DownloadFileMetadata.filename(null, "日本語".repeat(100) + ".blorp", "https://files.example/", null)
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(name.endsWith(".blorp"))
        assertFalse(name.contains('\uFFFD'))
    }

    @Test fun unknownMimeNeverPretendsToBeVideo() {
        assertEquals("application/octet-stream", DownloadFileMetadata.mime(null, null, "sample.blorp"))
        assertEquals("application/pdf", DownloadFileMetadata.mime("application/octet-stream", null, "sample.pdf"))
        assertEquals("application/vnd.android.package-archive", DownloadFileMetadata.mime(null, null, "sample.apk"))
        assertEquals("application/x-custom", DownloadFileMetadata.mime("application/x-custom; charset=binary", null, "sample.blorp"))
    }
}
