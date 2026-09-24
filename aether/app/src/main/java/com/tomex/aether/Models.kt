package com.tomex.aether

enum class MediaKind { IMAGE, GIF, VIDEO }
enum class SortMode(val wire: String) { HOT("hot"), TOP("top"), NEW("new") }
enum class AiAction { CAPTION, EXPLAIN, TAGS, SIMILAR }

data class FeedCategory(
    val id: String,
    val name: String,
    val subreddits: List<String>,
    val tags: List<String> = emptyList(),
)

data class MemePost(
    val id: String,
    val title: String,
    val subreddit: String,
    val permalink: String,
    val sourceUrl: String,
    val mediaUrl: String,
    val posterUrl: String? = null,
    val kind: MediaKind,
    val score: Int = 0,
    val comments: Int = 0,
    val createdUtc: Long = 0L,
)

data class RedditComment(
    val id: String,
    val author: String,
    val body: String,
    val score: Int,
    val createdUtc: Long,
    val depth: Int,
)

data class SubredditCandidate(
    val name: String,
    val title: String,
    val subscribers: Long,
    val description: String,
    val verified: Boolean,
    val over18: Boolean,
    val mediaFit: Float = 0f,
    val recentPosts: Int = 0,
    val matchScore: Float = 0f,
)

data class AppSettings(
    val includeVideos: Boolean = false,
    val autoplayVideos: Boolean = false,
    val sortMode: SortMode = SortMode.HOT,
    val aiBaseUrl: String = "",
    val redditClientId: String = "",
)

data class DiscoveryIntent(
    val categoryName: String = "",
    val queries: List<String> = emptyList(),
)

data class AiResult(
    val action: AiAction,
    val text: String,
    val tags: List<String> = emptyList(),
)

fun defaultCategories(): List<FeedCategory> = listOf(
    FeedCategory(
        id = "meme",
        name = "Memes",
        subreddits = listOf("memes", "dankmemes", "funny", "me_irl", "shitposting", "holup", "bonehurtingjuice", "Unexpected")
    ),
    FeedCategory(
        id = "anime",
        name = "Anime",
        subreddits = listOf("animemes", "anime_irl", "wholesomeanimemes", "goodanimemes", "anime")
    ),
    FeedCategory(
        id = "gaming",
        name = "Gaming",
        subreddits = listOf("gaming", "gamingmemes", "pcmasterrace", "Minecraft", "Eldenring", "NintendoSwitch")
    ),
    FeedCategory(
        id = "wholesome",
        name = "Wholesome",
        subreddits = listOf("wholesomememes", "aww", "MadeMeSmile", "rarepuppers", "AnimalsBeingDerps")
    ),
    FeedCategory(
        id = "science",
        name = "Science",
        subreddits = listOf("sciencememes", "physicsmemes", "chemistrymemes", "mathmemes", "ProgrammerHumor")
    ),
)
