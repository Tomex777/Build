package com.tomex.aether

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.max

class FeedRepository(
    private val reddit: RedditClient,
    private val ai: AiGatewayClient,
) {
    private val cursors = mutableMapOf<String, String?>()
    private val exhausted = mutableSetOf<String>()
    private var rotation = 0

    fun reset() {
        cursors.clear()
        exhausted.clear()
        rotation = 0
    }

    suspend fun nextBatch(
        category: FeedCategory,
        settings: AppSettings,
        seenIds: Set<String>,
        currentIds: Set<String>,
        searchQuery: String = "",
    ): List<MemePost> = coroutineScope {
        val subs = category.subreddits.map { it.removePrefix("r/").trim() }.filter { it.isNotBlank() }
        if (subs.isEmpty()) return@coroutineScope emptyList()

        val effectiveTerms = (category.tags + searchQuery.trim().takeIf { it.isNotBlank() }.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
        val shuffled = subs.sortedBy { stableOrder(it, rotation) }
        val selected = shuffled.filterNot { exhausted.contains(cursorKey(it, settings.sortMode, effectiveTerms)) }.take(4)
            .ifEmpty {
                exhausted.clear()
                shuffled.take(4)
            }
        rotation++

        val results = selected.map { sub ->
            async {
                val key = cursorKey(sub, settings.sortMode, effectiveTerms)
                key to runCatching {
                    reddit.fetchSubreddit(
                        subreddit = sub,
                        sort = settings.sortMode,
                        includeVideos = settings.includeVideos,
                        after = cursors[key],
                        limit = 40,
                        searchTerms = effectiveTerms,
                    )
                }
            }
        }.awaitAll()

        val successful = results.mapNotNull { (key, result) ->
            result.getOrNull()?.also { page ->
                cursors[key] = page.after
                if (page.after == null) exhausted += key
            }
        }
        if (successful.isEmpty()) {
            results.firstNotNullOfOrNull { it.second.exceptionOrNull() }?.let { throw it }
        }

        val pages = successful.flatMap { it.posts }
        val unique = pages
            .asSequence()
            .filter { it.id !in seenIds && it.id !in currentIds }
            .distinctBy { it.id }
            .toList()

        blendImageFirst(unique, settings.includeVideos)
    }

    suspend fun validateSubreddit(name: String): SubredditCandidate? = reddit.validateSubreddit(name)

    suspend fun comments(postId: String): List<RedditComment> = reddit.fetchComments(postId)

    suspend fun discover(prompt: String, aiBaseUrl: String): List<SubredditCandidate> {
        val fallbackQueries = prompt
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(5)
            .ifEmpty { listOf(prompt) }

        val intent = if (aiBaseUrl.isNotBlank()) {
            runCatching { ai.discoverIntent(aiBaseUrl, prompt) }.getOrNull()
        } else null

        val queries = ((intent?.queries ?: emptyList()) + prompt + fallbackQueries)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        val discovered = queries.flatMap { q ->
            runCatching { reddit.searchSubreddits(q, 12) }.getOrDefault(emptyList())
        }.distinctBy { it.name.lowercase() }.take(20)

        return coroutineScope {
            discovered.map { candidate ->
                async {
                    runCatching {
                        val verified = reddit.validateSubreddit(candidate.name) ?: return@runCatching null
                        reddit.profileCandidate(verified, prompt)
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull().sortedByDescending { it.matchScore }
    }

    suspend fun memeAi(baseUrl: String, post: MemePost, action: AiAction): AiResult = ai.memeAction(baseUrl, post, action)

    suspend fun vibe(baseUrl: String, mood: String, categories: List<FeedCategory>): VibeResult =
        ai.vibe(baseUrl, mood, categories)

    private fun blendImageFirst(posts: List<MemePost>, includeVideos: Boolean): List<MemePost> {
        val images = posts.filter { it.kind == MediaKind.IMAGE }.shuffled()
        val gifs = posts.filter { it.kind == MediaKind.GIF }.shuffled()
        val videos = if (includeVideos) posts.filter { it.kind == MediaKind.VIDEO }.shuffled() else emptyList()
        val result = ArrayList<MemePost>(posts.size)
        var i = 0; var g = 0; var v = 0
        var cycle = 0
        while (i < images.size || g < gifs.size || v < videos.size) {
            repeat(2) { if (i < images.size) result += images[i++] }
            if (g < gifs.size) result += gifs[g++]
            if (i < images.size) result += images[i++]
            if (includeVideos && cycle % 2 == 1 && v < videos.size) result += videos[v++]
            cycle++
            if (cycle > max(posts.size, 1) * 2) break
        }
        while (g < gifs.size) result += gifs[g++]
        while (includeVideos && v < videos.size) result += videos[v++]
        return result.distinctBy { it.id }
    }

    private fun cursorKey(sub: String, sort: SortMode, tags: List<String>) = sub + "|" + sort.name + "|" + tags.joinToString(",")
    private fun stableOrder(value: String, salt: Int): Int = value.hashCode() xor (salt * 1103515245)
}
