@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.velvet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CornerSize
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private object V {
    val bg = Color(0xFF171019)
    val paper = Color(0xFF281C2A)
    val raised = Color(0xFF352639)
    val border = Color(0xFF574052)
    val rose = Color(0xFFF2BDCC)
    val roseDark = Color(0xFFB87694)
    val text = Color(0xFFF8E8E4)
    val muted = Color(0xFFBCA4B4)
    val gold = Color(0xFFE1BDA3)
}
private val round = RoundedCornerShape(22.dp)
private enum class Page { HOME, CHAT, GAMES, STORY, US, CHAT_INFO, DECK, TTT }
private data class ChatMessage(val id: Int, val body: String, val mine: Boolean, val time: String, val quoted: String? = null, val pinned: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(23,16,25)
        window.navigationBarColor = android.graphics.Color.rgb(23,16,25)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = V.rose, background = V.bg, surface = V.paper, onSurface = V.text)) {
                VelvetApp()
            }
        }
    }
}

@Composable
private fun VelvetApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    var ownerName by remember { mutableStateOf("You") }
    var partnerName by remember { mutableStateOf("Your love") } // Replaced after pairing.
    var ownEdit by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf(ownerName) }
    val messages = remember { mutableStateListOf(
        ChatMessage(1, "I saved a little moment for us ♡", false, "9:38"),
        ChatMessage(2, "I want us to remember this feeling.", true, "9:40"),
        ChatMessage(3, "Then let's keep making memories.", false, "9:41", "I want us to remember this feeling.")
    ) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var questionTab by remember { mutableStateOf("Heart to heart") }
    val immersive = page in setOf(Page.CHAT, Page.CHAT_INFO, Page.DECK, Page.TTT)
    BackHandler(enabled = immersive) { page = when(page) { Page.CHAT_INFO -> Page.CHAT; Page.DECK, Page.TTT -> Page.GAMES; else -> Page.HOME } }

    Column(Modifier.fillMaxSize().background(V.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.HOME -> HomeScreen(ownerName = ownerName, openChat = { page = Page.CHAT }, openGames = { page = Page.GAMES })
                Page.CHAT -> ChatScreen(messages, partnerName, replyTo, onReply = { replyTo = it }, onDismissReply = { replyTo = null }, onInfo = { page = Page.CHAT_INFO }, onBack = { page = Page.HOME }, onSend = { body ->
                    if (body.isNotBlank()) { messages.add(ChatMessage((messages.maxOfOrNull { it.id } ?: 0) + 1, body.trim(), true, SimpleDateFormat("H:mm", Locale.getDefault()).format(Date()), replyTo?.body)); replyTo = null }
                })
                Page.CHAT_INFO -> ChatInfoScreen(partnerName, messages, onBack = { page = Page.CHAT })
                Page.GAMES -> GamesScreen(onDeck = { tab -> questionTab = tab; page = Page.DECK }, onTTT = { page = Page.TTT })
                Page.DECK -> QuestionDeckScreen(questionTab, onBack = { page = Page.GAMES }, onSend = { text ->
                    messages.add(ChatMessage((messages.maxOfOrNull { it.id } ?: 0) + 1, text, true, "Now")); page = Page.CHAT
                })
                Page.TTT -> TicTacToeScreen(onBack = { page = Page.GAMES })
                Page.STORY -> StoryScreen()
                Page.US -> UsScreen(ownerName, partnerName, onOwnProfile = { profileDraft = ownerName; ownEdit = true })
            }
        }
        if (!immersive) MainNav(page) { page = it }
    }
    if (ownEdit) AlertDialog(
        onDismissRequest = { ownEdit = false },
        title = { Text("Your profile", color = V.text) },
        text = { Column {
            Text("Only you can change your information. Your partner manages their own profile.", color = V.muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp)); OutlinedTextField(value = profileDraft, onValueChange = { profileDraft = it }, label = { Text("Your name") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if(profileDraft.isNotBlank()) ownerName = profileDraft.trim(); ownEdit = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { ownEdit = false }) { Text("Cancel") } },
        containerColor = V.paper
    )
}

