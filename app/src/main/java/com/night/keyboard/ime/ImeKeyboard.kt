package com.night.keyboard.ime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.model.*
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class ToolPanel { NONE, CLIPBOARD, EMOJI, VOICE, EDITOR, TONE, RESEARCH }

@Composable
fun ImeKeyboard(controller: KeyboardController, themeFlow: Flow<ThemeSnapshot>, preferenceFlow: Flow<KeyboardPreferenceState>, clipboardFlow: Flow<List<ClipboardItem>>) {
    val theme by themeFlow.collectAsState(initial = ThemeSnapshot())
    val prefs by preferenceFlow.collectAsState(initial = KeyboardPreferenceState())
    val clips by clipboardFlow.collectAsState(initial = emptyList())
    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var panel by remember { mutableStateOf(ToolPanel.NONE) }
    var textVersion by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf(listOf("I’m", "the", "thank you")) }
    fun textChanged() { textVersion++ }
    LaunchedEffect(textVersion) { suggestions = SuggestionEngine.suggest(controller.textBeforeCursor()) }

    Surface(color = Color(theme.backgroundArgb.toInt()), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 5.dp)) {
            Toolbar(panel, { panel = if (panel == it) ToolPanel.NONE else it }, controller)
            if (panel != ToolPanel.NONE) ToolPanelContent(panel, clips, prefs.serverUrl, controller, ::textChanged)
            if (prefs.suggestions && panel == ToolPanel.NONE) SuggestionStrip(suggestions) { controller.replaceCurrentWord(it); controller.commit(" "); textChanged() }
            if (prefs.numberRow && layer == KeyboardLayer.LETTERS) NumberRow(controller, ::textChanged, theme)
            KeyboardRows(KeyboardLayoutFactory.rows(layer), layer, shift, theme, prefs.secondaryCharacters, controller, { layer = it; panel = ToolPanel.NONE }, { shift = it }, { panel = if (panel == ToolPanel.EMOJI) ToolPanel.NONE else ToolPanel.EMOJI }, ::textChanged)
        }
    }
}

@Composable private fun Toolbar(panel: ToolPanel, onPanel: (ToolPanel) -> Unit, controller: KeyboardController) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(Icons.Outlined.ContentPaste, "Clipboard", panel == ToolPanel.CLIPBOARD) { onPanel(ToolPanel.CLIPBOARD) }
        ToolButton(Icons.Outlined.EmojiEmotions, "Emoji", panel == ToolPanel.EMOJI) { onPanel(ToolPanel.EMOJI) }
        ToolButton(Icons.Outlined.KeyboardVoice, "Voice input", panel == ToolPanel.VOICE) { onPanel(ToolPanel.VOICE) }
        ToolButton(Icons.Outlined.AutoAwesome, "Editor", panel == ToolPanel.EDITOR) { onPanel(ToolPanel.EDITOR) }
        ToolButton(Icons.Outlined.Tune, "Tone", panel == ToolPanel.TONE) { onPanel(ToolPanel.TONE) }
        ToolButton(Icons.Outlined.ManageSearch, "Research", panel == ToolPanel.RESEARCH) { onPanel(ToolPanel.RESEARCH) }
        Spacer(Modifier.width(2.dp)); ToolButton(Icons.Outlined.RecordVoiceOver, "Input picker", false) { controller.showInputPicker() }
    }
}

@Composable private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(39.dp).background(if (selected) Color(0xFF20262D) else Color.Transparent, CircleShape)) {
        Icon(icon, contentDescription = description, tint = if (selected) Color.White else Color(0xFFD6DCE2), modifier = Modifier.size(19.dp))
    }
}

@Composable private fun ToolPanelContent(panel: ToolPanel, clips: List<ClipboardItem>, serverUrl: String, controller: KeyboardController, onCommitted: () -> Unit) {
    Surface(color = Color(0xFF0B0E11), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)) {
        when (panel) {
            ToolPanel.CLIPBOARD -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                clips.take(8).forEach { clip -> Surface(onClick = { controller.commit(clip.text); onCommitted() }, color = Color(0xFF171C22), shape = RoundedCornerShape(10.dp)) { Text(clip.text, maxLines = 2, fontSize = 11.sp, modifier = Modifier.width(150.dp).padding(9.dp)) } }
                if (clips.isEmpty()) Text("Clipboard is empty", color = Color(0xFF8F99A4), fontSize = 11.sp, modifier = Modifier.padding(9.dp))
            }
            ToolPanel.EMOJI -> EmojiPanel(controller, onCommitted)
            ToolPanel.VOICE -> StatusPanel("Voice input", if (serverUrl.isBlank()) "Set your Whisper server URL in the app before voice input is enabled." else "Whisper endpoint configured. Recording/stream upload is the next integration gate.")
            ToolPanel.EDITOR -> OnlineToolPanel("Editor", "Fix spelling, grammar and punctuation without changing meaning.", serverUrl, controller.selectedText())
            ToolPanel.TONE -> OnlineToolPanel("Tone", "Rewrite selected text with a chosen tone.", serverUrl, controller.selectedText())
            ToolPanel.RESEARCH -> OnlineToolPanel("Contextual Research", "Research the selected text or current query without adding a generic chatbot.", serverUrl, controller.selectedText())
            ToolPanel.NONE -> Unit
        }
    }
}

