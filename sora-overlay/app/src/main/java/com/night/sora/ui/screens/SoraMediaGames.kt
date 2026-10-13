package com.night.sora.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.model.ContentType
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.*

private data class Question(val prompt: String, val right: String, val alternatives: List<String>)
private fun question(prompt: String, answer: String, a: String, b: String, c: String) =
    Question(prompt,answer,listOf(a,b,c))

private val whoQuestions = listOf(
    question("I want to be Hokage of the Hidden Leaf. Who am I?", "Naruto Uzumaki", "Monkey D. Luffy","Goku","Ichigo Kurosaki"),
    question("I am a straw-hat pirate captain searching for the One Piece.", "Monkey D. Luffy", "Zoro","Sanji","Light Yagami"),
    question("I fight demons to find a cure for my sister Nezuko.", "Tanjiro Kamado", "Eren Yeager","Levi Ackerman","Edward Elric"),
    question("I am a Saiyan raised on Earth and trained by Master Roshi.", "Goku", "Vegeta","Naruto Uzumaki","Saitama"),
    question("I found a Death Note and took the alias Kira.", "Light Yagami", "L Lawliet","Lelouch Lamperouge","Roronoa Zoro"),
    question("I lost an arm and leg after a failed human transmutation.", "Edward Elric", "Alphonse Elric","Kakashi Hatake","Gojo Satoru"),
    question("I am a Survey Corps captain known for my combat skill.", "Levi Ackerman", "Eren Yeager","Erwin Smith","Armin Arlert"),
    question("I am a blindfolded sorcerer who teaches at Tokyo Jujutsu High.", "Satoru Gojo", "Yuji Itadori","Megumi Fushiguro","Kento Nanami"),
)

private val quoteQuestions = listOf(
    question("Who made the line 'To infinity and beyond!' famous?", "Buzz Lightyear", "Woody","Lightning McQueen","Shrek"),
    question("Who famously says 'My precious' in The Lord of the Rings?", "Gollum", "Frodo Baggins","Gandalf","Aragorn"),
    question("Who promises 'I'll be back' in The Terminator?", "The T-800", "Sarah Connor","Kyle Reese","John Connor"),
    question("Who says 'Why so serious?' in The Dark Knight?", "The Joker", "Batman","Alfred","Harvey Dent"),
    question("Who says 'No, I am your father' in The Empire Strikes Back?", "Darth Vader", "Obi-Wan Kenobi","Yoda","Han Solo"),
    question("Who repeats 'Just keep swimming' in Finding Nemo?", "Dory", "Marlin","Nemo","Bruce"),
    question("Who tells Katniss 'May the odds be ever in your favor' at the Reaping?", "Effie Trinket", "President Snow","Peeta Mellark","Haymitch"),
    question("Who is best known for the phrase 'Wakanda forever!' in Black Panther?", "T'Challa", "Thor","Tony Stark","Steve Rogers"),
)

private val bibleQuestions = listOf(
    question("Which book begins the Bible?", "Genesis", "Exodus","Matthew","Psalms"),
    question("Who built the ark before the great flood?", "Noah", "Abraham","Moses","Joshua"),
    question("Who defeated Goliath using a sling?", "David", "Saul","Solomon","Daniel"),
    question("Who was thrown into a den of lions?", "Daniel", "Elijah","Jonah","Peter"),
    question("According to the Gospels, where was Jesus born?", "Bethlehem", "Nazareth","Jerusalem","Capernaum"),
    question("Which book contains 'The Lord is my shepherd'?", "Psalms", "Proverbs","Exodus","Isaiah"),
    question("Who led the Israelites out of Egypt?", "Moses", "Aaron","Joseph","David"),
    question("Who spent three days in a great fish?", "Jonah", "Daniel","Noah","Samson"),
    question("Which disciple denied knowing Jesus three times?", "Peter", "John","Thomas","Andrew"),
)

