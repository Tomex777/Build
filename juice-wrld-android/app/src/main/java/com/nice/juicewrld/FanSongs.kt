package com.nice.juicewrld

enum class Mood(val label: String, val tagline: String) {
    ALL("All songs", "Every side of Juice"),
    LOVE("Love", "The soft side"),
    HEARTBREAK("Heartbreak", "When it hurts"),
    SAD("Sad", "For the heavy nights"),
    HYPE("Hype", "Turn it up"),
    CHILL("Chill", "Late night rotation"),
    FAVORITES("Favorites", "The ones you keep")
}

data class FanSong(
    val id: String,
    val title: String,
    val album: String,
    val year: String,
    val moods: Set<Mood>,
) {
    val mood: Mood get() = moods.first()
}

object FanSongs {
    private fun song(id: String, title: String, album: String, year: String, vararg mood: Mood) =
        FanSong(id, title, album, year, mood.toSet())

    // Editorial groupings are fan-curated listening moods, not official genre classifications.
    // All tracks listed below are released tracks; no downloads or copyrighted recordings bundled.
    val all: List<FanSong> = listOf(
        song("lucid-dreams", "Lucid Dreams", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK, Mood.SAD),
        song("all-girls-are-the-same", "All Girls Are the Same", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK),
        song("lean-wit-me", "Lean Wit Me", "Goodbye & Good Riddance", "2018", Mood.SAD, Mood.CHILL),
        song("black-and-white", "Black & White", "Goodbye & Good Riddance", "2018", Mood.HYPE),
        song("wasted", "Wasted", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK, Mood.HYPE),
        song("hurt-me", "Hurt Me", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK),
        song("im-still", "I'm Still", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK, Mood.SAD),
        song("end-of-the-road", "End of the Road", "Goodbye & Good Riddance", "2018", Mood.SAD),
        song("scared-of-love", "Scared of Love", "Goodbye & Good Riddance", "2018", Mood.LOVE, Mood.HEARTBREAK),
        song("candles", "Candles", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK, Mood.CHILL),
        song("ill-be-fine", "I'll Be Fine", "Goodbye & Good Riddance", "2018", Mood.CHILL),
        song("used-to", "Used To", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK, Mood.SAD),
        song("long-gone", "Long Gone", "Goodbye & Good Riddance", "2018", Mood.HEARTBREAK),
        song("armed-and-dangerous", "Armed and Dangerous", "Goodbye & Good Riddance", "2018", Mood.HYPE),
        song("legends", "Legends", "Too Soon..", "2018", Mood.SAD, Mood.CHILL),
        song("rich-and-blind", "Rich and Blind", "Too Soon..", "2018", Mood.SAD),
        song("fine-china", "Fine China", "WRLD ON DRUGS", "2018", Mood.LOVE, Mood.HYPE),
        song("jet-lag", "Jet Lag", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("astronauts", "Astronauts", "WRLD ON DRUGS", "2018", Mood.HYPE, Mood.CHILL),
        song("red-bentley", "Red Bentley", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("make-it-back", "Make It Back", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("oxy", "Oxy", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("7-am-freestyle", "7 AM Freestyle", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("different", "Different", "WRLD ON DRUGS", "2018", Mood.CHILL),
        song("shorty", "Shorty", "WRLD ON DRUGS", "2018", Mood.CHILL),
        song("realer-n-realer", "Realer N Realer", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("no-issue", "No Issue", "WRLD ON DRUGS", "2018", Mood.CHILL),
        song("wrld-on-drugs", "WRLD On Drugs", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("afterlife", "Afterlife", "WRLD ON DRUGS", "2018", Mood.CHILL),
        song("aint-livin-right", "Ain't Livin Right", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("hard-work-pays-off", "Hard Work Pays Off", "WRLD ON DRUGS", "2018", Mood.HYPE),
        song("empty", "Empty", "Death Race for Love", "2019", Mood.SAD, Mood.HEARTBREAK),
        song("maze", "Maze", "Death Race for Love", "2019", Mood.SAD, Mood.CHILL),
        song("hemotions", "HeMotions", "Death Race for Love", "2019", Mood.SAD),
        song("fast", "Fast", "Death Race for Love", "2019", Mood.SAD, Mood.CHILL),
        song("hear-me-calling", "Hear Me Calling", "Death Race for Love", "2019", Mood.LOVE, Mood.CHILL),
        song("robbery", "Robbery", "Death Race for Love", "2019", Mood.HEARTBREAK, Mood.SAD),
        song("flaws-and-sins", "Flaws and Sins", "Death Race for Love", "2019", Mood.LOVE),
        song("desire", "Desire", "Death Race for Love", "2019", Mood.LOVE),
        song("who-shot-cupid", "Who Shot Cupid?", "Death Race for Love", "2019", Mood.HEARTBREAK),
        song("ring-ring", "Ring Ring", "Death Race for Love", "2019", Mood.HEARTBREAK, Mood.HYPE),
        song("out-my-way", "Out My Way", "Death Race for Love", "2019", Mood.HYPE),
        song("syphilis", "Syphilis", "Death Race for Love", "2019", Mood.HYPE),
        song("the-bees-knees", "The Bees Knees", "Death Race for Love", "2019", Mood.HYPE),
        song("shes-the-one", "She's the One", "Death Race for Love", "2019", Mood.LOVE),
        song("make-believe", "Make Believe", "Death Race for Love", "2019", Mood.HEARTBREAK),
        song("big", "Big", "Death Race for Love", "2019", Mood.HYPE),
        song("feeling", "Feeling", "Death Race for Love", "2019", Mood.HYPE),
        song("on-god", "ON GOD", "Death Race for Love", "2019", Mood.HYPE),
        song("10-feet", "10 Feet", "Death Race for Love", "2019", Mood.CHILL),
        song("wont-let-go", "Won't Let Go", "Death Race for Love", "2019", Mood.LOVE),
        song("rider", "Rider", "Death Race for Love", "2019", Mood.HYPE),
        song("bandit", "Bandit", "Bandit", "2019", Mood.HYPE),
        song("righteous", "Righteous", "Legends Never Die", "2020", Mood.SAD, Mood.CHILL),
        song("wishing-well", "Wishing Well", "Legends Never Die", "2020", Mood.SAD),
        song("conversations", "Conversations", "Legends Never Die", "2020", Mood.SAD),
        song("bad-energy", "Bad Energy", "Legends Never Die", "2020", Mood.SAD),
        song("lifes-a-mess", "Life's a Mess", "Legends Never Die", "2020", Mood.LOVE, Mood.SAD),
        song("come-and-go", "Come & Go", "Legends Never Die", "2020", Mood.LOVE, Mood.HYPE),
        song("i-want-it", "I Want It", "Legends Never Die", "2020", Mood.LOVE),
        song("up-up-and-away", "Up Up and Away", "Legends Never Die", "2020", Mood.CHILL),
        song("tell-me-u-luv-me", "Tell Me U Luv Me", "Legends Never Die", "2020", Mood.LOVE, Mood.HEARTBREAK),
        song("man-of-the-year", "Man of the Year", "Legends Never Die", "2020", Mood.HYPE),
        song("smile", "Smile", "Legends Never Die", "2020", Mood.LOVE, Mood.HEARTBREAK),
        song("titanic", "Titanic", "Legends Never Die", "2020", Mood.SAD),
        song("blood-on-my-jeans", "Blood On My Jeans", "Legends Never Die", "2020", Mood.LOVE, Mood.CHILL),
        song("hate-the-other-side", "Hate the Other Side", "Legends Never Die", "2020", Mood.SAD),
        song("fighting-demons", "Fighting Demons", "Legends Never Die", "2020", Mood.SAD),
        song("screw-juice", "Screw Juice", "Legends Never Die", "2020", Mood.SAD, Mood.HYPE),
        song("stay-high", "Stay High", "Legends Never Die", "2020", Mood.CHILL),
        song("cant-die", "Can't Die", "Legends Never Die", "2020", Mood.SAD),
        song("already-dead", "Already Dead", "Fighting Demons", "2021", Mood.SAD),
        song("burn", "Burn", "Fighting Demons", "2021", Mood.SAD, Mood.CHILL),
        song("wandered-to-la", "Wandered to LA", "Fighting Demons", "2021", Mood.LOVE),
        song("girl-of-my-dreams", "Girl of My Dreams", "Fighting Demons", "2021", Mood.LOVE),
        song("not-enough", "Not Enough", "Fighting Demons", "2021", Mood.LOVE, Mood.CHILL),
        song("sometimes", "Sometimes", "Fighting Demons", "2022", Mood.SAD),
        song("you-wouldnt-understand", "You Wouldn't Understand", "Fighting Demons", "2021", Mood.CHILL),
        song("rockstar-in-his-prime", "Rockstar in His Prime", "Fighting Demons", "2021", Mood.SAD),
        song("doom", "Doom", "Fighting Demons", "2021", Mood.SAD),
        song("go-hard", "Go Hard", "Fighting Demons", "2021", Mood.HYPE),
        song("go-hard-20", "Go Hard 2.0", "Fighting Demons", "2022", Mood.HYPE),
        song("feline", "Feline", "Fighting Demons", "2021", Mood.HYPE),
        song("relocate", "Relocate", "Fighting Demons", "2021", Mood.CHILL),
        song("until-the-plug-comes-back-around", "Until the Plug Comes Back Around", "Fighting Demons", "2021", Mood.SAD),
        song("from-my-window", "From My Window", "Fighting Demons", "2021", Mood.SAD),
        song("feel-alone", "Feel Alone", "Fighting Demons", "2021", Mood.SAD),
        song("my-life-in-a-nutshell", "My Life in a Nutshell", "Fighting Demons", "2021", Mood.SAD),
        song("cigarettes", "Cigarettes", "Cigarettes", "2022", Mood.LOVE, Mood.CHILL),
        song("in-my-head", "In My Head", "In My Head", "2022", Mood.SAD),
        song("face-2-face", "Face 2 Face", "Face 2 Face", "2022", Mood.SAD),
        song("the-light", "The Light", "The Light", "2023", Mood.LOVE, Mood.CHILL),
        song("lace-it", "Lace It", "Lace It", "2023", Mood.HYPE),
        song("empty-out-your-pockets", "Empty Out Your Pockets", "The Party Never Ends", "2024", Mood.HYPE),
        song("misfit", "Misfit", "The Party Never Ends", "2024", Mood.HYPE),
        song("party-by-myself", "Party By Myself", "The Party Never Ends", "2024", Mood.CHILL),
        song("adore-you", "Adore You", "The Party Never Ends", "2024", Mood.LOVE),
        song("condone-it", "Condone It", "The Party Never Ends", "2024", Mood.HYPE),
        song("the-party-never-ends", "The Party Never Ends", "The Party Never Ends", "2024", Mood.HYPE),
        song("agats2-insecure", "AGATS2 (Insecure)", "The Party Never Ends", "2024", Mood.HEARTBREAK),
        song("cuffed", "Cuffed", "The Party Never Ends", "2024", Mood.HYPE),
        song("ktm-drip", "KTM Drip", "The Party Never Ends", "2024", Mood.HYPE),
        song("love-letter", "Love Letter", "The Party Never Ends", "2024", Mood.LOVE),
        song("celebrate", "Celebrate", "The Party Never Ends", "2024", Mood.HYPE),
        song("jeffrey", "Jeffrey", "The Party Never Ends", "2024", Mood.HYPE),
        song("barbarian", "Barbarian", "The Party Never Ends", "2024", Mood.HYPE),
        song("best-friend", "Best Friend", "The Party Never Ends", "2024", Mood.LOVE),
        song("floor-it", "Floor It", "The Party Never Ends", "2024", Mood.HYPE),
        song("oxycodone", "Oxycodone", "The Party Never Ends", "2024", Mood.CHILL),
        song("spend-it", "Spend It", "The Party Never Ends", "2024", Mood.SAD),
    )

    private val byId = all.associateBy { it.id }
    fun find(id: String): FanSong? = byId[id]

    fun filter(
        mood: Mood,
        query: String,
        favorites: Set<String>,
    ): List<FanSong> {
        val search = query.trim()
        return all.filter { song ->
            (mood == Mood.ALL ||
                (mood == Mood.FAVORITES && song.id in favorites) ||
                mood in song.moods) &&
                (search.isBlank() ||
                    song.title.contains(search, ignoreCase = true) ||
                    song.album.contains(search, ignoreCase = true) ||
                    song.year.contains(search, ignoreCase = true))
        }
    }

    fun count(mood: Mood, favorites: Set<String>): Int = filter(mood, "", favorites).size
}


/** Matches real local audio by whole contiguous song-title tokens, not fuzzy/random filename guesses. */
object SongFileMatcher {
    private val separator = Regex("[^a-z0-9]+")
    private val rejected = setOf(
        "remix", "instrumental", "cover", "karaoke", "nightcore", "slowed", "spedup"
    )
    private fun tokens(text: String): List<String> =
        text.lowercase()
            .replace(Regex("(?<=[a-z])['’](?=[a-z])"), "")
            .replace("&", " and ")
            .split(separator).filter { it.isNotBlank() }

    fun matchFilename(filename: String): FanSong? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus")) return null
        val parts = tokens(filename.substringBeforeLast('.', filename))
        if (parts.isEmpty() || parts.any { it in rejected }) return null
        val identifiesArtist = parts.windowed(2).any { it == listOf("juice", "wrld") } ||
            "juicewrld" in parts
        // A lone generic title (e.g. Fast.mp3) could belong to any artist.
        // Require an artist marker before assigning single-word files automatically.
        if (parts.size == 1 && !identifiesArtist) return null
        return FanSongs.all.filter { song ->
            val title = tokens(song.title)
            title.isNotEmpty() && parts.windowed(title.size).any { it == title }
        }.maxByOrNull { it.title.length }
    }
}
