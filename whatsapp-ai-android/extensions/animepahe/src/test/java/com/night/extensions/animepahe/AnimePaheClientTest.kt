package com.night.extensions.animepahe

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnimePaheClientTest {
    private lateinit var server: MockWebServer
    private lateinit var session: FakeSession
    private lateinit var client: AnimePaheClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session =
            FakeSession(
                baseUrl = server.url("/").toString().trimEnd('/'),
            )
        client =
            AnimePaheClient(
                session = session,
                client = OkHttpClient.Builder().build(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchParsesAnimeCardsAndUsesSearchRoute() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "data": [{
                        "id": 12,
                        "session": "bleach-session",
                        "title": "Bleach",
                        "type": "TV",
                        "year": 2004,
                        "episodes": 366,
                        "status": "Finished",
                        "poster": "https://img.example/bleach.jpg"
                      }]
                    }
                    """.trimIndent()
                )
        )

        val result = client.search("Bleach")

        assertEquals(1, result.size)
        assertEquals("Bleach", result.single().title)
        assertEquals("bleach-session", result.single().session)
        assertEquals("366", result.single().episodes)

        val request = server.takeRequest()
        assertEquals("/api?m=search&q=Bleach", request.path)
    }

    @Test
    fun detailsParsesAnimePageMetadata() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <html>
                      <body>
                        <div class="title-wrapper">
                          <h1><span>Bleach</span></h1>
                        </div>
                        <div class="anime-poster">
                          <a href="/posters/bleach.jpg">Poster</a>
                        </div>
                        <div class="anime-summary">
                          Soul Reapers defend the living world.
                        </div>
                        <div class="col-sm-4 anime-info">
                          <p>Type: <a>TV</a></p>
                          <p>Status: <a>Finished Airing</a></p>
                          <p>Studios: Pierrot</p>
                          <p>Season: <a>Fall 2004</a></p>
                        </div>
                        <div class="anime-genre">
                          <ul>
                            <li>Action</li>
                            <li>Supernatural</li>
                          </ul>
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
        )

        val details =
            client.details(
                animeSession = "bleach-session",
                titleHint = "Bleach",
            )

        assertEquals("Bleach", details.title)
        assertEquals("TV", details.type)
        assertEquals(
            "Finished Airing",
            details.status,
        )
        assertEquals(
            "Pierrot",
            details.studios,
        )
        assertEquals(
            "Fall 2004",
            details.season,
        )
        assertEquals(
            listOf(
                "Action",
                "Supernatural",
            ),
            details.genres,
        )
        assertTrue(
            details.poster.orEmpty()
                .endsWith(
                    "/posters/bleach.jpg"
                )
        )

        val request =
            server.takeRequest()
        assertEquals(
            "/anime/bleach-session",
            request.path,
        )
    }

    @Test
    fun releasesParseEpisodePageAndPagination() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "current_page": 2,
                      "last_page": 4,
                      "data": [{
                        "session": "episode-session",
                        "episode": 13,
                        "title": "The Blade",
                        "snapshot": "https://img.example/13.jpg",
                        "duration": "24m",
                        "created_at": "2026-09-01"
                      }]
                    }
                    """.trimIndent()
                )
        )

        val result =
            client.episodes(
                animeSession = "bleach-session",
                page = 2,
            )

        assertEquals(2, result.currentPage)
        assertEquals(4, result.lastPage)
        assertEquals("13", result.items.single().number)
        assertEquals("episode-session", result.items.single().session)

        val request = server.takeRequest()
        assertTrue(
            request.path.orEmpty().contains("m=release")
        )
        assertTrue(
            request.path.orEmpty().contains("id=bleach-session")
        )
        assertTrue(
            request.path.orEmpty().contains("sort=episode_asc")
        )
        assertTrue(
            request.path.orEmpty().contains("page=2")
        )
    }

    @Test
    fun playPageParsesRealSourceMetadataWithoutHardcodedSourceRoles() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <html>
                      <body>
                        <button
                          data-src="https://kwik.example/e/abc"
                          data-resolution="1080"
                          data-audio="jpn"
                          data-fansub="SubsPlease"></button>
                        <div id="pickDownload">
                          <a href="https://pahe.win/a1">1080p</a>
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
        )

        val sources =
            client.sources(
                animeSession = "anime-session",
                episodeSession = "episode-session",
            )

        assertEquals(1, sources.size)
        assertEquals(1080, sources.single().resolution)
        assertEquals("jpn", sources.single().audio)
        assertEquals("SubsPlease", sources.single().fansub)
        assertEquals(
            "https://pahe.win/a1",
            sources.single().downloadPageUrl,
        )

        val request = server.takeRequest()
        assertEquals(
            "/play/anime-session/episode-session",
            request.path,
        )
    }

    @Test
    fun providerChallengeRequiresManualNightBrowserVerification() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Just a moment... Cloudflare")
        )

        val error =
            assertThrows(
                AnimePaheVerificationRequired::class.java
            ) {
                client.search("Bleach")
            }

        assertTrue(error.verificationUrl.contains("/api?"))
        assertEquals(
            server.hostName,
            error.host,
        )
    }

    @Test
    fun preferredQualityAndAudioSelectProviderSourceDeterministically() {
        session.qualityValue = "720"
        session.audioValue = "sub"

        val selected =
            client.selectPreferredSource(
                listOf(
                    AnimePaheSource(
                        kwikUrl = "https://kwik.example/1080",
                        downloadPageUrl = "https://pahe.win/1080",
                        resolution = 1080,
                        audio = "jpn",
                        fansub = "A",
                    ),
                    AnimePaheSource(
                        kwikUrl = "https://kwik.example/720",
                        downloadPageUrl = "https://pahe.win/720",
                        resolution = 720,
                        audio = "jpn",
                        fansub = "B",
                    ),
                    AnimePaheSource(
                        kwikUrl = "https://kwik.example/dub",
                        downloadPageUrl = "https://pahe.win/dub",
                        resolution = 720,
                        audio = "eng",
                        fansub = "C",
                    ),
                )
            )

        assertEquals(720, selected.resolution)
        assertEquals("jpn", selected.audio)
        assertEquals("B", selected.fansub)
    }

    @Test
    fun qualityFallbackMatchesPaheBatcherNearestLowerRule() {
        session.qualityValue = "360"
        session.audioValue = "sub"

        val selected =
            client.selectPreferredSource(
                listOf(
                    AnimePaheSource(
                        kwikUrl = "https://kwik.example/1080",
                        downloadPageUrl = null,
                        resolution = 1080,
                        audio = "jpn",
                        fansub = "A",
                    ),
                    AnimePaheSource(
                        kwikUrl = "https://kwik.example/720",
                        downloadPageUrl = null,
                        resolution = 720,
                        audio = "jpn",
                        fansub = "B",
                    ),
                )
            )

        assertEquals(720, selected.resolution)
        assertEquals("B", selected.fansub)
    }

    private class FakeSession(
        private val baseUrl: String,
    ) : AnimePaheSession {
        var qualityValue = "auto"
        var audioValue = "sub"
        private val cookies = linkedMapOf<String, String>()

        override fun baseUrl(): String = baseUrl

        override fun quality(): String = qualityValue

        override fun audio(): String = audioValue

        override fun parallelDownloads(): Int = 2

        override fun userAgent(): String = "Night AnimePahe Test"

        override fun cookieForUrl(url: String): String =
            cookies.values.joinToString("; ")

        override fun saveBrowserSession(
            host: String,
            cookieHeader: String,
        ) {
            cookies[host] = cookieHeader
        }

        override fun saveConfiguration(values: JSONObject) {
            values.optString("quality")
                .takeIf { it.isNotBlank() }
                ?.let { qualityValue = it }
            values.optString("audio")
                .takeIf { it.isNotBlank() }
                ?.let { audioValue = it }
        }

        override fun absorbResponseCookies(response: Response) {
            // Unit tests do not need persistent Set-Cookie handling.
        }
    }
}