@Composable
private fun SectionLabel(text: String) { Text(text.uppercase(), color = V.gold, fontSize = 10.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.SemiBold) }
@Composable
private fun Serif(text: String, size: Int, modifier: Modifier = Modifier, color: Color = V.text) { Text(text, modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Serif, lineHeight = (size * 1.13).sp) }
@Composable
private fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.clip(round).background(V.paper).border(1.dp, V.border, round).padding(18.dp), content = content) }
@Composable
private fun Avatar(size: Int = 40, partner: Boolean = true) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(if(partner) listOf(Color(0xFFBD8298),Color(0xFF5A344C)) else listOf(Color(0xFF786180),Color(0xFF453047)))).border(1.dp,V.rose.copy(alpha=.4f),CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Person, "Profile photo not set", tint = V.text, modifier = Modifier.size((size*.51).dp))
    }
}
@Composable
private fun MainNav(page: Page, go: (Page) -> Unit) {
    val nav = listOf(Triple(Page.HOME,"Home",Icons.Outlined.Home), Triple(Page.CHAT,"Chat",Icons.Outlined.ChatBubbleOutline), Triple(Page.GAMES,"Games",Icons.Outlined.SportsEsports), Triple(Page.STORY,"Our Story",Icons.Outlined.Collections), Triple(Page.US,"Us",Icons.Outlined.FavoriteBorder))
    Row(Modifier.fillMaxWidth().background(V.paper).padding(horizontal=4.dp,vertical=7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        nav.forEach { (p, label, icon) ->
            val active = page == p
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(if(active) V.raised else Color.Transparent).combinedClickable(onClick = { go(p) }).padding(vertical=7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, Modifier.size(21.dp), tint = if(active) V.rose else V.muted)
                Spacer(Modifier.height(3.dp)); Text(label, color = if(active) V.rose else V.muted, fontSize=10.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(ownerName: String, openChat: () -> Unit, openGames: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var heartCount by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Serif("Velvet", 31); Row(verticalAlignment = Alignment.CenterVertically) { Avatar(32,false); Spacer(Modifier.width(5.dp)); Avatar(32,true) }
        }
        Spacer(Modifier.height(22.dp))
        Text("Hello, $ownerName. Make today a little sweeter.", color=V.muted, fontSize=13.sp)
        Spacer(Modifier.height(18.dp))
        Tile(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(V.raised,V.paper)),round)) {
            Box(Modifier.fillMaxWidth().height(162.dp), contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c=Offset(size.width/2,size.height/2)
                    drawCircle(V.rose.copy(alpha=.07f),radius=size.minDimension*.49f,center=c)
                    drawCircle(V.rose.copy(alpha=.08f),radius=size.minDimension*.33f,center=c,style=Stroke(width=1.5f))
                }
                HeartDrawing(Modifier.size(125.dp).combinedClickable(onClick={heartCount++; haptics.performHapticFeedback(HapticFeedbackType.LongPress)},onLongClick={heartCount+=2; haptics.performHapticFeedback(HapticFeedbackType.LongPress)}))
            }
            Text("Tap to feel a pulse · hold for longer",Modifier.fillMaxWidth(), textAlign=TextAlign.Center,color=V.muted,fontSize=11.sp)
            Spacer(Modifier.height(12.dp)); SectionLabel("OUR STORY, ONE DAY AT A TIME")
            Spacer(Modifier.height(12.dp)); Serif("Our little\nforever.",43,color=V.rose)
            Spacer(Modifier.height(9.dp))
            Text("A place for the small gestures, the long conversations, and everything that feels like us.",color=V.muted,fontSize=13.sp,lineHeight=20.sp)
            Spacer(Modifier.height(15.dp))
            OutlinedButton(onClick=openChat) { Text("Go to our chat",fontSize=12.sp); Spacer(Modifier.width(7.dp)); Icon(Icons.Outlined.ArrowForward,null,Modifier.size(16.dp)) }
        }
        if(heartCount>0) { Spacer(Modifier.height(8.dp)); Text("A pulse was felt on this phone. Two-phone heartbeat delivery comes with pairing.",color=V.rose,fontSize=11.sp) }
        Spacer(Modifier.height(27.dp)); SectionLabel("SMALL GESTURES, BIG FEELINGS")
        Spacer(Modifier.height(8.dp)); Serif("Little things.",31)
        Text("The little ways to say ‘I'm here.’",color=V.muted,fontSize=12.sp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            LittleThing("A heartbeat","A pulse to their phone",Icons.Outlined.FavoriteBorder,Modifier.weight(1f)) { heartCount++;haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            LittleThing("Warm hug","For when words aren't enough",Icons.Outlined.VolunteerActivism,Modifier.weight(1f)) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            LittleThing("Thinking of you","A little reminder",Icons.Outlined.AutoAwesome,Modifier.weight(1f)) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            LittleThing("Goodnight","End the day softly",Icons.Outlined.NightsStay,Modifier.weight(1f)) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        }
        Spacer(Modifier.height(27.dp)); SectionLabel("THE DAYS THAT MAKE US")
        Spacer(Modifier.height(8.dp)); Serif("Just us.",30)
        Spacer(Modifier.height(14.dp))
        Tile(Modifier.fillMaxWidth()) {
            SectionLabel("TONIGHT'S LITTLE THING"); Spacer(Modifier.height(9.dp))
            Serif("A question, just for us.",25); Spacer(Modifier.height(7.dp))
            Text("Maybe you'll learn something about each other that you never knew.",color=V.muted,fontSize=13.sp,lineHeight=20.sp)
            TextButton(onClick=openGames) { Text("Pick a question →",color=V.rose) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HeartDrawing(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val p=Path().apply {
            moveTo(w*.5f,h*.87f)
            cubicTo(w*.39f,h*.77f,w*.07f,h*.56f,w*.07f,h*.35f)
            cubicTo(w*.07f,h*.09f,w*.37f,h*.06f,w*.5f,h*.25f)
            cubicTo(w*.63f,h*.06f,w*.93f,h*.09f,w*.93f,h*.35f)
            cubicTo(w*.93f,h*.56f,w*.61f,h*.77f,w*.5f,h*.87f)
            close()
        }
        drawPath(p, brush=Brush.linearGradient(listOf(Color(0xFFFFDDE0),Color(0xFFE4A1B4))))
        val shine=Path().apply { moveTo(w*.2f,h*.37f);cubicTo(w*.2f,h*.19f,w*.35f,h*.17f,w*.43f,h*.27f) }
        drawPath(shine,Color.White.copy(alpha=.64f),style=Stroke(width=w*.036f))
    }
}

@Composable
private fun LittleThing(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(round).background(V.paper).border(1.dp,V.border,round).combinedClickable(onClick=onClick).padding(13.dp)) {
        Icon(icon,label,tint=V.rose,modifier=Modifier.size(27.dp))
        Spacer(Modifier.height(14.dp));Text(label,color=V.text,fontSize=13.sp,fontWeight=FontWeight.Medium)
        Spacer(Modifier.height(4.dp));Text(description,color=V.muted,fontSize=10.sp,maxLines=2,lineHeight=14.sp)
    }
}

@Composable
private fun FlameChip() {
    var show by remember { mutableStateOf(false) }
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(V.raised).combinedClickable(onClick={show=true}).padding(horizontal=9.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            val w=size.width;val h=size.height
            val p=Path().apply { moveTo(w*.54f,0f); cubicTo(w*.69f,h*.23f,w*.93f,h*.41f,w*.91f,h*.65f); cubicTo(w*.89f,h*.93f,w*.67f,h,w*.48f,h);cubicTo(w*.22f,h,w*.08f,h*.77f,w*.11f,h*.56f);cubicTo(w*.13f,h*.37f,w*.32f,h*.26f,w*.38f,h*.10f);cubicTo(w*.40f,h*.33f,w*.52f,h*.37f,w*.54f,0f);close() }
            drawPath(p,brush=Brush.verticalGradient(listOf(V.rose,Color(0xFFCE6488))))
            val inner=Path().apply{moveTo(w*.50f,h*.42f);cubicTo(w*.70f,h*.62f,w*.71f,h*.86f,w*.49f,h*.9f);cubicTo(w*.30f,h*.82f,w*.34f,h*.65f,w*.5f,h*.42f);close()}; drawPath(inner,V.paper.copy(alpha=.62f))
        }
        Spacer(Modifier.width(5.dp));Text("0",color=V.rose,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
    if (show) AlertDialog(onDismissRequest={show=false},title={Text("Our talking streak")},text={Text("Your streak starts when both of you exchange messages on consecutive days. Sync and streak tracking will be enabled after account pairing.")},confirmButton={TextButton(onClick={show=false}){Text("Close")}},containerColor=V.paper)
}

@Composable
private fun ChatScreen(messages: MutableList<ChatMessage>, partner: String, replyTo: ChatMessage?, onReply: (ChatMessage) -> Unit, onDismissReply: () -> Unit, onInfo: () -> Unit, onBack: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editBody by remember { mutableStateOf("") }
    var showAttachment by remember { mutableStateOf(false) }
    val listState= rememberLazyListState()
    LaunchedEffect(messages.size) { if(messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().background(V.paper).padding(horizontal=9.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,modifier=Modifier.size(39.dp)){ Icon(Icons.Outlined.ArrowBack,"Back",tint=V.text) }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick=onInfo).padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Avatar(43,true);Spacer(Modifier.width(11.dp))
                Column {Text(partner,color=V.text,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);Text("Away for now",color=V.muted,fontSize=11.sp)}
            }
            FlameChip()
        }
        LazyColumn(state=listState, modifier=Modifier.weight(1f).fillMaxWidth(), contentPadding=PaddingValues(horizontal=6.dp,vertical=15.dp)) {
            item { Text("Today",Modifier.fillMaxWidth().padding(bottom=12.dp),textAlign=TextAlign.Center,color=V.muted,fontSize=11.sp) }
            items(messages,key={it.id}) { msg ->
                MessageRow(msg, onReply={onReply(msg)}, onEdit={editId=msg.id;editBody=msg.body}, onDelete={messages.remove(msg)}, onPin={i ->val k=messages.indexOfFirst{it.id==msg.id};if(k>=0) messages[k]=msg.copy(pinned = i)})
            }
        }
        if(replyTo!=null) Row(Modifier.fillMaxWidth().background(V.raised).padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Replying to ${if(replyTo.mine)"yourself" else partner}",color=V.rose,fontSize=11.sp);Text(replyTo.body,maxLines=1,overflow=TextOverflow.Ellipsis,color=V.text,fontSize=13.sp)}
            IconButton(onClick=onDismissReply){Icon(Icons.Outlined.Close,"Cancel reply",tint=V.muted)}
        }
        Row(Modifier.fillMaxWidth().background(V.paper).padding(horizontal=10.dp,vertical=9.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick={showAttachment=true},modifier=Modifier.size(44.dp)) {Icon(Icons.Outlined.AddCircleOutline,"Attach",tint=V.rose,modifier=Modifier.size(28.dp))}
            Box(Modifier.weight(1f).heightIn(min=43.dp,max=125.dp).clip(RoundedCornerShape(23.dp)).background(V.raised).padding(horizontal=15.dp,vertical=11.dp)) {
                if(draft.isBlank()) Text("Message…",color=V.muted,fontSize=14.sp)
                BasicTextField(value=draft,onValueChange={draft=it},textStyle=androidx.compose.ui.text.TextStyle(color=V.text,fontSize=14.sp),modifier=Modifier.fillMaxWidth())
            }
            IconButton(onClick={if(draft.isNotBlank()){onSend(draft);draft=""}else showAttachment=true},modifier=Modifier.size(45.dp)){
                Icon(if(draft.isNotBlank())Icons.Outlined.Send else Icons.Outlined.Mic,if(draft.isNotBlank())"Send" else "Voice note", tint=V.rose,modifier=Modifier.size(26.dp))
            }
        }
    }
    if(editId!=null) AlertDialog(onDismissRequest={editId=null},title={Text("Edit message")},text={OutlinedTextField(value=editBody,onValueChange={editBody=it})},confirmButton={TextButton(onClick={val ix=messages.indexOfFirst{it.id==editId};if(ix>=0 && editBody.isNotBlank()) messages[ix]=messages[ix].copy(body=editBody.trim());editId=null}){Text("Save")}},dismissButton={TextButton(onClick={editId=null}){Text("Cancel")}},containerColor=V.paper)
    if(showAttachment) AlertDialog(onDismissRequest={showAttachment=false},title={Text("Media and voice notes")},text={Text("The native attachment picker, recording and phone-to-phone delivery will be connected with the backend. This build does not send or store media yet.")},confirmButton={TextButton(onClick={showAttachment=false}){Text("Got it")}},containerColor=V.paper)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(msg:ChatMessage,onReply:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onPin:(Boolean)->Unit) {
    val clipboard=LocalClipboardManager.current
    val haptic=LocalHapticFeedback.current
    var shift by remember(msg.id) { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().pointerInput(msg.id) {
        detectHorizontalDragGestures(onHorizontalDrag={change,amount->change.consume();shift=(shift+amount).coerceIn(0f,160f)}, onDragEnd={if(shift>56f){haptic.performHapticFeedback(HapticFeedbackType.LongPress);onReply()};shift=0f},onDragCancel={shift=0f})
    }.padding(horizontal=8.dp,vertical=4.dp)) {
        if(shift>15f) Icon(Icons.Outlined.Reply,"Swipe to reply",Modifier.align(Alignment.CenterStart).size(25.dp),tint=V.rose)
        Row(Modifier.fillMaxWidth().offset {IntOffset(shift.roundToInt(),0)},horizontalArrangement=if(msg.mine) Arrangement.End else Arrangement.Start) {
            Box {
                Column(Modifier.widthIn(max=292.dp).clip(RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomEnd=if(msg.mine)5.dp else 18.dp,bottomStart=if(msg.mine)18.dp else 5.dp)).background(if(msg.mine)V.raised else V.paper).border(1.dp,V.border.copy(alpha=.6f),RoundedCornerShape(18.dp)).combinedClickable(onClick={},onLongClick={menu=true}).padding(horizontal=13.dp,vertical=9.dp)) {
                    if(msg.quoted!=null) Text(msg.quoted,color=V.rose,fontSize=11.sp,maxLines=2,modifier=Modifier.fillMaxWidth().background(V.bg.copy(alpha=.65f),RoundedCornerShape(7.dp)).padding(9.dp))
                    Text(msg.body,color=V.text,fontSize=14.sp,lineHeight=20.sp)
                    Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically) {if(msg.pinned)Icon(Icons.Outlined.PushPin,"Pinned",Modifier.size(12.dp),tint=V.gold);Text(msg.time,color=V.muted,fontSize=10.sp)}
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false},modifier=Modifier.background(V.paper)) {
                    DropdownMenuItem(text={Text("Copy")},onClick={clipboard.setText(AnnotatedString(msg.body));menu=false},leadingIcon={Icon(Icons.Outlined.ContentCopy,null)})
                    DropdownMenuItem(text={Text(if(msg.pinned)"Unpin" else "Pin")},onClick={onPin(!msg.pinned);menu=false},leadingIcon={Icon(Icons.Outlined.PushPin,null)})
                    if(msg.mine)DropdownMenuItem(text={Text("Edit")},onClick={menu=false;onEdit()},leadingIcon={Icon(Icons.Outlined.Edit,null)})
                    DropdownMenuItem(text={Text("Delete from this preview")},onClick={menu=false;onDelete()},leadingIcon={Icon(Icons.Outlined.Delete,null)})
                }
            }
        }
    }
}

