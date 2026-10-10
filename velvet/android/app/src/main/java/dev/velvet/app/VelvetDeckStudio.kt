package dev.velvet.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

internal object OurDeck {
    val questions=mutableStateListOf<String>()
    fun load(ctx:Context) {
        val prefs=ctx.getSharedPreferences("velvet_personal_deck_v1",Context.MODE_PRIVATE)
        questions.clear()
        runCatching{
            val arr=JSONArray(prefs.getString("questions","[]"))
            repeat(arr.length()){i->if(questions.size<150) questions.add(arr.getString(i))}
        }
    }
    fun save(ctx:Context) {
        val arr=JSONArray();questions.forEach{arr.put(it)}
        ctx.getSharedPreferences("velvet_personal_deck_v1",Context.MODE_PRIVATE)
            .edit().putString("questions",arr.toString()).apply()
    }
}
@Composable
internal fun OurDeckCreator(onBack:()->Unit,onPlay:()->Unit) {
    val ctx=LocalContext.current
    val p=VelvetTheme.current
    var draft by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        VelvetGameBar("Our own deck","Questions written by you ♡",onBack)
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(p.paper).padding(17.dp)){
            Text("Questions only we would ask.",color=p.text,fontSize=23.sp,fontFamily=FontFamily.Serif)
            Spacer(Modifier.height(7.dp))
            Text("Make a private custom deck. Every question is available offline. "+
                "AI-assisted deck creation can be added after our server is connected.",
                color=p.muted,fontSize=12.sp,lineHeight=18.sp)
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(value=draft,onValueChange={draft=it.take(240)},
                label={Text("Write a question…")},modifier=Modifier.fillMaxWidth(),maxLines=4)
            if(error.isNotBlank())Text(error,color=p.rose,fontSize=12.sp)
            Spacer(Modifier.height(9.dp))
            Button(onClick={
                val q=draft.trim()
                val existing=OurDeck.questions.toList()+VelvetOfflineLibrary.questions.map{it.second}
                when {
                    q.length<12->error="Give your question a little more detail."
                    OurDeck.questions.size>=150->error="Your deck already has 150 questions."
                    VelvetOfflineLibrary.isTooSimilar(q,existing)->error="This sounds very close to an existing question. Try another angle."
                    else ->{OurDeck.questions.add(q);OurDeck.save(ctx);draft="";error=""}
                }
            },modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null,Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp));Text("Add to our deck")
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Our questions",color=p.text,fontSize=22.sp,fontFamily=FontFamily.Serif,
                modifier=Modifier.weight(1f))
            Text("${OurDeck.questions.size} added",color=p.muted,fontSize=11.sp)
        }
        Spacer(Modifier.height(11.dp))
        if(OurDeck.questions.isNotEmpty()){
            Button(onClick=onPlay,modifier=Modifier.fillMaxWidth().height(49.dp)){
                Icon(Icons.Outlined.Style,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp));Text("Play our deck ♡")
            }
            Spacer(Modifier.height(13.dp))
        }
        if(OurDeck.questions.isEmpty())Text("Your own questions will appear here.",
            color=p.muted,fontSize=13.sp)
        OurDeck.questions.toList().forEachIndexed{index,q->
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp)
                .clip(RoundedCornerShape(15.dp)).background(p.paper).padding(11.dp),
                verticalAlignment=Alignment.CenterVertically){
                Text(q,color=p.text,fontSize=13.sp,modifier=Modifier.weight(1f))
                IconButton(onClick={OurDeck.questions.removeAt(index);OurDeck.save(ctx)}){
                    Icon(Icons.Outlined.Close,"Remove question",tint=p.rose)
                }
            }
        }
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
internal fun TogetherAnswers(onBack:()->Unit){
    val p=VelvetTheme.current
    val library=remember{VelvetOfflineLibrary.questions.filter{it.first=="Heart to heart"||it.first=="Sweet & silly"}}
    var index by remember{mutableIntStateOf(0)}
    var first by remember(index){mutableStateOf("")}
    var second by remember(index){mutableStateOf("")}
    var phase by remember(index){mutableIntStateOf(0)}
    val question=library[index%library.size].second
    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        VelvetGameBar("Answer together","Pass & play · Two answers, one reveal",onBack)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Color(0xFFE6BEC9)).padding(horizontal=23.dp,vertical=24.dp),
            horizontalAlignment=Alignment.CenterHorizontally){
            Text("A QUESTION FOR TWO",color=Color(0xFF5B3A51),fontSize=11.sp,letterSpacing=2.sp)
            Spacer(Modifier.height(24.dp))
            Text(question,color=Color(0xFF452D40),fontFamily=FontFamily.Serif,
                fontWeight=FontWeight.SemiBold,fontSize=28.sp,lineHeight=36.sp,
                textAlign=TextAlign.Center)
            Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(20.dp))
        when(phase){
            0 -> {
                Text("Your answer ♡",color=p.text,fontFamily=FontFamily.Serif,fontSize=26.sp)
                Text("Write yours, then hand the phone over.",color=p.muted,fontSize=12.sp)
                OutlinedTextField(value=first,onValueChange={first=it.take(450)},
                    modifier=Modifier.fillMaxWidth().padding(top=13.dp),minLines=3,maxLines=6,
                    placeholder={Text("What would you say?")})
                Spacer(Modifier.height(14.dp))
                Button(onClick={phase=1},enabled=first.isNotBlank(),
                    modifier=Modifier.fillMaxWidth()){Text("Pass to my love →")}
            }
            1 -> {
                Text("Their answer ♡",color=p.text,fontFamily=FontFamily.Serif,fontSize=26.sp)
                Text("The first answer is hidden until both are ready.",color=p.muted,fontSize=12.sp)
                OutlinedTextField(value=second,onValueChange={second=it.take(450)},
                    modifier=Modifier.fillMaxWidth().padding(top=13.dp),minLines=3,maxLines=6,
                    placeholder={Text("What would you say?")})
                Spacer(Modifier.height(14.dp))
                Button(onClick={phase=2},enabled=second.isNotBlank(),
                    modifier=Modifier.fillMaxWidth()){Text("Reveal our answers ♡")}
            }
            else ->{
                for((label,answer) in listOf("You" to first,"Your love" to second)){
                    Column(Modifier.fillMaxWidth().padding(bottom=11.dp)
                        .clip(RoundedCornerShape(18.dp)).background(p.paper).padding(17.dp)){
                        Text(label,color=p.rose,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(answer,color=p.text,fontSize=15.sp,lineHeight=21.sp)
                    }
                }
                Button(onClick={index=(index+1)%library.size},
                    modifier=Modifier.fillMaxWidth()){Text("Another question →")}
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Local pass-and-play preview. Live two-phone reveals require pairing.",
            color=p.muted,fontSize=11.sp,textAlign=TextAlign.Center)
        Spacer(Modifier.height(25.dp))
    }
}
