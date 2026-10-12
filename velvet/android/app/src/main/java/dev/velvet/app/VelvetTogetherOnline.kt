package dev.velvet.app

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private data class TogetherRound(val id:String,val question:String,val category:String,val at:String,
    val mine:String?,val theirs:String?,val partnerAnswered:Boolean,val revealed:Boolean) {
    companion object {
        fun parse(j:JSONObject):TogetherRound {
            val reveal=j.optBoolean("revealed")
            return TogetherRound(j.getString("id"),j.getString("question"),j.getString("category"),
                j.getString("created_at"),j.optString("my_answer").takeUnless{it.isBlank()||it=="null"},
                if(reveal)j.optString("partner_answer").takeUnless{it.isBlank()||it=="null"} else null,
                j.optBoolean("partner_answered"),reveal)
        }
    }
}

@Composable
internal fun VelvetTogetherOnline(client:VelvetPairClient,onBack:()->Unit,onPair:()->Unit) {
    val lifecycle=(LocalContext.current as LifecycleOwner).lifecycle
    val scope=rememberCoroutineScope()
    val rounds=remember{mutableStateListOf<TogetherRound>()}
    val drafts=remember{mutableStateMapOf<String,String>()}
    var selected by remember{mutableStateOf<String?>(null)}
    var ready by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var custom by remember{mutableStateOf("")}
    var requestId by remember{mutableStateOf<String?>(null)}
    var requestBody by remember{mutableStateOf("")}
    var beforeAt by remember{mutableStateOf<String?>(null)}
    var beforeId by remember{mutableStateOf<String?>(null)}
    var more by remember{mutableStateOf(false)}
    var initialized by remember{mutableStateOf(false)}
    var checks by remember{mutableIntStateOf(0)}
    val rose=Color(0xFFE4A1B6);val cream=Color(0xFFFFEAF0);val bg=Color(0xFF171019)
    fun accept(round:TogetherRound) {
        val index=rounds.indexOfFirst{it.id==round.id}
        if(index<0)rounds.add(round)
        else {
            val old=rounds[index]
            // A stale polling response must never roll back an acknowledged answer/reveal.
            if((old.mine==null || round.mine!=null) && (!old.revealed || round.revealed))rounds[index]=round
        }
    }
    LaunchedEffect(client,checks) {
        ready=false;error=""
        if(!client.configured){error="Connect the backend to answer together on two phones.";return@LaunchedEffect}
        try {
            check(client.hasSession() && client.myPair()?.optInt("member_count")==2){"Sign in and pair both accounts first."}
            ready=true
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(isActive) {
                    try {
                        val page=client.listTogether()
                        for(i in 0 until page.length())accept(TogetherRound.parse(page.getJSONObject(i)))
                        if(!initialized) {
                            more=page.length()>=20
                            if(page.length()>0)page.getJSONObject(page.length()-1).let{beforeAt=it.getString("created_at");beforeId=it.getString("id")}
                            initialized=true
                        }
                        selected?.let {id->
                            val snapshot=client.getTogether(id)
                            if(snapshot.has("id"))accept(TogetherRound.parse(snapshot))
                        }
                    } catch(cancelled:CancellationException){throw cancelled}
                    catch(e:Exception){error=e.message ?: "Couldn't refresh shared questions."}
                    delay(3000)
                }
            }
        } catch(cancelled:CancellationException){throw cancelled}
        catch(e:Exception){error=e.message ?: "Couldn't connect."}
    }
    val round=rounds.firstOrNull{it.id==selected}
    BackHandler(enabled=selected!=null) {selected=null}
    Column(Modifier.fillMaxSize().background(bg).imePadding().padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={if(selected!=null)selected=null else onBack()}){Text("← Back",color=rose)}
            Text("Answer together",color=cream,fontFamily=FontFamily.Serif,fontSize=26.sp)
        }
        Text("Two phones · One shared question",color=rose,fontSize=12.sp)
        if(error.isNotBlank()) {
            Text(error,color=cream,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))
            TextButton(onClick={checks++}){Text("Retry connection",color=rose)}
        }
        if(!ready)TextButton(onClick=onPair){Text("Connect our phones",color=rose)}
        else if(round==null) {
            OutlinedTextField(custom,{custom=it.take(1000)},label={Text("Ask your own question")},
                modifier=Modifier.fillMaxWidth().padding(top=14.dp),maxLines=3)
            Button(onClick={
                busy=true;error=""
                val body=custom.trim()
                if(requestId==null || requestBody!=body){requestId=UUID.randomUUID().toString();requestBody=body}
                val id=requestId!!
                scope.launch {
                    try {
                        val result=client.createTogether(id,body,"Our own question")
                        if(result.optBoolean("ok")) {
                            val item=TogetherRound.parse(result.getJSONObject("round"));accept(item);selected=item.id
                            custom="";requestId=null
                        } else error=result.optString("error","Couldn't share this question.")
                    } catch(cancelled:CancellationException){throw cancelled}
                    catch(e:Exception){error=e.message ?: "Couldn't share. Your question is kept for retry."}
                    finally {busy=false}
                }
            },enabled=!busy && custom.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Sharing…" else "Share a question ♡")}
            Text("You can also share a card from any conversation deck.",color=rose,fontSize=11.sp,modifier=Modifier.padding(vertical=12.dp))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(rounds.sortedByDescending{it.at+it.id},key={it.id}) {q->
                    Card(onClick={selected=q.id},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(q.category,fontSize=11.sp)
                            Text(q.question,fontFamily=FontFamily.Serif,fontSize=20.sp)
                            Text(if(q.revealed)"Both answers revealed ♡" else if(q.mine!=null)"Waiting for your person" else "Your answer is waiting",fontSize=12.sp)
                        }
                    }
                }
                if(initialized && rounds.isEmpty())item{Text("Start with a question from the heart ♡",color=cream)}
                if(more)item{TextButton(onClick={
                    busy=true
                    scope.launch {
                        try {
                            val page=client.listTogether(beforeAt,beforeId)
                            for(i in 0 until page.length())accept(TogetherRound.parse(page.getJSONObject(i)))
                            if(page.length()>0)page.getJSONObject(page.length()-1).let{beforeAt=it.getString("created_at");beforeId=it.getString("id")}
                            more=page.length()>=20
                        } catch(cancelled:CancellationException){throw cancelled}
                        catch(e:Exception){error=e.message ?: "Couldn't load earlier questions."}
                        finally{busy=false}
                    }
                },enabled=!busy){Text("Earlier questions",color=rose)}}
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().background(Color(0xFFE6BEC9),RoundedCornerShape(26.dp)).padding(25.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(round.category.uppercase(),color=Color(0xFF5B3A51),fontSize=11.sp)
                    Spacer(Modifier.height(25.dp))
                    Text(round.question,color=Color(0xFF452D40),fontFamily=FontFamily.Serif,fontSize=29.sp,lineHeight=37.sp,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                }
                Spacer(Modifier.height(22.dp))
                if(round.mine==null) {
                    Text(if(round.partnerAnswered)"Your person has answered. Add yours to reveal both ♡" else "Write yours privately. Both answers reveal together.",color=rose,textAlign=TextAlign.Center)
                    OutlinedTextField(drafts[round.id].orEmpty(),{drafts[round.id]=it.take(2000)},
                        label={Text("Your answer")},modifier=Modifier.fillMaxWidth().padding(vertical=15.dp),minLines=3,maxLines=6)
                    Text("Once submitted, your answer stays final for this round.",color=rose,fontSize=11.sp)
                    Button(onClick={
                        busy=true;error=""
                        val body=drafts[round.id].orEmpty().trim()
                        scope.launch {
                            try {
                                val result=client.answerTogether(round.id,body)
                                if(result.optBoolean("ok"))accept(TogetherRound.parse(result.getJSONObject("round")))
                                else error=result.optString("error","Couldn't submit this answer.")
                            } catch(cancelled:CancellationException){throw cancelled}
                            catch(e:Exception){error=e.message ?: "Couldn't submit. Your draft is kept for retry."}
                            finally{busy=false}
                        }
                    },enabled=!busy && drafts[round.id].orEmpty().isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(busy)"Submitting…" else "Submit my answer ♡")}
                } else {
                    Text("Your answer",color=rose,fontSize=17.sp)
                    Text(round.mine,color=cream,fontSize=18.sp,modifier=Modifier.fillMaxWidth().padding(15.dp))
                    if(round.revealed) {
                        Text("Your person's answer ♡",color=rose,fontSize=17.sp)
                        Text(round.theirs.orEmpty(),color=cream,fontSize=18.sp,modifier=Modifier.fillMaxWidth().padding(15.dp))
                    } else Text("Waiting for your person's answer…",color=rose,modifier=Modifier.padding(20.dp))
                }
            }
        }
    }
}
