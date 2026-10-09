@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.velvet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import android.app.DatePickerDialog
import java.time.format.DateTimeFormatter
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    val bg get() = VelvetTheme.current.bg
    val paper get() = VelvetTheme.current.paper
    val raised get() = VelvetTheme.current.raised
    val border get() = VelvetTheme.current.border
    val rose get() = VelvetTheme.current.rose
    val roseDark get() = VelvetTheme.current.roseDark
    val text get() = VelvetTheme.current.text
    val muted get() = VelvetTheme.current.muted
    val gold get() = VelvetTheme.current.gold
}
private val round = RoundedCornerShape(22.dp)
private enum class Page { HOME, CHAT, GAMES, STORY, US, CHAT_INFO, DECK, TTT, STUDIO, CALL }
internal enum class MessageKind { TEXT, QUESTION, VOICE }
internal data class ChatMessage(
    val id: Int, val body: String, val mine: Boolean, val time: String,
    val quoted: String? = null, val pinned: Boolean = false,
    val starred: Boolean = false, val deleted: Boolean = false,
    val kind: MessageKind = MessageKind.TEXT, val category: String? = null,
    val cardTone: Int = 0, val questionId: String? = null, val caption: String? = null,
    val voicePath: String? = null, val voiceBars: List<Float> = emptyList(), val durationMs: Long = 0L
)

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
    val ctx = LocalContext.current
    val settings = remember { ctx.getSharedPreferences("velvet_couple_settings", android.content.Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf(Page.HOME) }
    var chatBackTo by remember { mutableStateOf(Page.HOME) }
    var ownerName by remember { mutableStateOf(settings.getString("my_name", "You") ?: "You") }
    val partnerName = "Your love" // Read-only until the partner's own authenticated profile is synced.
    var anniversary by remember { mutableStateOf(settings.getString("anniversary", "") ?: "") }
    var ownEdit by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf(ownerName) }
    val cache=remember {MessageCache(ctx)}
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(cache.load() ?: listOf(
                ChatMessage(1,"I saved a little moment for us ♡",false,"9:38"),
                ChatMessage(2,"I want us to remember this feeling.",true,"9:40"),
                ChatMessage(3,"Then let's keep making memories.",false,"9:41","I want us to remember this feeling.")
            ))
        }
    }
    LaunchedEffect(messages.toList()) {cache.save(messages.toList())}
    val deckPositions = remember { mutableStateMapOf<String,Int>() }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var questionTab by remember { mutableStateOf("Heart to heart") }
    var studioNote by remember { mutableStateOf(settings.getString("studio_note", "You feel like home to me. ♡") ?: "") }
    var videoCall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { VelvetChatStyle.load(ctx) }
    DisposableEffect(page) {
        val activity = ctx as? android.app.Activity
        if(page == Page.CALL && activity != null) {
            val ctrl=androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView)
            ctrl.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            onDispose {ctrl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())}
        } else onDispose {}
    }
    val immersive = page in setOf(Page.CHAT, Page.CHAT_INFO, Page.DECK, Page.TTT, Page.STUDIO, Page.CALL)
    BackHandler(enabled = immersive) {
        page = when(page) {
            Page.CHAT_INFO, Page.CALL -> Page.CHAT
            Page.STUDIO -> Page.US
            Page.CHAT -> chatBackTo
            Page.DECK, Page.TTT -> Page.GAMES
            else -> Page.HOME
        }
    }
    fun sendMessage(msg: ChatMessage) { messages.add(msg.copy(id=(messages.maxOfOrNull { it.id } ?: 0)+1)) }
    Column(Modifier.fillMaxSize().background(V.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f)) {
            when (page) {
                Page.HOME -> HomeScreen(ownerName = ownerName, anniversary = anniversary, note=studioNote,
                    openChat = { chatBackTo = Page.HOME; page = Page.CHAT }, openGames = { page = Page.GAMES })
                Page.CHAT -> ChatScreen(messages, partnerName, replyTo,
                    onReply = { replyTo = it }, onDismissReply = { replyTo = null },
                    onInfo = { page = Page.CHAT_INFO }, onBack = { page = chatBackTo },
                    onCall = { isVideo -> videoCall=isVideo;page=Page.CALL },
                    onSend = { body ->
                        if (body.isNotBlank()) { sendMessage(ChatMessage(0, body.trim(),true,nowTime(),replyTo?.body));replyTo = null }
                    }, onVoice = { clip ->
                        sendMessage(ChatMessage(0,"Voice note",true,nowTime(),replyTo?.body,kind=MessageKind.VOICE,
                            voicePath=clip.path,voiceBars=clip.bars,durationMs=clip.durationMs));replyTo=null
                    })
                Page.CHAT_INFO -> ChatInfoScreen(partnerName, messages, onBack = { page = Page.CHAT })
                Page.GAMES -> GamesScreen(onDeck = { tab -> questionTab = tab; page = Page.DECK }, onTTT = { page = Page.TTT })
                Page.DECK -> QuestionDeckScreen(questionTab, index=deckPositions[questionTab] ?: 0,
                    onIndexChange={deckPositions[questionTab]=it},
                    onBack={page=Page.GAMES},
                    onSend = { prompt,tone,caption ->
                        sendMessage(ChatMessage(0,prompt.question,true,nowTime(),kind=MessageKind.QUESTION,
                            category=prompt.category,cardTone=tone,questionId=prompt.id,caption=caption.takeIf{it.isNotBlank()}))
                    }, onViewChat={chatBackTo=Page.DECK;page=Page.CHAT})
                Page.TTT -> TicTacToeScreen(onBack = { page = Page.GAMES })
                Page.STORY -> GalleryFirstScreen(ownerName)
                Page.STUDIO -> VelvetStudioScreen(note=studioNote,
                    onNoteChange={value->studioNote=value;settings.edit().putString("studio_note",value).apply()},
                    onBack={page=Page.US})
                Page.CALL -> VelvetCallPreview(partnerName,videoCall,onBack={page=Page.CHAT})
                Page.US -> UsScreen(ownerName, partnerName, anniversary=anniversary,
                    onDateChange={date -> anniversary=date;settings.edit().putString("anniversary",date).apply()},
                    onOwnProfile = { profileDraft = ownerName; ownEdit = true },
                    onStudio = { page=Page.STUDIO })
            }
        }
        if (!immersive) MainNav(page) { selected -> chatBackTo=Page.HOME;page=selected }
    }
    if (ownEdit) AlertDialog(
        onDismissRequest = { ownEdit = false },
        title = { Text("Your profile", color = V.text) },
        text = { Column {
            Text("Only you can change your information. Your partner manages their own profile.", color = V.muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp)); OutlinedTextField(value = profileDraft, onValueChange = { profileDraft = it }, label = { Text("Your name") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if(profileDraft.isNotBlank()) {ownerName = profileDraft.trim();settings.edit().putString("my_name",ownerName).apply()}; ownEdit = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { ownEdit = false }) { Text("Cancel") } },
        containerColor = V.paper
    )
}