@Composable private fun StatusPanel(title: String, message: String) { Column(Modifier.padding(11.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(message, color = Color(0xFF8F99A4), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp)) } }
@Composable private fun OnlineToolPanel(title: String, message: String, serverUrl: String, selectedText: String) {
    Column(Modifier.padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(if (serverUrl.isBlank()) "Not configured" else "Server ready", color = if (serverUrl.isBlank()) Color(0xFFE4B661) else Color(0xFF78D69C), fontSize = 9.sp) }
        Text(message, color = Color(0xFF8F99A4), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        if (selectedText.isNotBlank()) Text("Selected: ${selectedText.take(80)}", color = Color(0xFFC9D1D9), fontSize = 10.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
    }
}

private data class VectorEmoji(val kind: String, val unicode: String)
@Composable private fun EmojiPanel(controller: KeyboardController, onCommitted: () -> Unit) {
    val entries = remember { listOf(VectorEmoji("smile","🙂"),VectorEmoji("grin","😄"),VectorEmoji("love","😍"),VectorEmoji("cool","😎"),VectorEmoji("sad","😔"),VectorEmoji("cry","😢"),VectorEmoji("angry","😠"),VectorEmoji("heart","❤️"),VectorEmoji("sparkle","✨"),VectorEmoji("sun","☀️"),VectorEmoji("flower","🌸"),VectorEmoji("rocket","🚀")) }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        entries.forEach { e -> Surface(onClick = { controller.commit(e.unicode); onCommitted() }, color = Color.Transparent, shape = RoundedCornerShape(9.dp)) { CustomEmojiArt(e.kind, Modifier.size(42.dp).padding(4.dp)) } }
    }
}

@Composable private fun CustomEmojiArt(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height; val c=Offset(w/2,h/2)
        when(kind) {
            "heart" -> { val p=Path().apply { moveTo(w*.5f,h*.86f); cubicTo(w*.08f,h*.62f,w*.1f,h*.22f,w*.32f,h*.22f); cubicTo(w*.43f,h*.22f,w*.49f,h*.31f,w*.5f,h*.36f); cubicTo(w*.51f,h*.31f,w*.57f,h*.22f,w*.68f,h*.22f); cubicTo(w*.9f,h*.22f,w*.92f,h*.62f,w*.5f,h*.86f) }; drawPath(p,Color(0xFFFF6178)) }
            "sparkle" -> { val p=Path().apply { moveTo(w*.5f,h*.05f); lineTo(w*.6f,h*.4f); lineTo(w*.95f,h*.5f); lineTo(w*.6f,h*.6f); lineTo(w*.5f,h*.95f); lineTo(w*.4f,h*.6f); lineTo(w*.05f,h*.5f); lineTo(w*.4f,h*.4f); close() }; drawPath(p,Color(0xFFFFD45C)) }
            "sun" -> { drawCircle(Color(0xFFFFD45C),w*.24f,c); repeat(8){i->val a=i*Math.PI/4; drawLine(Color(0xFFFFD45C),Offset(c.x+cos(a).toFloat()*w*.32f,c.y+sin(a).toFloat()*w*.32f),Offset(c.x+cos(a).toFloat()*w*.44f,c.y+sin(a).toFloat()*w*.44f),w*.07f,cap=StrokeCap.Round)} }
            "flower" -> { repeat(5){i->val a=i*2*Math.PI/5-Math.PI/2; drawCircle(Color(0xFFFF8CC7),w*.19f,Offset(c.x+cos(a).toFloat()*w*.22f,c.y+sin(a).toFloat()*w*.22f))}; drawCircle(Color(0xFFFFD45C),w*.14f,c) }
            "rocket" -> { val p=Path().apply { moveTo(w*.5f,h*.06f); cubicTo(w*.75f,h*.22f,w*.75f,h*.56f,w*.5f,h*.78f); cubicTo(w*.25f,h*.56f,w*.25f,h*.22f,w*.5f,h*.06f); close() }; drawPath(p,Color(0xFFD9E1EB)); drawCircle(Color(0xFF6EA8FF),w*.1f,Offset(w*.5f,h*.39f)); drawLine(Color(0xFFFFD45C),Offset(w*.5f,h*.76f),Offset(w*.5f,h*.94f),w*.1f,cap=StrokeCap.Round) }
            else -> {
                drawCircle(Color(0xFFFFD45C),w*.44f,c)
                when(kind){
                    "cool"->{drawRect(Color(0xFF242B34),Offset(w*.2f,h*.32f),Size(w*.27f,h*.18f));drawRect(Color(0xFF242B34),Offset(w*.53f,h*.32f),Size(w*.27f,h*.18f));drawLine(Color(0xFF242B34),Offset(w*.47f,h*.37f),Offset(w*.53f,h*.37f),w*.04f)}
                    "love"->{drawCircle(Color(0xFFFF5D75),w*.07f,Offset(w*.35f,h*.38f));drawCircle(Color(0xFFFF5D75),w*.07f,Offset(w*.65f,h*.38f))}
                    else->{drawCircle(Color(0xFF3E342A),w*.045f,Offset(w*.36f,h*.4f));drawCircle(Color(0xFF3E342A),w*.045f,Offset(w*.64f,h*.4f))}
                }
                val mouth=Color(0xFF3E342A); if(kind in listOf("sad","cry","angry")) drawArc(mouth,200f,140f,false,Offset(w*.33f,h*.61f),Size(w*.34f,h*.22f),style=Stroke(w*.045f,cap=StrokeCap.Round)) else drawArc(mouth,20f,140f,false,Offset(w*.33f,h*.48f),Size(w*.34f,h*.25f),style=Stroke(w*.045f,cap=StrokeCap.Round)); if(kind=="cry")drawCircle(Color(0xFF62B8F2),w*.06f,Offset(w*.69f,h*.55f))
            }
        }
    }
}