@Composable
private fun ChatInfoScreen(partner:String,messages:List<ChatMessage>,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back")};Text("Conversation",color=V.text,fontSize=17.sp)}
        Spacer(Modifier.height(17.dp))
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Avatar(90,true)}
        Spacer(Modifier.height(14.dp)); Text(partner,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=V.text,fontFamily=FontFamily.Serif,fontSize=28.sp)
        Text("Away for now",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=V.muted,fontSize=12.sp)
        Spacer(Modifier.height(26.dp))
        listOf("Shared media" to "Pictures and videos from this chat", "Links" to "Websites shared here", "Documents" to "Files from this conversation", "Audio" to "Voice notes and audio", "Pinned" to "${messages.count{it.pinned}} pinned messages", "Search" to "Find a message", "Wallpaper" to "Chat appearance", "Notifications" to "Conversation alerts").forEach { (name,description) ->
            Tile(Modifier.fillMaxWidth().padding(bottom=9.dp)){Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text(name,color=V.text,fontSize=15.sp);Text(description,color=V.muted,fontSize=11.sp)};Icon(Icons.Outlined.ChevronRight,null,tint=V.rose)}}
        }
        Text("Media is separate from Our Story until someone deliberately saves it.",color=V.muted,fontSize=12.sp,modifier=Modifier.padding(bottom=24.dp,top=9.dp))
    }
}

