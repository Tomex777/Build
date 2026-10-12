package dev.velvet.app

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private data class OnlineBoard(val id:String,val kind:String,val board:List<Int>,val one:String,
    val two:String,val turn:Int,val winner:Int,val version:Int) {
    fun myTurn(me:String)=winner==0 && me==(if(turn==1)one else two)
    companion object {
        fun parse(j:JSONObject):OnlineBoard {
            val cells=j.getJSONArray("board")
            val kind=j.getString("kind")
            val board=(0 until cells.length()).map{cells.getInt(it)}
            require(kind in listOf("ttt","connect4") && board.size==if(kind=="ttt")9 else 42)
            require(board.all{it in 0..2})
            return OnlineBoard(j.getString("id"),kind,board,j.getString("player_one"),
                j.getString("player_two"),j.getInt("turn"),j.getInt("winner"),j.getInt("version"))
        }
    }
}

@Composable
internal fun VelvetOnlineGames(client:VelvetPairClient,onBack:()->Unit,onPair:()->Unit) {
    val lifecycle=(LocalContext.current as LifecycleOwner).lifecycle
    val scope=rememberCoroutineScope()
    val games=remember {mutableStateListOf<OnlineBoard>()}
    var me by remember {mutableStateOf("")}
    var partner by remember {mutableStateOf("Your love")}
    var selected by remember {mutableStateOf<String?>(null)}
    var ready by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var resigning by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf("Connecting…")}
    var attempts by remember {mutableIntStateOf(0)}
    var pendingCreate by remember {mutableStateOf<Pair<String,String>?>(null)}
    val rose=Color(0xFFE4A1B6);val cream=Color(0xFFFFEAF0);val bg=Color(0xFF171019)
    fun applyGame(game:OnlineBoard) {
        val index=games.indexOfFirst{it.id==game.id}
        if(index<0) games.add(game)
        else if(game.version>=games[index].version)games[index]=game
    }
    LaunchedEffect(client,attempts) {
        ready=false;error=""
        if(!client.configured) {notice="Online games need a connected backend.";return@LaunchedEffect}
        try {
            check(client.hasSession()) {"Sign in and pair your two accounts first."}
            val pair=client.myPair()
            check(pair?.optInt("member_count")==2) {"Pair your two accounts first."}
            me=pair!!.getString("my_id")
            partner=pair.optString("partner_name").takeUnless{it.isBlank()||it=="null"} ?: "Your love"
            ready=true
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(isActive) {
                    try {
                        val page=client.onlineGames()
                        for(i in 0 until page.length())applyGame(OnlineBoard.parse(page.getJSONObject(i)))
                        // A successful refresh resolves transient polling errors.
                        error=""
                        notice="Paired lounge · Refreshes while open"
                    } catch(cancelled:CancellationException) {throw cancelled}
                    catch(e:Exception) {error=e.message ?: "Couldn't refresh the lounge."}
                    delay(3000)
                }
            }
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(e:Exception) {error=e.message ?: "Couldn't connect."}
    }
    fun create(kind:String) {
        if(busy)return
        busy=true;error=""
        val request=pendingCreate?.takeIf{it.second==kind} ?: (UUID.randomUUID().toString() to kind)
        pendingCreate=request
        scope.launch {
            try {
                val result=client.createOnlineGame(request.first,kind)
                if(result.optBoolean("ok")) {
                    val game=OnlineBoard.parse(result.getJSONObject("game"));applyGame(game)
                    selected=game.id;pendingCreate=null
                } else error=result.optString("error","Couldn't create this round.")
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(e:Exception) {error=e.message ?: "Retry to create the same round."}
            finally {busy=false}
        }
    }
    val game=games.firstOrNull{it.id==selected}
    BackHandler(enabled=selected!=null) {selected=null}
    Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={if(selected!=null)selected=null else onBack()}) {Text("← Back",color=rose)}
            Text(if(game==null)"Our online lounge" else if(game.kind=="ttt")"Tic-tac-toe" else "Connect Four",
                color=cream,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        }
        Text(notice,color=rose,fontSize=12.sp)
        if(error.isNotBlank())Text(error,color=cream,modifier=Modifier.padding(vertical=8.dp))
        if(!ready) {
            TextButton(onClick=onPair) {Text("Connect our phones",color=rose)}
            TextButton(onClick={attempts++}) {Text("Retry connection",color=rose)}
        } else if(game==null) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={create("ttt")},enabled=!busy,modifier=Modifier.weight(1f)){Text("New Tic-tac-toe")}
                Button(onClick={create("connect4")},enabled=!busy,modifier=Modifier.weight(1f)){Text("New Connect Four")}
            }
            Text("Your partner opens this lounge to join. Up to five active rounds.",color=cream,fontSize=12.sp,
                modifier=Modifier.padding(vertical=12.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(games.sortedBy{it.winner!=0},key={it.id}) {g->
                    Card(onClick={selected=g.id},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if(g.kind=="ttt")"Tic-tac-toe" else "Connect Four")
                            Text(if(g.winner!=0)"Finished · Tap to review" else if(g.myTurn(me))"Your turn" else "$partner's turn")
                        }
                    }
                }
                if(games.isEmpty())item{Text("Start your first shared round ♡",color=cream)}
            }
        } else {
            val winner=if(game.winner==1)game.one else game.two
            Text(when {
                game.winner==3 -> "A draw ♡"
                game.winner!=0 -> if(winner==me)"You won! ♡" else "$partner won! ♡"
                game.myTurn(me) -> "Your turn · "+if(game.kind=="ttt") (if(me==game.one)"X" else "O") else "Drop a disc"
                else -> "$partner's turn"
            },color=cream,fontSize=20.sp,modifier=Modifier.padding(vertical=18.dp))
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Column(Modifier.fillMaxWidth().widthIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    val cols=if(game.kind=="ttt")3 else 7
                    val rows=if(game.kind=="ttt")3 else 6
                    repeat(rows) {r->
                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            repeat(cols) {c->
                                val cell=game.board[r*cols+c]
                                Box(Modifier.weight(1f).aspectRatio(1f)
                                    .background(if(cell==0)Color(0xFF30202D) else if(cell==1)rose else Color(0xFFF0CB8E),
                                        if(game.kind=="ttt")RoundedCornerShape(10.dp) else CircleShape)
                                    .clickable(enabled=!busy && game.myTurn(me) && (game.kind!="ttt" || cell==0)) {
                                        busy=true;error=""
                                        scope.launch {
                                            try {
                                                val result=client.moveOnlineGame(game.id,game.version,if(game.kind=="ttt")r*3+c else c)
                                                result.optJSONObject("game")?.let{applyGame(OnlineBoard.parse(it))}
                                                if(!result.optBoolean("ok"))error=when(result.optString("error")) {
                                                    "stale_move" -> "This round changed. Review the board and tap again."
                                                    "not_your_turn" -> "Wait for your partner's move."
                                                    "occupied" -> "Choose an empty cell or another column."
                                                    else -> result.optString("error","Move not accepted.")
                                                }
                                            } catch(cancelled:CancellationException) {throw cancelled}
                                            catch(e:Exception) {error=e.message ?: "Couldn't submit this move. The board will refresh."}
                                            finally {busy=false}
                                        }
                                    },contentAlignment=Alignment.Center) {
                                    if(game.kind=="ttt")Text(if(cell==1)"X" else if(cell==2)"O" else "",color=bg,
                                        fontSize=38.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            if(game.winner==0)TextButton(onClick={resigning=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Concede this round",color=rose)}
            if(game.winner!=0)Button(onClick={create(game.kind)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Invite a rematch")}
            Text("Server-checked turns · Foreground updates · No background game notifications yet",
                color=rose,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
        }
    }
    if(resigning && game!=null)AlertDialog(onDismissRequest={resigning=false},
        title={Text("Concede this round?")},text={Text("Your partner wins this round. You can invite a rematch afterward.")},
        confirmButton={TextButton(onClick={
            resigning=false;busy=true
            scope.launch {
                try {
                    val result=client.resignGame(game.id,game.version)
                    result.optJSONObject("game")?.let{applyGame(OnlineBoard.parse(it))}
                    if(!result.optBoolean("ok"))error="This round changed. Refresh and try again."
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(e:Exception) {error=e.message ?: "Couldn't concede this round."}
                finally {busy=false}
            }
        }) {Text("Concede")}},dismissButton={TextButton(onClick={resigning=false}) {Text("Keep playing")}})

}