@Composable private fun SuggestionStrip(suggestions: List<String>, onSuggestion: (String)->Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) { suggestions.take(3).forEach { s -> Box(Modifier.weight(1f).height(41.dp).combinedClickable(onClick={onSuggestion(s)}), contentAlignment=Alignment.Center){Text(s,color=Color(0xFFF2F3F5),fontSize=14.sp,maxLines=1)} } }
}

@Composable private fun NumberRow(controller: KeyboardController, onTextChanged: ()->Unit, theme: ThemeSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(1.dp)){ "1234567890".forEach{c->ImeKey(KeySpec("number_$c",c.toString(),output=c.toString()),c.toString(),theme,false,Modifier.weight(1f),{controller.commit(c.toString());onTextChanged()})} }
}

@Composable private fun KeyboardRows(rows: List<List<KeySpec>>, layer: KeyboardLayer, shift: ShiftState, theme: ThemeSnapshot, secondaryVisible: Boolean, controller: KeyboardController, onLayer:(KeyboardLayer)->Unit, onShift:(ShiftState)->Unit, onOpenEmoji:()->Unit, onTextChanged:()->Unit) {
    rows.forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(1.dp),verticalAlignment=Alignment.CenterVertically) {
        row.forEach { key ->
            val display=if(layer==KeyboardLayer.LETTERS && key.output?.singleOrNull()?.isLetter()==true && shift!=ShiftState.OFF) key.label.uppercase() else key.label
            if(key.special==SpecialKey.SPACE) SpacebarKey(key,theme,Modifier.weight(key.weight),{controller.commit(" ");onTextChanged()},{controller.moveCursor(it)})
            else ImeKey(key,display,theme,secondaryVisible,Modifier.weight(key.weight),{
                when(key.special){
                    SpecialKey.SHIFT->onShift(if(shift==ShiftState.OFF)ShiftState.ONCE else ShiftState.OFF)
                    SpecialKey.BACKSPACE->{controller.backspace();onTextChanged()}
                    SpecialKey.ENTER->{controller.enter();onTextChanged()}
                    SpecialKey.EMOJI->onOpenEmoji()
                    SpecialKey.NUMBERS->onLayer(KeyboardLayer.SYMBOLS)
                    SpecialKey.LETTERS->onLayer(KeyboardLayer.LETTERS)
                    SpecialKey.MORE_SYMBOLS->onLayer(KeyboardLayer.SYMBOLS_MORE)
                    SpecialKey.LESS_SYMBOLS->onLayer(KeyboardLayer.SYMBOLS)
                    else->{ val raw=key.output; if(raw!=null){ val out=if(layer==KeyboardLayer.LETTERS&&raw.length==1&&raw[0].isLetter()&&shift!=ShiftState.OFF)raw.uppercase() else raw; controller.commit(out); if(shift==ShiftState.ONCE)onShift(ShiftState.OFF);onTextChanged()} }
                }
            }, if(key.special==SpecialKey.SHIFT){{onShift(if(shift==ShiftState.LOCKED)ShiftState.OFF else ShiftState.LOCKED)}}else null, if(key.secondary!=null&&key.special==null){{controller.commit(key.secondary);onTextChanged()}}else null)
        }
    } }
}