private fun nowTime():String = SimpleDateFormat("H:mm",Locale.getDefault()).format(Date())

private fun relationshipCounts(anniversary:String):Pair<Long,Long>? {
    val day=runCatching {LocalDate.parse(anniversary)}.getOrNull() ?: return null
    val today=LocalDate.now()
    if(day.isAfter(today))return null
    fun yearly(year:Int):LocalDate {
        val length=YearMonth.of(year,day.month).lengthOfMonth()
        return LocalDate.of(year,day.month,minOf(day.dayOfMonth,length))
    }
    var next=yearly(today.year)
    if(next.isBefore(today))next=yearly(today.year+1)
    return ChronoUnit.DAYS.between(day,today) to ChronoUnit.DAYS.between(today,next)
}

@Composable
private fun CoupleAvatars(size:Int=44, modifier:Modifier=Modifier) {
    // Profile art remains a neutral fallback until each partner uploads their own photo.
    Box(modifier.width((size*1.66f).dp).height((size+2).dp)) {
        Box(Modifier.align(Alignment.CenterStart)){ Avatar(size,false) }
        Box(Modifier.align(Alignment.CenterEnd)){ Avatar(size,true) }
        Box(Modifier.size(17.dp).align(Alignment.BottomCenter).clip(CircleShape)
            .background(V.rose).border(2.dp,V.bg,CircleShape),contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.Favorite,"Together",Modifier.size(11.dp),tint=V.bg)
        }
    }
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
private fun HomeScreen(ownerName: String, anniversary:String, note:String, openChat: () -> Unit, openGames: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var heartCount by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Serif("Velvet", 31); CoupleAvatars(size=43)
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
            relationshipCounts(anniversary)?.let { (together,until) ->
                Spacer(Modifier.height(12.dp)); Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    Text("$together days together",color=V.rose,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(until==0L) "Our anniversary is today ♡" else "$until days to our anniversary", color=V.gold,fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(15.dp))
            OutlinedButton(onClick=openChat) { Text("Go to our chat",fontSize=12.sp); Spacer(Modifier.width(7.dp)); Icon(Icons.Outlined.ArrowForward,null,Modifier.size(16.dp)) }
        }
        if(heartCount>0) { Spacer(Modifier.height(8.dp)); Text("A pulse was felt on this phone. Two-phone heartbeat delivery comes with pairing.",color=V.rose,fontSize=11.sp) }
        if(note.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(V.paper)
                .padding(horizontal=17.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp),tint=V.gold)
                Spacer(Modifier.width(10.dp))
                Text(note,color=V.rose,fontSize=14.sp,fontFamily=FontFamily.Serif)
            }
        }
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
private fun ChatScreen(
    messages: MutableList<ChatMessage>, partner:String,replyTo:ChatMessage?,onReply:(ChatMessage)->Unit,
    onDismissReply:()->Unit,onInfo:()->Unit,onBack:()->Unit,onCall:(Boolean)->Unit,onSend:(String)->Unit,onVoice:(VoiceClip)->Unit
) {
    var draft by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editBody by remember { mutableStateOf("") }
    var showAttachment by remember { mutableStateOf(false) }
    var voiceActive by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val listState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val pinned = messages.filter { it.pinned && !it.deleted }
    LaunchedEffect(messages.size) { if(messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex+1) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().background(V.paper).padding(horizontal=9.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,modifier=Modifier.size(39.dp)){Icon(Icons.Outlined.ArrowBack,"Back",tint=V.text)}
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick=onInfo).padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Avatar(43,true);Spacer(Modifier.width(11.dp))
                Column {Text(partner,color=V.text,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);Text("Away for now",color=V.muted,fontSize=11.sp)}
            }
            IconButton(onClick={onCall(false)},modifier=Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Call,"Voice call preview",tint=V.rose,modifier=Modifier.size(21.dp))
            }
            IconButton(onClick={onCall(true)},modifier=Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Videocam,"Video call preview",tint=V.rose,modifier=Modifier.size(22.dp))
            }
            FlameChip()
        }
        if(pinned.isNotEmpty()) {
            val top=pinned.last()
            Row(Modifier.fillMaxWidth().background(V.raised).clickable {
                val pos=messages.indexOfFirst{it.id==top.id}
                if(pos>=0)scope.launch{listState.animateScrollToItem(pos+1)}
            }.padding(horizontal=15.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.PushPin,"Pinned message",Modifier.size(17.dp),tint=V.rose)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {Text("Pinned message",color=V.rose,fontSize=11.sp,fontWeight=FontWeight.Medium)
                    Text(if(top.kind==MessageKind.QUESTION)"Question · ${top.body}" else top.body,color=V.text,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                if(pinned.size>1)Text("${pinned.size}",color=V.gold,fontSize=12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
          ChatWallpaper(Modifier.matchParentSize())
          LazyColumn(state=listState, modifier=Modifier.fillMaxSize(), contentPadding=PaddingValues(horizontal=6.dp,vertical=9.dp)) {
            item {Text("Today",Modifier.fillMaxWidth().padding(bottom=12.dp),textAlign=TextAlign.Center,color=V.muted,fontSize=11.sp)}
            items(messages,key={it.id}) { msg ->
                MessageRow(msg,onReply={onReply(msg)},onEdit={editId=msg.id;editBody=msg.body},
                    onDelete={deleteTarget=msg}, onPin={bool->
                        val k=messages.indexOfFirst{it.id==msg.id};if(k>=0)messages[k]=msg.copy(pinned=bool)
                    },onStar={bool->
                        val k=messages.indexOfFirst{it.id==msg.id};if(k>=0)messages[k]=msg.copy(starred=bool)
                    })
            }
          }
        }
        if(replyTo!=null) Row(Modifier.fillMaxWidth().background(V.raised).padding(horizontal=13.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Replying to ${if(replyTo.mine)"yourself" else partner}",color=V.rose,fontSize=11.sp)
                Text(if(replyTo.deleted)"This message was deleted" else replyTo.body,maxLines=1,overflow=TextOverflow.Ellipsis,color=V.text,fontSize=13.sp)}
            IconButton(onClick=onDismissReply){Icon(Icons.Outlined.Close,"Cancel reply",tint=V.muted)}
        }
        Row(Modifier.fillMaxWidth().background(V.paper).padding(horizontal=8.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!voiceActive) {
                Row(Modifier.weight(1f).heightIn(min=47.dp,max=125.dp).clip(RoundedCornerShape(25.dp))
                    .background(V.raised).padding(horizontal=3.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={showAttachment=true},modifier=Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.SentimentSatisfiedAlt,"Emoji and attachments",tint=V.muted,modifier=Modifier.size(23.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical=9.dp)) {
                        if(draft.isBlank())Text("Message",color=V.muted,fontSize=14.sp)
                        BasicTextField(value=draft,onValueChange={draft=it},
                            textStyle=androidx.compose.ui.text.TextStyle(color=V.text,fontSize=14.sp),
                            modifier=Modifier.fillMaxWidth())
                    }
                    IconButton(onClick={showAttachment=true},modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.AttachFile,"Attachments",tint=V.muted,modifier=Modifier.size(21.dp))
                    }
                    IconButton(onClick={showAttachment=true},modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.PhotoCamera,"Camera",tint=V.muted,modifier=Modifier.size(21.dp))
                    }
                }
            } else Spacer(Modifier.weight(1f))
            if(draft.isNotBlank() && !voiceActive) {
                IconButton(onClick={onSend(draft);draft=""},modifier=Modifier.size(46.dp)
                    .clip(CircleShape).background(V.rose)) {
                    Icon(Icons.Outlined.Send,"Send message",tint=V.bg,modifier=Modifier.size(24.dp))
                }
            } else {
                VoiceHoldControl(onRecorded=onVoice,onActiveChange={voiceActive=it})
            }
        }
    }
    if(editId!=null)AlertDialog(onDismissRequest={editId=null},title={Text("Edit message")},text={OutlinedTextField(value=editBody,onValueChange={editBody=it})},confirmButton={TextButton(onClick={
        val ix=messages.indexOfFirst{it.id==editId};if(ix>=0 && editBody.isNotBlank())messages[ix]=messages[ix].copy(body=editBody.trim());editId=null
    }){Text("Save")}},dismissButton={TextButton(onClick={editId=null}){Text("Cancel")}},containerColor=V.paper)
    deleteTarget?.let{msg->
        AlertDialog(onDismissRequest={deleteTarget=null},title={Text("Delete message?")},text={Text("Delete only on this phone, or replace it with a deleted-message notice for everyone?")},
            confirmButton={Column {
                if(msg.mine)TextButton(onClick={val k=messages.indexOfFirst{it.id==msg.id};if(k>=0)messages[k]=msg.copy(body="",quoted=null,deleted=true,pinned=false,starred=false,voicePath=null);deleteTarget=null}){Text("Delete for everyone")}
                TextButton(onClick={messages.removeAll{it.id==msg.id};deleteTarget=null}){Text("Delete for me")}
            }},dismissButton={TextButton(onClick={deleteTarget=null}){Text("Cancel")}},containerColor=V.paper)
    }
    if(showAttachment)AlertDialog(onDismissRequest={showAttachment=false},title={Text("Chat attachments")},text={Text("Chat attachments will be stored separately from Our Story. Azure uploads need your backend credentials and pairing setup; media cannot be sent to your partner from this offline alpha yet.")},confirmButton={TextButton(onClick={showAttachment=false}){Text("Got it")}},containerColor=V.paper)

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(msg:ChatMessage,onReply:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onPin:(Boolean)->Unit,onStar:(Boolean)->Unit) {
    val clipboard=LocalClipboardManager.current
    val haptic=LocalHapticFeedback.current
    var shift by remember(msg.id){mutableFloatStateOf(0f)}
    var menu by remember {mutableStateOf(false)}
    val shape=VelvetChatStyle.bubbleShape(msg.mine)
    // Gesture detector belongs to the FULL ROW, including the empty space beside the bubble.
    Box(Modifier.fillMaxWidth().heightIn(min=34.dp).pointerInput(msg.id) {
        detectHorizontalDragGestures(onHorizontalDrag={change,amount->change.consume();shift=(shift+amount).coerceIn(0f,155f)},
            onDragEnd={if(shift>52f){haptic.performHapticFeedback(HapticFeedbackType.LongPress);onReply()};shift=0f},onDragCancel={shift=0f})
    }.padding(horizontal=7.dp,vertical=2.dp)) {
        if(shift>15f)Icon(Icons.Outlined.Reply,"Reply",Modifier.align(Alignment.CenterStart).size(22.dp),tint=V.rose)
        Row(Modifier.fillMaxWidth().offset{IntOffset(shift.roundToInt(),0)},horizontalArrangement=if(msg.mine)Arrangement.End else Arrangement.Start){
            Box {
                Column(Modifier.widthIn(max=300.dp).clip(shape)
                    .background(if(msg.mine)VelvetChatStyle.outgoingColor else VelvetChatStyle.incomingColor)
                    .combinedClickable(onClick={},onLongClick={menu=true})
                    .padding(horizontal=11.dp,vertical=7.dp)) {
                    if(msg.deleted) {
                        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Block,null,Modifier.size(14.dp),tint=V.muted);Spacer(Modifier.width(6.dp))
                            Text("This message was deleted",color=V.muted,fontSize=13.sp,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic)}
                    } else {
                        msg.quoted?.let {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                                .background(V.bg.copy(alpha=.65f)).padding(horizontal=8.dp,vertical=5.dp)) {
                                Box(Modifier.width(3.dp).height(24.dp).background(V.rose, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(7.dp))
                                Text(it,color=V.rose,fontSize=11.sp,lineHeight=14.sp,maxLines=2)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        when(msg.kind) {
                            MessageKind.TEXT -> Text(buildAnnotatedString {
                                append(msg.body.trimEnd())
                                if(!msg.starred && !msg.pinned) {
                                    append("   ")
                                    withStyle(SpanStyle(color=V.muted,fontSize=9.sp)) {append(msg.time)}
                                }
                            },color=V.text,fontFamily=VelvetChatStyle.font,fontSize=14.sp,lineHeight=19.sp)
                            MessageKind.QUESTION -> {
                                val tone=cardColors[msg.cardTone.mod(cardColors.size)]
                                Column(Modifier.widthIn(min=205.dp).clip(RoundedCornerShape(15.dp)).background(tone).padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                        Text(msg.category?.uppercase() ?: "QUESTION",color=Color(0xFF50374B),fontSize=10.sp,letterSpacing=1.sp)
                                        Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(16.dp),tint=Color(0xFF50374B))
                                    }
                                    Spacer(Modifier.height(15.dp))
                                    Text(msg.body,color=Color(0xFF3E283D),fontFamily=FontFamily.Serif,fontSize=20.sp,lineHeight=26.sp)
                                    Spacer(Modifier.height(15.dp));Text("Answer by swiping to reply ↗",color=Color(0xFF50374B),fontSize=10.sp)
                                }
                            }
                            MessageKind.VOICE -> VoiceBubble(path=msg.voicePath,bars=msg.voiceBars,durationMs=msg.durationMs)
                        }
                        if(msg.kind==MessageKind.QUESTION && !msg.caption.isNullOrBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(msg.caption,color=V.text,fontSize=14.sp,lineHeight=20.sp)
                        }
                    }
                    if(msg.kind != MessageKind.TEXT || msg.starred || msg.pinned || msg.deleted)
                    Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically){
                        if(msg.starred && !msg.deleted){Icon(Icons.Outlined.Star,"Starred",Modifier.size(13.dp),tint=V.gold);Spacer(Modifier.width(4.dp))}
                        if(msg.pinned && !msg.deleted){Icon(Icons.Outlined.PushPin,"Pinned",Modifier.size(12.dp),tint=V.gold);Spacer(Modifier.width(3.dp))}
                        Text(msg.time,color=V.muted,fontSize=9.sp)
                    }
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false},modifier=Modifier.background(V.paper)) {
                    DropdownMenuItem(text={Text("Reply")},onClick={menu=false;onReply()},leadingIcon={Icon(Icons.Outlined.Reply,null)})
                    if(!msg.deleted){
                        if(msg.kind==MessageKind.TEXT)DropdownMenuItem(text={Text("Copy")},onClick={clipboard.setText(AnnotatedString(msg.body));menu=false},leadingIcon={Icon(Icons.Outlined.ContentCopy,null)})
                        DropdownMenuItem(text={Text(if(msg.starred)"Remove star" else "Star for later")},onClick={onStar(!msg.starred);menu=false},leadingIcon={Icon(Icons.Outlined.StarBorder,null)})
                        DropdownMenuItem(text={Text(if(msg.pinned)"Unpin" else "Pin at top")},onClick={onPin(!msg.pinned);menu=false},leadingIcon={Icon(Icons.Outlined.PushPin,null)})
                        if(msg.mine && msg.kind==MessageKind.TEXT)DropdownMenuItem(text={Text("Edit")},onClick={menu=false;onEdit()},leadingIcon={Icon(Icons.Outlined.Edit,null)})
                    }
                    DropdownMenuItem(text={Text("Delete")},onClick={menu=false;onDelete()},leadingIcon={Icon(Icons.Outlined.Delete,null)})
                }
            }
        }
    }
}