private val deckCategories = listOf("Heart to heart","Little laughs","Our future","Sweet & silly","Deep talks","Would you rather")
private data class Prompt(val category: String,val question: String)
private val prompts = listOf(
    Prompt("Heart to heart","When do you feel closest to me?"),Prompt("Heart to heart","What tiny thing always reminds you of us?"),Prompt("Heart to heart","What's one memory you want us to make again?"),Prompt("Heart to heart","What part of our story makes you smile instantly?"),Prompt("Heart to heart","When have you felt most understood?"),Prompt("Heart to heart","What do we do that makes ordinary days special?"),Prompt("Heart to heart","What should we make more time for?"),Prompt("Heart to heart","What surprised you about falling for me?"),
    Prompt("Little laughs","Which of us would lose our keys on a date?"),Prompt("Little laughs","What silly nickname would you invent for me?"),Prompt("Little laughs","If we had a secret handshake, what would it be?"),Prompt("Little laughs","What would our imaginary restaurant serve?"),Prompt("Little laughs","Which of us would fall asleep first during a movie?"),Prompt("Little laughs","What is our funniest inside joke?"),
    Prompt("Our future","Where should we take our first spontaneous trip?"),Prompt("Our future","What little tradition should we start?"),Prompt("Our future","What's one skill we could learn together?"),Prompt("Our future","What would our ideal weekend look like?"),Prompt("Our future","What do you want to celebrate together next year?"),Prompt("Our future","What tiny dream should we chase this month?"),Prompt("Our future","How should we decorate our someday corner?"),Prompt("Our future","What song belongs on our future road trip?"),Prompt("Our future","Where should our next photo together be taken?"),
    Prompt("Sweet & silly","If I were a dessert, what would I be?"),Prompt("Sweet & silly","What would you put in a care package for me?"),Prompt("Sweet & silly","What color feels like us?"),Prompt("Sweet & silly","Which fictional couple reminds you of us?"),Prompt("Sweet & silly","What are three words that describe our vibe?"),Prompt("Sweet & silly","How would you describe me to a puppy?"),Prompt("Sweet & silly","Which emoji would we invent together?"),
    Prompt("Deep talks","What's something you wish people understood about you?"),Prompt("Deep talks","When do you feel most supported?"),Prompt("Deep talks","What's a value we both want to protect?"),Prompt("Deep talks","What would help us disagree more gently?"),Prompt("Deep talks","What have you learned about yourself recently?"),Prompt("Deep talks","What does feeling safe together mean to you?"),Prompt("Deep talks","How can we make tough days easier for each other?"),Prompt("Deep talks","What's one habit we can build together?"),Prompt("Deep talks","What helps you feel heard?"),
    Prompt("Would you rather","Would you rather watch sunrise together or stay up for the stars?"),Prompt("Would you rather","Would you rather cook together or explore a new café?"),Prompt("Would you rather","Would you rather swap playlists or write each other notes?"),Prompt("Would you rather","Would you rather dance in the rain or picnic under the sun?"),Prompt("Would you rather","Would you rather take a train trip or a beach holiday?"),Prompt("Would you rather","Would you rather explore a museum or a night market?"),Prompt("Would you rather","Would you rather build a blanket fort or go camping?"),Prompt("Would you rather","Would you rather make a scrapbook or record a mini-film?"),Prompt("Would you rather","Would you rather learn painting or pottery together?")
)
private val cardColors=listOf(Color(0xFFF4B8C7),Color(0xFFB6A4D8),Color(0xFFF3C5A2),Color(0xFFB8D6C7),Color(0xFFF0D99D),Color(0xFFB6CEE4),Color(0xFFD5AFCE),Color(0xFFDBD49D))

