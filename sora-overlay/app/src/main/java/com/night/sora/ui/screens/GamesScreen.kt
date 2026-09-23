package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ContentType
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.*

private data class GameStats(val quizzes: Int, val correct: Int, val bestStreak: Int)

@Composable
fun GamesScreen(modifier: Modifier = Modifier, library: List<LibraryEntry>) {
    val context = LocalContext.current
    val candidates = remember(library) {
        library.filter { (it.contentType == ContentType.ANIME || it.contentType == ContentType.MANGA) && !it.artworkUrl.isNullOrBlank() }
            .distinctBy { it.id }
    }
    var stats by remember { mutableStateOf(readGameStats(context)) }
    var questions by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var round by remember { mutableIntStateOf(0) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var correctThisGame by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    fun startGame() {
        questions = candidates.shuffled().take(5)
        round = 0
        currentStreak = 0
        correctThisGame = 0
        answered = null
        finished = false
    }

    fun answer(entry: LibraryEntry) {
        if (answered != null || questions.isEmpty()) return
        val target = questions[round]
        val isCorrect = entry.id == target.id
        answered = entry.id
        if (isCorrect) {
            correctThisGame++
            currentStreak++
            stats = stats.copy(correct = stats.correct + 1, bestStreak = maxOf(stats.bestStreak, currentStreak))
        } else currentStreak = 0
        writeGameStats(context, stats)
    }

    fun nextRound() {
        answered = null
        if (round + 1 >= questions.size) {
            finished = true
            stats = stats.copy(quizzes = stats.quizzes + 1)
            writeGameStats(context, stats)
        } else round++
    }

    val target = questions.getOrNull(round)
    val choices = remember(target?.id, candidates) {
        target?.let { current -> (candidates.filterNot { it.id == current.id }.shuffled().take(3) + current).shuffled() }.orEmpty()
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Games", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("A title quiz built from your saved collection.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        item {
            Surface(color = Color(0xFF242118), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(19.dp)) {
                    Text("TITLE MATCH", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("Which title is this?", fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
                    Text("Pick the real Anime or Manga from its saved cover. Questions and choices come from your Library.", color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                    if (candidates.size < 2) {
                        Text("Save at least two Anime or Manga titles with cover art to play.", color = SoraAccent, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
                    } else {
                        Button(onClick = ::startGame, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (questions.isNotEmpty() && !finished) "Start over" else "Play ${minOf(5, candidates.size)} rounds")
                        }
                    }
                }
            }
        }
        if (target != null && !finished) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ROUND ${round + 1} OF ${questions.size}", color = SoraMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    Text("$correctThisGame correct", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                AsyncImage(target.artworkUrl, "Cover art quiz", Modifier.fillMaxWidth().height(260.dp).padding(top = 9.dp).background(SoraSurface, RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Text("Choose the title", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 11.dp, bottom = 2.dp))
            }
            items(choices, key = { it.id }) { choice ->
                val isCorrect = choice.id == target.id
                val isPicked = answered == choice.id
                val background = when {
                    answered == null -> SoraSurface
                    isCorrect -> Color(0xFF263C2C)
                    isPicked -> Color(0xFF492E27)
                    else -> SoraSurface
                }
                Row(
                    Modifier.fillMaxWidth().background(background, RoundedCornerShape(12.dp))
                        .clickable(enabled = answered == null) { answer(choice) }.padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(choice.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (answered != null && isCorrect) Icon(Icons.Rounded.Check, "Correct", tint = Color(0xFF8AD49A))
                    else if (answered != null && isPicked) Icon(Icons.Rounded.Close, "Incorrect", tint = Color(0xFFE98573))
                }
            }
            if (answered != null) item {
                Button(onClick = ::nextRound, modifier = Modifier.fillMaxWidth()) { Text(if (round + 1 == questions.size) "Finish quiz" else "Next") }
            }
        }
        if (finished) item {
            Surface(color = SoraSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Quiz complete", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("You got $correctThisGame of ${questions.size} right.", color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    TextButton(onClick = ::startGame, modifier = Modifier.padding(top = 6.dp)) { Text("Play again") }
                }
            }
        }
        item {
            Text("YOUR PLAY", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = SoraAccent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameStat(stats.quizzes.toString(), "Quizzes", Modifier.weight(1f))
                GameStat(stats.correct.toString(), "Correct", Modifier.weight(1f))
                GameStat(stats.bestStreak.toString(), "Best streak", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(SoraSurface, RoundedCornerShape(13.dp)).padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), color = SoraMuted, fontSize = 8.sp, letterSpacing = .5.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun readGameStats(context: Context): GameStats {
    val prefs = context.getSharedPreferences("sora_games", Context.MODE_PRIVATE)
    return GameStats(prefs.getInt("quizzes", 0), prefs.getInt("correct", 0), prefs.getInt("best_streak", 0))
}

private fun writeGameStats(context: Context, stats: GameStats) {
    context.getSharedPreferences("sora_games", Context.MODE_PRIVATE).edit()
        .putInt("quizzes", stats.quizzes).putInt("correct", stats.correct).putInt("best_streak", stats.bestStreak).apply()
}