@Composable
private fun ChatInfoScreen(partner:String,messages:List<ChatMessage>,onBack:()->Unit) {
    var detail by remember {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ if(detail != null) detail = null else onBack() }) {
                Icon(Icons.Outlined.ArrowBack,"Back")
            }
            Text(detail ?: "Conversation", color=V.text, fontSize=17.sp)
        }
        if(detail==null) {
            Spacer(Modifier.height(17.dp));Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Avatar(90,true)}
            Spacer(Modifier.height(14.dp));Text(partner,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=V.text,fontFamily=FontFamily.Serif,fontSize=28.sp)
            Text("Away for now",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=V.muted,fontSize=12.sp)
            Spacer(Modifier.height(26.dp))
            listOf("Shared media" to "Pictures and videos from this chat", "Links" to "Websites shared here", "Documents" to "Files from this conversation", "Audio" to "Voice notes and audio", "Pinned" to "${messages.count{it.pinned}} pinned messages", "Starred messages" to "${messages.count{it.starred}} saved for later", "Search" to "Find a message", "Wallpaper" to "Chat appearance", "Notifications" to "Conversation alerts").forEach { (name,description) ->
                Row(Modifier.fillMaxWidth().padding(bottom=9.dp).clip(round).background(V.paper).border(1.dp,V.border,round).clickable{detail=name}.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(name,color=V.text,fontSize=15.sp);Text(description,color=V.muted,fontSize=11.sp)};Icon(Icons.Outlined.ChevronRight,null,tint=V.rose)
                }
            }
            Text("Chat media is separate from Our Story until someone explicitly adds it.",color=V.muted,fontSize=12.sp,modifier=Modifier.padding(bottom=24.dp,top=9.dp))
        } else {
            val chosen = when(detail) {"Pinned" -> messages.filter{it.pinned && !it.deleted};"Starred messages" -> messages.filter{it.starred && !it.deleted};"Audio" -> messages.filter{it.kind==MessageKind.VOICE && !it.deleted};else -> emptyList()}
            Spacer(Modifier.height(15.dp))
            if(chosen.isEmpty())Text("Nothing here yet. ${if(detail=="Starred messages")"Long-press a message and choose Star for later." else "This collection will fill as you use Chat."}",color=V.muted,fontSize=13.sp)
            chosen.forEach { msg ->
                Tile(Modifier.fillMaxWidth().padding(bottom=9.dp)){
                    Text(if(msg.mine)"You · ${msg.time}" else "$partner · ${msg.time}",color=V.rose,fontSize=11.sp)
                    Spacer(Modifier.height(7.dp));Text(if(msg.kind==MessageKind.VOICE)"Voice note" else msg.body,color=V.text,fontSize=14.sp)
                }
            }
        }
    }
}