@Composable
private fun GamesScreen(onDeck:(String)->Unit,onTTT:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Spacer(Modifier.height(14.dp));SectionLabel("TOGETHER IS THE FUN PART")
        Spacer(Modifier.height(8.dp));Serif("Let's play.",38)
        Text("A little competition. A lot of connection.",fontSize=13.sp,color=V.muted)
        Spacer(Modifier.height(24.dp))
        Tile(Modifier.fillMaxWidth()) {
            SectionLabel("FEATURED DECK");Spacer(Modifier.height(9.dp));Serif("Heart to heart",29,color=V.rose)
            Text("One question at a time, with room for real answers.",color=V.muted,fontSize=13.sp)
            Spacer(Modifier.height(12.dp));Button(onClick={onDeck("Heart to heart")}){Text("Pick a card →")}
        }
        Spacer(Modifier.height(23.dp));SectionLabel("QUESTION GAMES")
        Spacer(Modifier.height(10.dp))
        deckCategories.chunked(2).forEachIndexed {rowI,pair->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {pair.forEachIndexed {i,c->
                val index=rowI*2+i
                Column(Modifier.weight(1f).clip(round).background(cardColors[index]).combinedClickable(onClick={onDeck(c)}).padding(18.dp).height(93.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Icon(Icons.Outlined.Style,null,tint=Color(0xFF4A3142));Text(c,color=Color(0xFF41283D),fontSize=16.sp,fontFamily=FontFamily.Serif,lineHeight=18.sp)
                }
            }};Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp));SectionLabel("PLAY TOGETHER")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GameEntry("Tic-tac-toe","Play locally",true,Modifier.weight(1f),onTTT)
            GameEntry("Connect Four","Multiplayer later",false,Modifier.weight(1f),{})
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GameEntry("Chess","Rules engine later",false,Modifier.weight(1f),{})
            GameEntry("Ludo","Rules engine later",false,Modifier.weight(1f),{})
        }
        Spacer(Modifier.height(10.dp));GameEntry("Draw & Guess","Realtime canvas later",false,Modifier.fillMaxWidth(),{})
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GameEntry(title:String,subtitle:String,enabled:Boolean,modifier:Modifier,click:()->Unit) {
    Column(modifier.clip(round).background(V.paper).border(1.dp,V.border,round).combinedClickable(onClick=click).padding(16.dp)) {
        Icon(if(enabled)Icons.Outlined.Grid3x3 else Icons.Outlined.SportsEsports,null,tint=V.rose)
        Spacer(Modifier.height(24.dp));Text(title,color=V.text,fontSize=17.sp,fontFamily=FontFamily.Serif);Spacer(Modifier.height(4.dp));Text(subtitle,color=V.muted,fontSize=10.sp)
    }
}

