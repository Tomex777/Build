package dev.velvet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val gameBg get() = VelvetTheme.current.bg
private val gameSurface get() = VelvetTheme.current.paper
private val gameText get() = VelvetTheme.current.text
private val gameMuted get() = VelvetTheme.current.muted
private val gameAccent get() = VelvetTheme.current.rose
private val gameGold get() = VelvetTheme.current.gold

@Composable
internal fun VelvetGameBar(title:String,subtitle:String,onBack:()->Unit){
    Row(Modifier.fillMaxWidth().padding(start=9.dp,end=17.dp,top=9.dp,bottom=12.dp),
        verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onBack,modifier=Modifier.size(46.dp).clip(CircleShape)
            .background(VelvetTheme.current.raised)) {
            Icon(Icons.Outlined.ArrowBack,"Back to games",Modifier.size(26.dp),tint=gameText)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(title,color=gameText,fontFamily=FontFamily.Serif,fontSize=27.sp,fontWeight=FontWeight.SemiBold)
            Text(subtitle,color=gameMuted,fontSize=11.sp)
        }
    }
}

@Composable
internal fun ConnectFourGame(onBack:()->Unit,onOtherGame:()->Unit) {
    val cells=remember {mutableStateListOf<Int>().apply{repeat(42){add(0)}}}
    var turn by remember {mutableIntStateOf(1)}
    var winner by remember {mutableIntStateOf(0)}
    var gamesRose by remember {mutableIntStateOf(0)}
    var gamesGold by remember {mutableIntStateOf(0)}
    var draws by remember {mutableIntStateOf(0)}
    val rose=Color(0xFFE8A9C0);val gold=Color(0xFFFFD28A)
    fun victory(value:Int):Boolean {
        for(row in 0..5) for(col in 0..6) {
            if(cells[row*7+col]!=value)continue
            for((dx,dy) in listOf(1 to 0,0 to 1,1 to 1,1 to -1)){
                if((1..3).all { d ->
                    val x=col+dx*d;val y=row+dy*d
                    x in 0..6 && y in 0..5 && cells[y*7+x]==value
                })return true
            }
        }
        return false
    }
    fun drop(col:Int) {
        if(winner!=0)return
        for(row in 5 downTo 0) {
            val i=row*7+col
            if(cells[i]==0) {
                cells[i]=turn
                when{
                    victory(turn)->{winner=turn;if(turn==1)gamesRose++ else gamesGold++}
                    cells.none{it==0}->{winner=3;draws++}
                    else->turn=3-turn
                }
                break
            }
        }
    }
    Column(Modifier.fillMaxSize().background(gameBg).verticalScroll(rememberScrollState()),
        horizontalAlignment=Alignment.CenterHorizontally) {
        VelvetGameBar("Connect Four","Two players · Pass & play",onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("♡ Rose  $gamesRose",color=rose,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text("✦ Gold  $gamesGold",color=gold,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(22.dp))
        Text(when(winner){1->"Rose wins! ♡";2->"Gold wins! ✦";3->"It's a tie!";else->if(turn==1)"Rose's turn ♡" else "Gold's turn ✦"},
            color=gameText,fontFamily=FontFamily.Serif,fontSize=25.sp)
        Text("Tap any column to drop your piece.",color=gameMuted,fontSize=11.sp)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal=11.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF343F64)).padding(7.dp)) {
            for(row in 0..5){
                Row(Modifier.fillMaxWidth()) {
                    for(col in 0..6){
                        val value=cells[row*7+col]
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)
                            .clip(CircleShape).background(when(value){1->rose;2->gold;else->Color(0xFF172036)})
                            .border(1.dp,Color.White.copy(alpha=.16f),CircleShape)
                            .clickable(enabled=winner==0){drop(col)},contentAlignment=Alignment.Center) {
                            if(value!=0)Box(Modifier.fillMaxSize(.32f).clip(CircleShape)
                                .background(Color.White.copy(alpha=.24f)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(23.dp))
        Button(onClick={for(i in cells.indices)cells[i]=0;winner=0;turn=1},
            shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(.65f)) {
            Icon(Icons.Outlined.Refresh,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("New round")
        }
        Spacer(Modifier.height(20.dp))
        QuickGameFooter(onOtherGame)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun QuickGameFooter(onOtherGame:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=18.dp).clip(RoundedCornerShape(18.dp))
        .background(gameSurface).clickable(onClick=onOtherGame)
        .padding(15.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Outlined.SportsEsports,null,tint=gameAccent)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("In the mood for something else?",color=gameText,fontSize=14.sp)
            Text("Explore more games for two",color=gameMuted,fontSize=11.sp)
        }
        Icon(Icons.Outlined.ChevronRight,null,tint=gameAccent)
    }
}

/** Local chess engine: detects legal moves, checks, mate, stalemate, castling and en passant.
 * Promotion auto-queens. Designed for pass-and-play, not a substitute for server move verification.
 */
private data class ChessStep(val from:Int,val to:Int,val promotion:Char?=null)
private data class ChessPosition(
    val squares:List<Char>, val white:Boolean=true, val castle:String="KQkq",
    val ep:Int=-1, val halfmove:Int=0
) {
    private fun own(p:Char):Boolean=p!='.' && p.isUpperCase()==white
    private fun opponent(p:Char):Boolean=p!='.' && p.isUpperCase()!=white
    private fun inBounds(r:Int,c:Int)=r in 0..7 && c in 0..7
    private val knight= listOf(1 to 2,1 to -2,-1 to 2,-1 to -2,2 to 1,2 to -1,-2 to 1,-2 to -1)
    private val bishop= listOf(1 to 1,1 to -1,-1 to 1,-1 to -1)
    private val rook= listOf(1 to 0,-1 to 0,0 to 1,0 to -1)
    private fun ray(index:Int,dirs:List<Pair<Int,Int>>,attacks:Boolean):List<Int>{
        val out=mutableListOf<Int>()
        for((dr,dc) in dirs){
            var r=index/8+dr;var c=index%8+dc
            while(inBounds(r,c)){
                val to=r*8+c
                if(attacks || !own(squares[to]))out.add(to)
                if(squares[to]!='.')break
                r+=dr;c+=dc
            }
        }
        return out
    }
    fun attacked(square:Int,byWhite:Boolean):Boolean {
        for(i in squares.indices){
            val p=squares[i]
            if(p=='.'||p.isUpperCase()!=byWhite)continue
            val r=i/8;val c=i%8
            val targets=when(p.lowercaseChar()){
                'p'->listOf(r+(if(byWhite)-1 else 1) to (c-1),r+(if(byWhite)-1 else 1) to (c+1))
                    .filter{inBounds(it.first,it.second)}.map{it.first*8+it.second}
                'n'->knight.map{r+it.first to c+it.second}.filter{inBounds(it.first,it.second)}
                    .map{it.first*8+it.second}
                'b'->ray(i,bishop,true)
                'r'->ray(i,rook,true)
                'q'->ray(i,bishop+rook,true)
                'k'->(bishop+rook).map{r+it.first to c+it.second}
                    .filter{inBounds(it.first,it.second)}.map{it.first*8+it.second}
                else->emptyList()
            }
            if(square in targets)return true
        }
        return false
    }
    fun inCheck(whiteKing:Boolean):Boolean {
        val king=squares.indexOf(if(whiteKing)'K' else 'k')
        return king<0 || attacked(king,!whiteKing)
    }
    private fun pseudoMoves(from:Int):List<ChessStep>{
        val p=squares[from]
        if(!own(p))return emptyList()
        val row=from/8;val col=from%8
        val tos=mutableListOf<Int>()
        when(p.lowercaseChar()){
            'p'->{
                val dir=if(white)-1 else 1
                val next=row+dir
                if(inBounds(next,col) && squares[next*8+col]=='.'){
                    tos.add(next*8+col)
                    if(row==(if(white)6 else 1)&&squares[(row+dir*2)*8+col]=='.')
                        tos.add((row+dir*2)*8+col)
                }
                for(dc in listOf(-1,1)){
                    val c=col+dc
                    if(inBounds(next,c)){
                        val dest=next*8+c
                        if(opponent(squares[dest])&&squares[dest].lowercaseChar()!='k'||dest==ep)tos.add(dest)
                    }
                }
            }
            'n'->tos.addAll(knight.map{row+it.first to col+it.second}
                .filter{inBounds(it.first,it.second)}
                .map{it.first*8+it.second}.filter{!own(squares[it])&&squares[it].lowercaseChar()!='k'})
            'b'->tos.addAll(ray(from,bishop,false))
            'r'->tos.addAll(ray(from,rook,false))
            'q'->tos.addAll(ray(from,bishop+rook,false))
            'k'->{
                tos.addAll((bishop+rook).map{row+it.first to col+it.second}
                    .filter{inBounds(it.first,it.second)}
                    .map{it.first*8+it.second}.filter{!own(squares[it])&&squares[it].lowercaseChar()!='k'})
                if(!inCheck(white)){
                    val base=if(white)56 else 0
                    val kingStart=base+4
                    if(from==kingStart){
                        val right=if(white)'K' else 'k'
                        val left=if(white)'Q' else 'q'
                        if(right in castle && squares[base+5]=='.' && squares[base+6]=='.' &&
                            squares[base+7]==(if(white)'R' else 'r') &&
                            !attacked(base+5,!white) && !attacked(base+6,!white))tos.add(base+6)
                        if(left in castle && squares[base+1]=='.' && squares[base+2]=='.' &&
                            squares[base+3]=='.' && squares[base]==(if(white)'R' else 'r') &&
                            !attacked(base+3,!white) && !attacked(base+2,!white))tos.add(base+2)
                    }
                }
            }
        }
        return tos.filter {squares[it].lowercaseChar()!='k'}.map{ChessStep(from,it)}
    }
    fun legalFrom(from:Int):List<ChessStep> =
        pseudoMoves(from).filter{!playUnchecked(it).inCheck(white)}
    fun allLegal():List<ChessStep> = squares.indices.flatMap(::legalFrom)
    fun move(step:ChessStep):ChessPosition? =
        if(step in legalFrom(step.from))playUnchecked(step) else null
    private fun playUnchecked(step:ChessStep):ChessPosition{
        val board=squares.toMutableList()
        val p=board[step.from];val captured=board[step.to]
        board[step.from]='.'
        val endRow=step.to/8
        board[step.to]=if(p.lowercaseChar()=='p' && endRow==(if(white)0 else 7))
            if(white)'Q' else 'q' else p
        var available=castle
        if(p=='K')available=available.replace("K","").replace("Q","")
        if(p=='k')available=available.replace("k","").replace("q","")
        when(step.from){0->available=available.replace("q","");7->available=available.replace("k","")
            56->available=available.replace("Q","");63->available=available.replace("K","")}
        when(step.to){0->available=available.replace("q","");7->available=available.replace("k","")
            56->available=available.replace("Q","");63->available=available.replace("K","")}
        if(p.lowercaseChar()=='k' && abs(step.to-step.from)==2){
            val row=step.from/8
            if(step.to>step.from){board[row*8+5]=board[row*8+7];board[row*8+7]='.'}
            else {board[row*8+3]=board[row*8];board[row*8]='.'}
        }
        if(p.lowercaseChar()=='p' && step.to==ep && captured=='.'){
            board[step.to+(if(white)8 else -8)]='.'
        }
        val nextEp=if(p.lowercaseChar()=='p' && abs(step.to-step.from)==16)
            (step.from+step.to)/2 else -1
        return ChessPosition(board,!white,available,nextEp,
            if(p.lowercaseChar()=='p'||captured!='.')0 else halfmove+1)
    }
    companion object{
        fun initial()=ChessPosition(
            ("rnbqkbnr"+"pppppppp"+"........".repeat(4)+"PPPPPPPP"+"RNBQKBNR").toList()
        )
    }
}
private val chessSymbols=mapOf('K' to "♔",'Q' to "♕",'R' to "♖",'B' to "♗",'N' to "♘",'P' to "♙",
    'k' to "♚",'q' to "♛",'r' to "♜",'b' to "♝",'n' to "♞",'p' to "♟")
@Composable
internal fun VelvetChessGame(onBack:()->Unit) {
    var position by remember {mutableStateOf(ChessPosition.initial())}
    val history=remember {mutableStateListOf<ChessPosition>()}
    var selected by remember {mutableIntStateOf(-1)}
    val moves=remember(position,selected){if(selected>=0)position.legalFrom(selected).map{it.to}.toSet() else emptySet()}
    val legal=remember(position){position.allLegal()}
    val check=remember(position){position.inCheck(position.white)}
    val gameOver=legal.isEmpty()
    val title=when{
        gameOver && check -> "Checkmate! ${if(position.white)"Black" else "White"} wins ♡"
        gameOver -> "Stalemate · A draw"
        check -> "${if(position.white)"White" else "Black"} is in check"
        else -> "${if(position.white)"White" else "Black"} to move"
    }
    Column(Modifier.fillMaxSize().background(gameBg).verticalScroll(rememberScrollState()),
        horizontalAlignment=Alignment.CenterHorizontally){
        VelvetGameBar("Chess","Pass & play · Legal moves & check detection",onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal=21.dp).clip(RoundedCornerShape(14.dp))
            .background(gameSurface).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Text("♚",fontSize=29.sp,color=gameGold)
            Spacer(Modifier.width(12.dp))
            Column {Text("Black",color=gameText,fontSize=14.sp)
                Text(if(!position.white)"Playing now" else "Waiting",color=gameMuted,fontSize=11.sp)}
        }
        Spacer(Modifier.height(15.dp))
        Text(title,color=if(check)gameGold else gameAccent,fontSize=18.sp,
            fontFamily=FontFamily.Serif,modifier=Modifier.padding(horizontal=10.dp),
            textAlign=TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp)
            .clip(RoundedCornerShape(9.dp)).border(2.dp,gameGold,RoundedCornerShape(9.dp))){
            for(row in 0..7) {
                Row(Modifier.fillMaxWidth()){
                    for(col in 0..7){
                        val index=row*8+col
                        val piece=position.squares[index]
                        val square=if((row+col)%2==0)Color(0xFFF0DACC) else Color(0xFF9B7187)
                        val selectedBg=when{
                            index==selected->Color(0xFFD1C060)
                            index in moves->Color(0xFFC7B46A)
                            else->square
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).background(selectedBg)
                            .clickable(enabled=!gameOver){
                                if(index in moves && selected>=0) {
                                    val next=position.move(ChessStep(selected,index))
                                    if(next!=null){history.add(position);position=next}
                                    selected=-1
                                } else selected=if(selected==index) -1
                                else if(piece!='.'&&piece.isUpperCase()==position.white)index else -1
                            },contentAlignment=Alignment.Center){
                            Text(chessSymbols[piece]?:"",fontSize=34.sp,
                                color=if(piece.isUpperCase())Color(0xFFFFFCF4) else Color(0xFF241E2B),
                                fontWeight=FontWeight.Bold)
                            if(index in moves && piece=='.') Box(Modifier.size(11.dp).clip(CircleShape)
                                .background(Color(0xFF563B3A).copy(alpha=.48f)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=21.dp).clip(RoundedCornerShape(14.dp))
            .background(gameSurface).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Text("♔",fontSize=29.sp,color=gameText)
            Spacer(Modifier.width(12.dp))
            Column {Text("White",color=gameText,fontSize=14.sp)
                Text(if(position.white)"Playing now" else "Waiting",color=gameMuted,fontSize=11.sp)}
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick={
                if(history.isNotEmpty()) {position=history.removeAt(history.lastIndex);selected=-1}
            },enabled=history.isNotEmpty()){
                Icon(Icons.Outlined.Undo,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Undo")
            }
            Button(onClick={position=ChessPosition.initial();history.clear();selected=-1}) {
                Icon(Icons.Outlined.Refresh,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("New game")
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("Promotions become queens. Local board; online matches need pairing.",
            color=gameMuted,fontSize=11.sp,textAlign=TextAlign.Center,
            modifier=Modifier.padding(horizontal=20.dp))
        Spacer(Modifier.height(24.dp))
    }
}