private val deckCategories = listOf("Heart to heart","Little laughs","Our future","Sweet & silly","Deep talks","Would you rather")
private data class Prompt(val category: String,val question: String) { val id:String get() = "${category.lowercase(Locale.ROOT).replace(" ","_")}_${question.hashCode().toUInt().toString(16)}" }
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
private fun QuestionDeckScreen(category:String,index:Int,onIndexChange:(Int)->Unit,onBack:()->Unit,onSend:(Prompt,Int,String)->Unit,onViewChat:()->Unit) {
    val filtered=remember(category){prompts.filter{it.category==category}}
    if(filtered.isEmpty()){
        Column {Text("No questions in this deck yet.",color=V.text);TextButton(onClick=onBack){Text("Back")}}
        return
    }
    var drag by remember(category){mutableFloatStateOf(0f)}
    var animating by remember(category){mutableStateOf(false)}
    var sentRecently by remember(category){mutableStateOf(false)}
    var expanded by remember(category){mutableStateOf(false)}
    var sendTick by remember(category){mutableIntStateOf(0)}
    val drafts=remember(category){mutableStateMapOf<String,String>()}
    val scope=rememberCoroutineScope()
    val current=index.coerceIn(0,filtered.lastIndex)
    val prompt=filtered[current]
    val colorOffset=deckCategories.indexOf(category).coerceAtLeast(0)*2
    val tone=(current+colorOffset)%cardColors.size
    val availableNext=current<filtered.lastIndex
    val availablePrevious=current>0
    fun moveCard(direction:Int) {
        if(animating)return
        if((direction<0 && !availableNext)||(direction>0 && !availablePrevious)){
            scope.launch {val a=Animatable(drag);a.animateTo(0f,tween(170)){drag=value}}
            return
        }
        animating=true
        scope.launch {
            val a=Animatable(drag)
            a.animateTo(direction*850f,tween(225)){drag=value}
            onIndexChange(current+if(direction<0)1 else -1)
            drag=0f
            animating=false
        }
    }
    LaunchedEffect(sendTick) {if(sendTick>0){kotlinx.coroutines.delay(2800);sentRecently=false}}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back",tint=V.text)}
            Column(Modifier.weight(1f)) {
                Text(category,color=V.text,fontSize=20.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold)
                Text("A little closer, one question at a time ♡",color=V.muted,fontSize=11.sp)
            }
            Icon(Icons.Outlined.Style,"Card deck",tint=V.rose)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical=6.dp),contentAlignment=Alignment.Center) {
            val behind=if(drag>0f)(current-1).coerceAtLeast(0) else (current+1).coerceAtMost(filtered.lastIndex)
            QuestionCard(filtered[behind].question,cardColors[(behind+colorOffset)%cardColors.size],
                Modifier.fillMaxWidth(.96f).fillMaxHeight(.96f).scale(.975f).offset(y=5.dp))
            QuestionCard(prompt.question,cardColors[tone],
                Modifier.fillMaxWidth(.96f).fillMaxHeight(.96f).offset{IntOffset(drag.roundToInt(),0)}
                    .graphicsLayer{rotationZ=drag/53f}.clickable { expanded=true }.pointerInput(current,animating) {
                        detectHorizontalDragGestures(onHorizontalDrag={change,amount->
                            if(!animating){change.consume();drag=(drag+amount).coerceIn(-680f,680f)}
                        },onDragEnd={
                            if(!animating) {
                                if(abs(drag)>92f)moveCard(if(drag<0f)-1 else 1)
                                else scope.launch{val a=Animatable(drag);a.animateTo(0f,tween(180)){drag=value}}
                            }
                        },onDragCancel={drag=0f})
                    })
        }
        AnimatedVisibility(sentRecently) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(V.raised)
                .padding(horizontal=12.dp,vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("♡ Sent to Chat",color=V.rose,fontSize=12.sp)
                TextButton(onClick=onViewChat){Text("View in Chat →",color=V.gold,fontSize=11.sp)}
            }
        }
        OutlinedTextField(value=drafts[prompt.id].orEmpty(),
            onValueChange={if(it.length<=500)drafts[prompt.id]=it},
            modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp),
            placeholder={Text("Add your answer or a little note…",fontSize=13.sp)},
            maxLines=3,shape=RoundedCornerShape(17.dp),
            leadingIcon={Icon(Icons.Outlined.Edit,null,tint=V.rose,modifier=Modifier.size(18.dp))})
        Spacer(Modifier.height(9.dp))
        Button(onClick={onSend(prompt,tone,drafts[prompt.id].orEmpty());sentRecently=true;sendTick++},
            modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp).height(50.dp),
            shape=RoundedCornerShape(17.dp)) {
            Icon(Icons.Outlined.Send,null,Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Send to Chat",fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
    }
    if(expanded) Dialog(onDismissRequest={expanded=false},
        properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(V.bg).padding(horizontal=16.dp,vertical=20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={expanded=false}){Icon(Icons.Outlined.Close,"Close question",tint=V.text)}
                Spacer(Modifier.width(7.dp))
                Text(category,color=V.text,fontFamily=FontFamily.Serif,fontSize=24.sp)
            }
            Spacer(Modifier.height(13.dp))
            QuestionCard(prompt.question,cardColors[tone],Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(value=drafts[prompt.id].orEmpty(),
                onValueChange={if(it.length<=500)drafts[prompt.id]=it},
                modifier=Modifier.fillMaxWidth(),
                placeholder={Text("Your answer or caption…")},maxLines=3)
            Spacer(Modifier.height(9.dp))
            Button(onClick={
                onSend(prompt,tone,drafts[prompt.id].orEmpty())
                sentRecently=true;sendTick++;expanded=false
            },modifier=Modifier.fillMaxWidth().height(52.dp)){
                Icon(Icons.Outlined.Send,null,Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("Send to Chat")
            }
            Spacer(Modifier.height(15.dp))
        }
    }
}

