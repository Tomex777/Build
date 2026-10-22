package com.night.jigsaw

import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A small curated starter catalog from the existing NIGHT Play photo inventory.
 * Images are not bundled and never downloaded without a deliberate user tap.
 * Photo use is within a puzzle game, not a general Unsplash image library.
 * Source: https://unsplash.com/license
 */
data class CuratedPuzzlePhoto(
    val id: String, val title: String, val category: String, val asset: String
)

object CuratedPhotoCatalog {
    val photos=listOf(
        CuratedPuzzlePhoto("alpine-lake","Alpine Lake","Nature","photo-1470770841072-f978cf4d019e"),
        CuratedPuzzlePhoto("forest-light","Forest Light","Nature","photo-1441974231531-c6227db76b6e"),
        CuratedPuzzlePhoto("golden-coast","Golden Coast","Nature","photo-1507525428034-b723cf961d3e"),
        CuratedPuzzlePhoto("mountain-air","Mountain Air","Nature","photo-1501785888041-af3ef285b470"),
        CuratedPuzzlePhoto("curious-cat","Curious Cat","Animals","photo-1514888286974-6c03e2ca1dba"),
        CuratedPuzzlePhoto("golden-friend","Golden Friend","Animals","photo-1552053831-71594a27632d"),
        CuratedPuzzlePhoto("wild-fox","Wild Fox","Animals","photo-1474511320723-9a56873867b5"),
        CuratedPuzzlePhoto("paris-evening","Paris Evening","Places","photo-1502602898657-3e91760cbb34"),
        CuratedPuzzlePhoto("tokyo-nights","Tokyo Nights","Places","photo-1540959733332-eab4deabeeaf"),
        CuratedPuzzlePhoto("morning-in-agra","Morning in Agra","Places","photo-1548013146-72479768bada"),
        CuratedPuzzlePhoto("pink-blossoms","Pink Blossoms","Flowers","photo-1490750967868-88aa4486c946"),
        CuratedPuzzlePhoto("spring-bouquet","Spring Bouquet","Flowers","photo-1468327768560-75b778cbb551"),
        CuratedPuzzlePhoto("under-the-stars","Under the Stars","Sky","photo-1519681393784-d120267933ba"),
        CuratedPuzzlePhoto("last-light","Last Light","Sky","photo-1470252649378-9c29740c9fa8")
    )
}

/** Download one chosen image into the app's existing offline photo library.
 * The importer center-crops and owns its file. No remote playback dependency.
 */
class CuratedPhotoDownloader(private val store: PuzzleStore,
                            private val cacheDir: File) {
    fun download(photo: CuratedPuzzlePhoto): ImportedPuzzlePhoto {
        require(CuratedPhotoCatalog.photos.any { it.id==photo.id && it.asset==photo.asset })
        val url=URL("https://images.unsplash.com/"+photo.asset+
            "?fit=crop&crop=entropy&w=2400&h=1800&q=85&fm=jpg")
        require(url.protocol=="https" && url.host=="images.unsplash.com")
        val connection=(url.openConnection() as HttpURLConnection).apply {
            connectTimeout=14000
            readTimeout=24000
            instanceFollowRedirects=false
            setRequestProperty("Accept","image/jpeg")
            setRequestProperty("User-Agent","NIGHT-Jigsaw/0.1")
        }
        val file=File.createTempFile("jigsaw-curated-",".jpg",cacheDir)
        try {
            check(connection.responseCode==HttpURLConnection.HTTP_OK) {
                "Image unavailable. Check your connection and try again."
            }
            val size=connection.contentLengthLong
            check(size<=12_000_000 || size<0) { "Image too large." }
            check(connection.contentType?.startsWith("image/")==true) {
                "The photo provider did not return an image."
            }
            var received=0L
            connection.inputStream.use { incoming ->
                file.outputStream().use { saved ->
                    val buffer=ByteArray(16_384)
                    while (true) {
                        val count=incoming.read(buffer)
                        if (count<0) break
                        received+=count
                        check(received<=12_000_000) { "Image too large." }
                        saved.write(buffer,0,count)
                    }
                }
            }
            check(received>0) { "Empty image." }
            return store.importPhoto(Uri.fromFile(file),photo.title,"Unsplash")
        } finally {
            connection.disconnect()
            file.delete()
        }
    }
}