@Composable private fun ImeKey(key: KeySpec, displayLabel:String, theme:ThemeSnapshot, secondaryVisible:Boolean, modifier:Modifier=Modifier, onClick:()->Unit, onDoubleClick:(()->Unit)?=null, onLongClick:(()->Unit)?=null) {
    val haptic=LocalHapticFeedback.current; val o=theme.overrides[key.id]?:KeyStyleOverride(); val radius=(o.cornerRadiusDp?:theme.cornerRadiusDp).dp; val borderEnabled=o.borderEnabled?:theme.borderEnabled
    val fill=when{ o.invisibleFill==true->Color.Transparent; o.fillArgb!=null->Color(o.fillArgb.toInt()).copy(alpha=o.fillAlpha?:1f); else->Color(theme.keyFillArgb.toInt()) }
    val labelColor=Color((o.labelArgb?:theme.keyLabelArgb).toInt()); val border=Color((o.borderArgb?:theme.borderArgb).toInt()); val borderWidth=if(borderEnabled)(o.borderWidthDp?:theme.borderWidthDp).dp else 0.dp
    Box(modifier.height(50.dp).padding(horizontal=1.dp).background(fill,RoundedCornerShape(radius)).then(if(borderWidth>0.dp)Modifier.border(borderWidth,border,RoundedCornerShape(radius))else Modifier).combinedClickable(onClick=onClick,onDoubleClick=onDoubleClick,onLongClick=onLongClick?.let{long->{haptic.performHapticFeedback(HapticFeedbackType.LongPress);long()}}),contentAlignment=Alignment.Center){
        if(secondaryVisible&&key.secondary!=null&&key.secondary!="mic")Text(key.secondary,Modifier.align(Alignment.TopEnd).padding(top=2.dp,end=6.dp),Color(theme.secondaryLabelArgb.toInt()),fontSize=8.sp)
        Text(displayLabel,color=labelColor,fontSize=(o.labelSizeSp?:theme.labelSizeSp).sp,fontWeight=if(o.bold==true)FontWeight.Bold else FontWeight.Normal,fontStyle=if(o.italic==true)FontStyle.Italic else FontStyle.Normal)
        if(key.special==SpecialKey.COMMA&&key.secondary=="mic")Icon(Icons.Outlined.KeyboardVoice,null,tint=Color(theme.secondaryLabelArgb.toInt()),modifier=Modifier.align(Alignment.TopEnd).padding(top=3.dp,end=5.dp).size(9.dp))
    }
}

@Composable private fun SpacebarKey(key:KeySpec,theme:ThemeSnapshot,modifier:Modifier,onSpace:()->Unit,onCursor:(Int)->Boolean){
    var accumulated by remember{mutableFloatStateOf(0f)};var tracking by remember{mutableStateOf(false)};val haptic=LocalHapticFeedback.current;val o=theme.overrides[key.id]?:KeyStyleOverride();val radius=(o.cornerRadiusDp?:theme.cornerRadiusDp).dp;val fill=if(o.invisibleFill==true)Color.Transparent else Color((o.fillArgb?:theme.keyFillArgb).toInt()).copy(alpha=o.fillAlpha?:1f)
    Box(modifier.height(50.dp).padding(horizontal=1.dp).background(fill,RoundedCornerShape(radius)).pointerInput(Unit){detectDragGesturesAfterLongPress(onDragStart={tracking=true;haptic.performHapticFeedback(HapticFeedbackType.LongPress)},onDragEnd={tracking=false;accumulated=0f},onDragCancel={tracking=false;accumulated=0f}){change,drag->change.consume();accumulated+=drag.x;val stepPx=18.dp.toPx();while(abs(accumulated)>=stepPx){val direction=if(accumulated>0)1 else -1;if(onCursor(direction))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);accumulated-=direction*stepPx}}}.combinedClickable(onClick={if(!tracking)onSpace()}),contentAlignment=Alignment.Center){Text(if(tracking)"cursor" else "",color=Color(theme.secondaryLabelArgb.toInt()),fontSize=9.sp,modifier=Modifier.alpha(if(tracking)1f else 0f))}
}