@Composable
private fun QuestionCard(question:String,color:Color,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(color).border(2.dp,Color(0xFF614158).copy(alpha=.32f),RoundedCornerShape(28.dp)).padding(25.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("VELVET",letterSpacing=2.sp,color=Color(0xFF493044),fontSize=11.sp,fontWeight=FontWeight.Bold);Icon(Icons.Outlined.FavoriteBorder,null,tint=Color(0xFF493044))}
        Text(question,color=Color(0xFF3E283D),fontSize=if(question.length>125)23.sp else if(question.length>80)27.sp else 31.sp,
            fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold,
            lineHeight=if(question.length>125)30.sp else 39.sp,textAlign=TextAlign.Center)
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
        Spacer(Modifier.height(34.dp));Serif(when{winner!=null->"$winner wins!";done->"A perfect tie.";else->"$move's turn"},32)
        Spacer(Modifier.height(17.dp))
        Column(Modifier.fillMaxWidth().widthIn(max=360.dp)) {
            repeat(3){r->Row(Modifier.fillMaxWidth()) {repeat(3){c->val k=r*3+c
                Box(Modifier.weight(1f).aspectRatio(1f).padding(5.dp).clip(RoundedCornerShape(17.dp)).background(V.paper).border(1.dp,V.border,RoundedCornerShape(17.dp)).combinedClickable(onClick={if(!done&&cells[k]==""){cells[k]=move;move=if(move=="♡")"✕" else "♡"}}),contentAlignment=Alignment.Center){Text(cells[k],color=V.rose,fontSize=43.sp,fontFamily=FontFamily.Serif)}
            }}}
        }
        Spacer(Modifier.height(18.dp));Button(onClick={for(i in cells.indices)cells[i]="";move="♡"}){Text("New game")};Spacer(Modifier.weight(1f))
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
private fun UsScreen(owner:String,partner:String,anniversary:String,onDateChange:(String)->Unit,onOwnProfile:()->Unit,onStudio:()->Unit) {
    val context=LocalContext.current
    val counts=relationshipCounts(anniversary)
    val datePicker={
        val base=runCatching {LocalDate.parse(anniversary)}.getOrElse{LocalDate.now()}
        DatePickerDialog(context,{_,y,m,d->onDateChange(LocalDate.of(y,m+1,d).toString())},
            base.year,base.monthValue-1,base.dayOfMonth).show()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Spacer(Modifier.height(12.dp));SectionLabel("OUR STORY IN TWO PEOPLE")
        Spacer(Modifier.height(8.dp));Serif("Us, always.",40)
        Text("Everything that makes us, us.",color=V.muted,fontSize=13.sp)
        Spacer(Modifier.height(20.dp))
        Tile(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.clickable(onClick=onOwnProfile),horizontalAlignment=Alignment.CenterHorizontally){Avatar(72,false);Spacer(Modifier.height(9.dp));Text(owner,color=V.text);Text("Your profile",color=V.rose,fontSize=10.sp)}
                HeartDrawing(Modifier.size(33.dp))
                Column(horizontalAlignment=Alignment.CenterHorizontally){Avatar(72,true);Spacer(Modifier.height(9.dp));Text(partner,color=V.text);Text("Managed by your partner",color=V.muted,fontSize=10.sp)}
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(round).background(V.raised).clickable(onClick=onStudio)
            .padding(horizontal=18.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette,"Our Studio",Modifier.size(30.dp),tint=V.rose)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Our Studio",color=V.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text("Themes, a shared note, and little drawings",color=V.muted,fontSize=11.sp)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=V.rose)
        }
        Spacer(Modifier.height(20.dp));SectionLabel("THE DAYS WE KEEP")
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Tile(Modifier.weight(1f)) { Serif(counts?.first?.toString() ?: "—",35,color=V.rose);Text("Days together",color=V.muted,fontSize=12.sp) }
            Tile(Modifier.weight(1f)) { Serif(counts?.second?.toString() ?: "—",35,color=V.rose);Text(if(counts?.second==0L)"Happy anniversary!" else "Days to anniversary",color=V.muted,fontSize=12.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Event,"Anniversary",tint=V.rose);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){
                Text("Our anniversary",color=V.text,fontSize=15.sp)
                Text(if(counts==null)"Choose the day your story began" else runCatching{LocalDate.parse(anniversary).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}.getOrDefault(anniversary),color=V.muted,fontSize=12.sp)
            };TextButton(onClick=datePicker){Text(if(counts==null)"Set date" else "Change")}}
            Text("This is saved on this phone for now. Once paired, Velvet will synchronize the date with your partner.",color=V.muted,fontSize=11.sp)
        }
        Spacer(Modifier.height(20.dp));SectionLabel("ACROSS ANY DISTANCE")
        Spacer(Modifier.height(10.dp))
        Tile(Modifier.fillMaxWidth()) {
            Serif("Two places. One us.",26)
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4E344B),Color(0xFF302536),Color(0xFF5C3D53)))),contentAlignment=Alignment.Center){
                Canvas(Modifier.fillMaxSize()){
                    val line=Path().apply{moveTo(size.width*.20f,size.height*.70f);cubicTo(size.width*.39f,size.height*.05f,size.width*.57f,size.height*.94f,size.width*.83f,size.height*.31f)}
                    drawPath(line,V.rose.copy(alpha=.66f),style=Stroke(width=3f));drawCircle(V.rose,9f,Offset(size.width*.20f,size.height*.70f));drawCircle(V.rose,9f,Offset(size.width*.83f,size.height*.31f))
                }
                Text("Location sharing is off",modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp).background(V.paper.copy(alpha=.83f),RoundedCornerShape(9.dp)).padding(7.dp),color=V.text,fontSize=11.sp)
            }
            Spacer(Modifier.height(9.dp));Text("Neither person can set the other's location. City-level sharing will require each person's own opt-in.",color=V.muted,fontSize=12.sp,lineHeight=19.sp)
        }
        Spacer(Modifier.height(18.dp))
        Tile(Modifier.fillMaxWidth()) {
            SectionLabel("OUR TIME")
            Spacer(Modifier.height(9.dp));Serif("Wherever you are.",23)
            Spacer(Modifier.height(8.dp))
            Text("Your time zone: ${java.util.TimeZone.getDefault().id}",color=V.rose,fontSize=12.sp)
            Text("Your partner's time zone will appear after pairing.",color=V.muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(18.dp));SectionLabel("LITTLE MILESTONES")
        Spacer(Modifier.height(9.dp))
        Tile(Modifier.fillMaxWidth()) {
            Serif("Every chapter counts.",23)
            Spacer(Modifier.height(8.dp));Text("Anniversaries and special dates will become shared milestones when your accounts are connected.",color=V.muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
