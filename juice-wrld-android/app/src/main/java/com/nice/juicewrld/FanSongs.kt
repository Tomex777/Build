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
        song("armed-and-dangerous", "Armed and Dangerous", "Goodbye & Good Riddance", "2018", Mood.HYPE),
        song("legends", "Legends", "Too Soon..", "2018", Mood.SAD, Mood.CHILL),
        song("rich-and-blind", "Rich and Blind", "Too Soon..", "2018", Mood.SAD),
        song("fine-china", "Fine China", "WRLD ON DRUGS", "2018", Mood.LOVE, Mood.HYPE),
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
        song("already-dead", "Already Dead", "Fighting Demons", "2021", Mood.SAD),
        song("burn", "Burn", "Fighting Demons", "2021", Mood.SAD, Mood.CHILL),
        song("wandered-to-la", "Wandered to LA", "Fighting Demons", "2021", Mood.LOVE),
        song("girl-of-my-dreams", "Girl of My Dreams", "Fighting Demons", "2021", Mood.LOVE),
        song("not-enough", "Not Enough", "Fighting Demons", "2021", Mood.LOVE, Mood.CHILL),
        song("sometimes", "Sometimes", "Fighting Demons", "2022", Mood.SAD),
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