@Composable
fun MediaTriviaArcade(category: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sora_arcade_v1",Context.MODE_PRIVATE) }
    fun createRound(): List<Question> = when(category) {
        "who" -> whoQuestions.shuffled().take(5)
        "quotes" -> quoteQuestions.shuffled().take(5)
        "bible" -> bibleQuestions.shuffled().take(5)
        else -> (whoQuestions.shuffled().take(2) +
            quoteQuestions.shuffled().take(1) + bibleQuestions.shuffled().take(2)).shuffled()
    }
    var questions by remember(category) { mutableStateOf(createRound()) }
    var index by remember(category) { mutableIntStateOf(0) }
    var points by remember(category) { mutableIntStateOf(0) }
    var choice by remember(category,index) { mutableStateOf<String?>(null) }
    val bestKey = "quiz_best_" + category
    var best by remember(category) { mutableIntStateOf(prefs.getInt(bestKey,0)) }
    val finished = index >= questions.size
    val title = when(category) {
        "who" -> "Who am I?"
        "quotes" -> "Who said it?"
        "bible" -> "Bible trivia"
        else -> "Daily 5"
    }
    LaunchedEffect(finished,points) {
        if (finished && points > best) {
            best = points
            prefs.edit().putInt(bestKey,best).apply()
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        if (!finished) {
            val current = questions[index]
            val options = remember(category,index,questions) { (current.alternatives + current.right).shuffled() }
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("QUESTION " + (index+1) + " / " + questions.size,color=SoraAccent,fontWeight=FontWeight.Bold)
                Text(points.toString() + " CORRECT",fontWeight=FontWeight.Bold)
            }
            LinearProgressIndicator(progress={index.toFloat()/questions.size},
                modifier=Modifier.fillMaxWidth().padding(bottom=18.dp),color=SoraAccent)
            Surface(color=SoraSurfaceHigh,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(title.uppercase(),color=SoraAccent,fontSize=11.sp,fontWeight=FontWeight.Bold)
                    Text(current.prompt,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,
                        modifier=Modifier.padding(top=15.dp,bottom=10.dp))
                }
            }
            Spacer(Modifier.height(15.dp))
            options.forEach { option ->
                val revealed = choice != null
                val correct = option == current.right
                Surface(
                    modifier=Modifier.fillMaxWidth().padding(bottom=9.dp)
                        .clickable(enabled=!revealed) { choice=option; if(correct) points++ },
                    color=when {
                        revealed && correct -> Color(0xFF254333)
                        revealed && choice==option -> Color(0xFF493026)
                        else -> SoraSurfaceHigh
                    },shape=RoundedCornerShape(13.dp)
                ) {
                    Text(option,modifier=Modifier.padding(17.dp),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
            }
            if (choice != null) {
                Text(if(choice==current.right) "Correct!" else "Correct answer: " + current.right,
                    color=SoraAccent,modifier=Modifier.padding(top=10.dp))
                Button(onClick={index++},modifier=Modifier.fillMaxWidth().padding(top=10.dp)) {
                    Text(if(index+1 == questions.size) "See results" else "Next question")
                }
            }
        } else {
            Spacer(Modifier.height(60.dp))
            Text("Round complete",fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
            Text(points.toString()+" / "+questions.size, color=SoraAccent,fontSize=48.sp,
                fontWeight=FontWeight.Black,modifier=Modifier.padding(18.dp))
            Text("Best: " + best + " correct",color=SoraMuted)
            Button(onClick={ questions=createRound();index=0;points=0 },
                modifier=Modifier.padding(top=20.dp)) { Text("Play again") }
        }
    }
}

private data class RatherPrompt(val a: String, val b: String)
private val ratherPrompts = listOf(
    RatherPrompt("Finish your favorite manga in one night","Watch its entire anime adaptation"),
    RatherPrompt("Hear any soundtrack perfectly","See any book's story animated"),
    RatherPrompt("Explore a fictional world for one day","Meet your favorite fictional character"),
    RatherPrompt("Never forget a story's plot","Experience your favorite story for the first time again"),
    RatherPrompt("Only watch movies for a month","Only listen to music for a month"),
    RatherPrompt("Have unlimited time to read","Have unlimited time to listen to music"),
    RatherPrompt("Be able to pause time in a game","Be able to rewind one game move"),
    RatherPrompt("Watch a new anime every weekend","Read a new manga every weekend"),
    RatherPrompt("Always know the next plot twist","Never have a plot twist spoiled"),
    RatherPrompt("Build your own soundtrack","Design your own anime opening"),
)

@Composable
fun RatherArcade(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sora_arcade_v1",Context.MODE_PRIVATE) }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember(index) { mutableStateOf<Int?>(null) }
    var finished by remember { mutableIntStateOf(0) }
    val prompt = ratherPrompts[index%ratherPrompts.size]
    fun choose(value: Int) {
        if (selected != null) return
        selected = value
        val key = "rather_" + (index%ratherPrompts.size) + "_" + value
        prefs.edit().putInt(key,prefs.getInt(key,0)+1).apply()
        finished++
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text("Make your choice",fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=16.dp))
        Text("ROUND " + (index%ratherPrompts.size+1) + " / " + ratherPrompts.size,
            color=SoraAccent,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        listOf(prompt.a,prompt.b).forEachIndexed { number,option ->
            val picked = selected==number
            Surface(
                modifier=Modifier.fillMaxWidth().heightIn(min=137.dp).padding(bottom=14.dp)
                    .clickable(enabled=selected==null) { choose(number) },
                color=if(picked) Color(0xFF514027) else SoraSurfaceHigh,
                shape=RoundedCornerShape(22.dp)
            ) {
                Box(Modifier.padding(23.dp),contentAlignment=Alignment.Center) {
                    Text(option,fontSize=21.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                }
            }
        }
        if (selected!=null) {
            val key = "rather_" + (index%ratherPrompts.size) + "_" + selected
            Text("Choice saved on this device · selected " + prefs.getInt(key,0) + " time(s)",
                fontSize=12.sp,color=SoraMuted,textAlign=TextAlign.Center)
            Button(onClick={index++},modifier=Modifier.padding(top=16.dp)) { Text("Next choice") }
        } else {
            Text("A personal choice game; no fabricated worldwide votes.",
                fontSize=12.sp,color=SoraMuted,textAlign=TextAlign.Center)
        }
    }
}

@Composable
fun MemeCaptionArcade(library: List<LibraryEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sora_arcade_captions_v1",Context.MODE_PRIVATE) }
    val memes = remember(library) {
        library.filter { it.contentType==ContentType.MEME && !it.artworkUrl.isNullOrBlank() }
            .distinctBy { it.id }
    }
    var at by remember { mutableIntStateOf(0) }
    val meme = memes.getOrNull(at%memes.size.coerceAtLeast(1))
    var caption by remember(meme?.id) { mutableStateOf(prefs.getString(meme?.id.orEmpty(),"").orEmpty()) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        if (meme==null) {
            Text("Save a meme in your Library to start the caption studio.",
                color=SoraMuted,fontSize=16.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=70.dp))
            return@Column
        }
        Text("CAPTION STUDIO",color=SoraAccent,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(SoraSurfaceHigh)) {
            AsyncImage(model=meme.artworkUrl,contentDescription=meme.label,
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            if(caption.isNotBlank()) {
                Text(caption,color=Color.White,fontWeight=FontWeight.Black,textAlign=TextAlign.Center,
                    fontSize=21.sp,lineHeight=24.sp,
                    modifier=Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha=.72f)).padding(14.dp))
            }
        }
        OutlinedTextField(value=caption,onValueChange={caption=it.take(180)},
            label={Text("Your caption")},placeholder={Text("Make it your own…")},
            modifier=Modifier.fillMaxWidth().padding(top=14.dp),minLines=2,maxLines=3)
        Row(Modifier.fillMaxWidth().padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={at=(at+1)%memes.size},modifier=Modifier.weight(1f)) { Text("Next meme") }
            Button(enabled=caption.isNotBlank(),onClick={
                prefs.edit().putString(meme.id,caption).apply()
            },modifier=Modifier.weight(1f)) { Text("Save caption") }
        }
        OutlinedButton(enabled=caption.isNotBlank(),onClick={
            runCatching {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/plain"
                    putExtra(Intent.EXTRA_TEXT,caption+"\n"+meme.artworkUrl.orEmpty())
                },"Share caption and source link"))
            }
        },modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
            Icon(Icons.Rounded.Share,null,modifier=Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share caption text and meme link")
        }
        Text("Captions are saved locally. AI judging and image export are not connected.",
            color=SoraMuted,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=14.dp))
    }
}