@Composable
private fun QuestionDeckScreen(category:String,onBack:()->Unit,onSend:(String)->Unit) {
    val filtered = remember(category) { prompts.filter{it.category==category} }
    var index by remember(category) { mutableIntStateOf(0) }
    var drag by remember(category) { mutableFloatStateOf(0f) }
    var animating by remember(category) { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun moveCard() {if(animating)return;animating=true;scope.launch {val anim=Animatable(drag);anim.animateTo(if(drag<0)-900f else 900f,tween(210)){drag=value};index=(index+1)%filtered.size;drag=0f;animating=false}}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back")};Column(Modifier.weight(1f)){Text(category,color=V.text,fontSize=19.sp,fontFamily=FontFamily.Serif);Text("${index+1} of ${filtered.size} starter questions",color=V.muted,fontSize=10.sp)};Icon(Icons.Outlined.Style,"Cards",tint=V.rose)}
        Spacer(Modifier.height(9.dp));Text("Swipe either way to reveal the next card.",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=V.muted,fontSize=12.sp)
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            QuestionCard(filtered[(index+1)%filtered.size].question,cardColors[(index+1)%cardColors.size],Modifier.fillMaxWidth(.90f).height(366.dp).scale(.95f).offset(y=10.dp))
            QuestionCard(filtered[index].question,cardColors[index%cardColors.size],Modifier.fillMaxWidth(.90f).height(366.dp).offset {IntOffset(drag.roundToInt(),0)}.graphicsLayer{rotationZ=drag/37f}.pointerInput(index,animating) {
                detectHorizontalDragGestures(onHorizontalDrag={change,amount->if(!animating){change.consume();drag=(drag+amount).coerceIn(-700f,700f)}},onDragEnd={if(!animating){if(abs(drag)>90f)moveCard() else scope.launch {val anim=Animatable(drag);anim.animateTo(0f,tween(180)){drag=value}}}},onDragCancel={drag=0f})
            })
        }
        Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={drag=-120f;moveCard()},modifier=Modifier.weight(1f)){Text("Next card")}
            Button(onClick={onSend(filtered[index].question)},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Send,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("Send to Chat")}
        }
    }
}

