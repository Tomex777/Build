package dev.velvet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val ludoPath:List<Pair<Int,Int>> = buildList {
    for(c in 1..5)add(6 to c)
    for(r in 5 downTo 0)add(r to 6)
    add(0 to 7);add(0 to 8)
    for(r in 1..5)add(r to 8)
    for(c in 9..14)add(6 to c)
    add(7 to 14);add(8 to 14)
    for(c in 13 downTo 9)add(8 to c)
    for(r in 9..14)add(r to 8)
    add(14 to 7);add(14 to 6)
    for(r in 13 downTo 9)add(r to 6)
    for(c in 5 downTo 0)add(8 to c)
    add(7 to 0);add(6 to 0)
}
private data class LudoPiece(val team:Int,val id:Int,val progress:Int)
@Composable
internal fun VelvetLudoGame(onBack:()->Unit){
    val p=VelvetTheme.current
    val pieces=remember {mutableStateListOf<LudoPiece>().apply{
        repeat(4){add(LudoPiece(0,it,-1));add(LudoPiece(1,it,-1))}
    }}
    var turn by remember {mutableIntStateOf(0)}
    var dice by remember {mutableIntStateOf(0)}
    var moves by remember {mutableIntStateOf(0)}
    var finished by remember {mutableIntStateOf(-1)}
    var showHelp by remember {mutableStateOf(false)}
    val colors=listOf(Color(0xFFF0AFC4),Color(0xFFEACD84))
    fun destination(piece:LudoPiece):Int{
        if(piece.progress<0)return -1
        if(piece.progress<52) return (piece.progress+if(piece.team==0)0 else 26)%52
        return -2
    }
    fun legal(piece:LudoPiece):Boolean =
        piece.team==turn&&piece.progress<58&&dice>0&&
            (if(piece.progress<0)dice==6 else piece.progress+dice<=58)
    fun advance(piece:LudoPiece){
        if(!legal(piece))return
        val newProgress=if(piece.progress<0)0 else piece.progress+dice
        val index=pieces.indexOfFirst{it.team==piece.team&&it.id==piece.id}
        if(index>=0)pieces[index]=piece.copy(progress=newProgress)
        if(newProgress in 0..51){
            val landing=(newProgress+if(turn==0)0 else 26)%52
            if(landing !in setOf(0,13,26,39)){
                pieces.toList().filter{it.team!=turn && it.progress in 0..51 &&
                    (it.progress+if(it.team==0)0 else 26)%52==landing}.forEach { rival ->
                    val at=pieces.indexOfFirst{it.team==rival.team&&it.id==rival.id}
                    if(at>=0)pieces[at]=rival.copy(progress=-1)
                }
            }
        }
        if(pieces.count{it.team==turn&&it.progress==58}==4)finished=turn
        if(dice!=6)turn=1-turn
        dice=0;moves++
    }
    fun roll(){
        if(dice>0||finished>=0)return
        dice=Random.nextInt(1,7)
        val available=pieces.any{legal(it)}
        if(!available){dice=0;turn=1-turn}
    }
    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()),
        horizontalAlignment=Alignment.CenterHorizontally){
        VelvetGameBar("Ludo","Two players · Four pieces each · Pass & play",onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal=19.dp),
            horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("♡ Rose",color=colors[0],fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text("✦ Gold",color=colors[1],fontSize=17.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp)
            .clip(RoundedCornerShape(13.dp)).background(p.paper).padding(4.dp)){
            for(r in 0..14){
                Row(Modifier.fillMaxWidth()){
                    for(c in 0..14){
                        val i=ludoPath.indexOf(r to c)
                        val homeRose=r in 0..5&&c in 0..5
                        val homeGold=r in 9..14&&c in 9..14
                        val laneRose=r==7&&c in 1..6
                        val laneGold=r==7&&c in 8..13
                        val center=r in 6..8&&c in 6..8
                        val bg=when{
                            homeRose->Color(0xFF583649)
                            homeGold->Color(0xFF625334)
                            laneRose->Color(0xFFD39DAE)
                            laneGold->Color(0xFFE0C98A)
                            center->Color(0xFF775867)
                            i==0->Color(0xFFDFABBA)
                            i==26->Color(0xFFE0C98A)
                            i>=0->Color(0xFFF2E9E5)
                            else->p.bg
                        }
                        val tokens=pieces.filter{piece->
                            when{
                                piece.progress<0->if(piece.team==0)
                                    r==1+piece.id/2*2 && c==1+piece.id%2*2
                                    else r==10+piece.id/2*2 && c==10+piece.id%2*2
                                piece.progress>=58->r==7 && c==7
                                piece.progress>=52->{
                                    r==7 && c==(if(piece.team==0)1+(piece.progress-52) else 13-(piece.progress-52))
                                }
                                else->ludoPath[destination(piece)]==(r to c)
                            }
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(.5.dp)
                            .clip(RoundedCornerShape(2.dp)).background(bg)
                            .clickable {
                                val active=tokens.firstOrNull{legal(it)}
                                if(active!=null)advance(active)
                            },contentAlignment=Alignment.Center){
                            if(tokens.isNotEmpty()){
                                val token=tokens.first()
                                Box(Modifier.fillMaxSize(.78f).clip(CircleShape)
                                    .background(colors[token.team])
                                    .border(1.dp,p.bg,CircleShape),contentAlignment=Alignment.Center){
                                    if(tokens.size>1)Text("${tokens.size}",fontSize=9.sp,
                                        color=Color(0xFF30212C),fontWeight=FontWeight.Bold)
                                }
                            } else if(i in setOf(13,39))
                                Icon(Icons.Outlined.Star,null,Modifier.size(11.dp),tint=Color(0xFF7D5867))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Text(if(finished>=0)"${if(finished==0)"Rose" else "Gold"} wins! ♡"
             else "${if(turn==0)"Rose" else "Gold"}'s turn",
             color=if(turn==0)colors[0] else colors[1],fontSize=23.sp,fontFamily=FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
            Box(Modifier.size(59.dp).clip(RoundedCornerShape(15.dp)).background(p.paper),
                contentAlignment=Alignment.Center){
                Text(if(dice==0)"⚄" else dice.toString(),color=p.text,fontSize=29.sp,fontWeight=FontWeight.Bold)
            }
            Button(onClick=::roll,enabled=dice==0&&finished<0,
                modifier=Modifier.height(51.dp)){
                Text("Roll dice",fontSize=15.sp)
            }
            TextButton(onClick={showHelp=true}){Icon(Icons.Outlined.Info,null,tint=p.rose)}
        }
        if(dice>0){
            Text("You rolled $dice · Tap an available piece on the board or below.",
                color=p.muted,fontSize=11.sp,textAlign=TextAlign.Center,
                modifier=Modifier.padding(horizontal=16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceEvenly){
            pieces.filter{it.team==turn}.forEach{token->
                val enabled=legal(token)
                OutlinedButton(onClick={advance(token)},enabled=enabled,
                    contentPadding=PaddingValues(horizontal=9.dp),
                    modifier=Modifier.width(76.dp)){
                    Text("Piece ${token.id+1}",fontSize=10.sp,maxLines=1)
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        TextButton(onClick={
            pieces.clear();repeat(4){pieces.add(LudoPiece(0,it,-1));pieces.add(LudoPiece(1,it,-1))}
            turn=0;dice=0;finished=-1;moves=0
        }){Icon(Icons.Outlined.Refresh,null,Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp));Text("New game")}
        Spacer(Modifier.height(20.dp))
    }
    if(showHelp)AlertDialog(onDismissRequest={showHelp=false},
        title={Text("Two-player Ludo rules")},
        text={Text("Roll a 6 to bring a piece out. Move forward by the dice value, capture opponents on unsafe spaces, and roll again after a 6. Reach the center by exact roll with all four pieces to win. This is a local two-player ruleset; online matches are not active yet.")},
        confirmButton={TextButton(onClick={showHelp=false}){Text("Got it")}},
        containerColor=p.paper)
}

@Composable
internal fun DrawGuessGame(onBack:()->Unit){
    val p=VelvetTheme.current
    val prompts=remember{listOf("A tiny cottage","Two penguins dancing","An enormous birthday cake",
        "A flying bicycle","A friendly octopus","A moonlit picnic","A magical teapot",
        "A happy cactus","A castle in the clouds","A cat wearing sunglasses",
        "A rainbow umbrella","A rocket-shaped sandwich","A dragon reading a book")}
    var prompt by remember{mutableStateOf(prompts.random())}
    var promptShown by remember{mutableStateOf(false)}
    val strokes=remember{mutableStateListOf<Pair<Int,List<Offset>>>()}
    var pen by remember {mutableIntStateOf(0)}
    var points by remember {mutableIntStateOf(0)}
    val colors=listOf(p.rose,p.gold,p.text,Color(0xFF9DCFC7),Color(0xFFBBABE9))
    Column(Modifier.fillMaxSize().background(p.bg),horizontalAlignment=Alignment.CenterHorizontally){
        VelvetGameBar("Draw & Guess","Pass & play sketchbook",onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),
            verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(if(promptShown)prompt else "Secret prompt ♡",color=p.text,fontSize=19.sp,
                    fontFamily=FontFamily.Serif)
                Text("Artist peeks; guesser looks away.",color=p.muted,fontSize=11.sp)
            }
            TextButton(onClick={promptShown=!promptShown}) {
                Icon(if(promptShown)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp))
                Text(if(promptShown)"Hide" else "Reveal")
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal=14.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFFF7F0EC))){
            Canvas(Modifier.fillMaxSize().pointerInput(pen) {
                detectDragGestures(onDragStart={strokes.add(pen to listOf(it))},
                    onDrag={change,_->
                        change.consume()
                        if(strokes.isNotEmpty())strokes[strokes.lastIndex]=
                            strokes.last().let{it.first to it.second+change.position}
                    })
            }){
                strokes.forEach{(index,dots)->
                    val color=colors[index.coerceIn(colors.indices)]
                    if(dots.size==1)drawCircle(color,5f,dots.first())
                    for(i in 1 until dots.size)
                        drawLine(color,dots[i-1],dots[i],strokeWidth=10f,
                            cap=androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            if(strokes.isEmpty())
                Text("A canvas for two ♡",color=Color(0xFF937887),
                    modifier=Modifier.align(Alignment.Center),fontSize=16.sp)
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=17.dp),
            horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
            colors.forEachIndexed{index,color->
                Box(Modifier.size(39.dp).clip(CircleShape).background(color)
                    .border(if(pen==index)3.dp else 1.dp,
                        if(pen==index)p.text else p.border,CircleShape)
                    .clickable{pen=index})
            }
            IconButton(onClick={if(strokes.isNotEmpty())strokes.removeAt(strokes.lastIndex)}) {
                Icon(Icons.Outlined.Undo,"Undo",tint=p.text)
            }
            IconButton(onClick={strokes.clear()}) {
                Icon(Icons.Outlined.DeleteOutline,"Clear",tint=p.rose)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={points++;prompt=prompts.random();strokes.clear();promptShown=false}){
                Text("Guessed it! +1")
            }
            Button(onClick={prompt=prompts.random();strokes.clear();promptShown=false}){
                Text("Next prompt →")
            }
        }
        Text("$points correct · Live drawing sync comes with pairing",
            color=p.muted,fontSize=11.sp,modifier=Modifier.padding(vertical=12.dp))
    }
}