@Composable
private fun QuestionCard(question:String,color:Color,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(color).border(2.dp,Color(0xFF614158).copy(alpha=.32f),RoundedCornerShape(28.dp)).padding(25.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("VELVET",letterSpacing=2.sp,color=Color(0xFF493044),fontSize=11.sp,fontWeight=FontWeight.Bold);Icon(Icons.Outlined.FavoriteBorder,null,tint=Color(0xFF493044))}
        Text(question,color=Color(0xFF3E283D),fontSize=28.sp,fontFamily=FontFamily.Serif,lineHeight=34.sp,textAlign=TextAlign.Center)
        Text("A little closer, one answer at a time",color=Color(0xFF553847),fontSize=11.sp,textAlign=TextAlign.Center)
    }
}

@Composable
private fun TicTacToeScreen(onBack:()->Unit) {
    val cells=remember { mutableStateListOf("","","","","","","","","") }
    var move by remember { mutableStateOf("♡") }
    val patterns=listOf(listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),listOf(0,4,8),listOf(2,4,6))
    val winner=patterns.firstOrNull{line->cells[line[0]].isNotEmpty()&&cells[line[0]]==cells[line[1]]&&cells[line[1]]==cells[line[2]]}?.let{cells[it[0]]}
    val done=winner!=null||cells.none{it==""}
    Column(Modifier.fillMaxSize().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back")};Serif("Tic-tac-toe",27)}
        Spacer(Modifier.height(13.dp));Text("Local pass-and-play demonstration",color=V.muted,fontSize=12.sp)
        Spacer(Modifier.weight(1f));Serif(when{winner!=null->"$winner wins!";done->"A perfect tie.";else->"$move's turn"},32)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().widthIn(max=360.dp)) {
            repeat(3){r->Row(Modifier.fillMaxWidth()) {repeat(3){c->val k=r*3+c
                Box(Modifier.weight(1f).aspectRatio(1f).padding(5.dp).clip(RoundedCornerShape(17.dp)).background(V.paper).border(1.dp,V.border,RoundedCornerShape(17.dp)).combinedClickable(onClick={if(!done&&cells[k]==""){cells[k]=move;move=if(move=="♡")"✕" else "♡"}}),contentAlignment=Alignment.Center){Text(cells[k],color=V.rose,fontSize=43.sp,fontFamily=FontFamily.Serif)}
            }}}
        }
        Spacer(Modifier.height(20.dp));Button(onClick={for(i in cells.indices)cells[i]="";move="♡"}){Text("New game")};Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StoryScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Spacer(Modifier.height(12.dp));SectionLabel("THE DAYS THAT MAKE US")
        Spacer(Modifier.height(8.dp));Serif("Our Story.",41)
        Spacer(Modifier.height(9.dp));Text("A gallery to hold the moments that matter.",color=V.muted,fontSize=13.sp)
        Spacer(Modifier.height(24.dp))
        Tile(Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PhotoLibrary,"Gallery",Modifier.size(36.dp),tint=V.rose)
            Spacer(Modifier.height(15.dp));Serif("The gallery is yours to choose.",28)
            Spacer(Modifier.height(10.dp))
            Text("Before building the actual shared media library, choose a direction from the separate Our Story HTML layout comparison: Gallery first, Timeline first, or Albums first.",color=V.muted,fontSize=13.sp,lineHeight=20.sp)
        }
        Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f)){Icon(Icons.Outlined.Collections,"Albums",tint=V.rose);Spacer(Modifier.height(12.dp));Text("Albums",color=V.text)}
            Tile(Modifier.weight(1f)){Icon(Icons.Outlined.AutoStories,"Someday",tint=V.rose);Spacer(Modifier.height(12.dp));Text("Someday",color=V.text)}
        }
        Spacer(Modifier.height(14.dp))
        Text("Chat attachments will stay in Chat Media until someone explicitly adds them here. We'll preserve both who sent the original file and who saved it.",color=V.muted,fontSize=12.sp,lineHeight=20.sp)
    }
}

@Composable
private fun UsScreen(owner:String,partner:String,onOwnProfile:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Spacer(Modifier.height(12.dp));SectionLabel("JUST THE TWO OF US")
        Spacer(Modifier.height(8.dp));Serif("Us, always.",40)
        Text("Our little corner of the world.",color=V.muted,fontSize=13.sp)
        Spacer(Modifier.height(21.dp))
        Tile(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.combinedClickable(onClick=onOwnProfile),horizontalAlignment=Alignment.CenterHorizontally){Avatar(72,false);Spacer(Modifier.height(9.dp));Text(owner,color=V.text);Text("Edit your profile",color=V.rose,fontSize=10.sp)}
                HeartDrawing(Modifier.size(33.dp))
                Column(horizontalAlignment=Alignment.CenterHorizontally){Avatar(72,true);Spacer(Modifier.height(9.dp));Text(partner,color=V.text);Text("Their own account",color=V.muted,fontSize=10.sp)}
            }
        }
        Spacer(Modifier.height(18.dp))
        Tile(Modifier.fillMaxWidth()) {
            SectionLabel("ACROSS ANY DISTANCE")
            Spacer(Modifier.height(9.dp));Serif("Two places. One us.",26)
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF4E344B),Color(0xFF302536),Color(0xFF5C3D53)))),contentAlignment=Alignment.Center){
                Canvas(Modifier.fillMaxSize()){val p=Path().apply{moveTo(size.width*.20f,size.height*.70f);cubicTo(size.width*.39f,size.height*.05f,size.width*.57f,size.height*.94f,size.width*.83f,size.height*.31f)};drawPath(p,V.rose.copy(alpha=.66f),style=Stroke(width=3f));drawCircle(V.rose,9f,Offset(size.width*.20f,size.height*.70f));drawCircle(V.rose,9f,Offset(size.width*.83f,size.height*.31f))}
                Text("Location sharing is off",modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp).background(V.paper.copy(alpha=.83f),RoundedCornerShape(9.dp)).padding(7.dp),color=V.text,fontSize=11.sp)
            }
            Spacer(Modifier.height(9.dp));Text("Each person chooses whether to share their city. Neither person can edit the other's location. Live location is not enabled in this build.",color=V.muted,fontSize=12.sp,lineHeight=19.sp)
        }
        Spacer(Modifier.height(18.dp));SectionLabel("OUR STORY IN NUMBERS")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f)){Serif("—",35,color=V.rose);Text("Days together",color=V.muted,fontSize=12.sp)}
            Tile(Modifier.weight(1f)){Serif("0",35,color=V.rose);Text("Talking streak",color=V.muted,fontSize=12.sp)}
        }
        Spacer(Modifier.height(18.dp))
        Tile(Modifier.fillMaxWidth()) { SectionLabel("OUR MILESTONES");Spacer(Modifier.height(12.dp));Serif("Every chapter counts.",23);Spacer(Modifier.height(8.dp));Text("Set your anniversary and shared dates once your accounts are paired.",color=V.muted,fontSize=12.sp) }
        Spacer(Modifier.height(20.dp))
    }
}
