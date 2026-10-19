package com.night.soundatlas

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.Intent
import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var surface: AtlasCanvas
    private val audio by lazy { AudioEngine(assets) }
    private val store by lazy { getSharedPreferences("atlas-worlds", MODE_PRIVATE) }
    private var world=AtlasPresets.create(0)
    private var pendingExport: String? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var shouldResumeAfterTransientLoss=false
    private var inForeground=false
    private var noisyRegistered=false
    private val focusListener=AudioManager.OnAudioFocusChangeListener { focus ->
        when(focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldResumeAfterTransientLoss && inForeground) {
                    shouldResumeAfterTransientLoss=false
                    audio.start(world)
                    surface.invalidate()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                shouldResumeAfterTransientLoss=audio.isPlaying
                audio.stop()
                surface.invalidate()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeAfterTransientLoss=false
                audio.stop()
                surface.invalidate()
            }
        }
    }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(false)
            .build()
    }
    private val noisyReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action==AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopPlayback()
        }
    }
    private fun startPlayback() {
        shouldResumeAfterTransientLoss=false
        val outcome=audioManager.requestAudioFocus(focusRequest)
        if(outcome!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            Toast.makeText(this,"Audio output is being used by another app",Toast.LENGTH_SHORT).show()
            return
        }
        audio.start(world)
        surface.invalidate()
    }
    private fun stopPlayback() {
        shouldResumeAfterTransientLoss=false
        audio.stop()
        audioManager.abandonAudioFocusRequest(focusRequest)
        if(::surface.isInitialized)surface.invalidate()
    }
    override fun onResume() { super.onResume();inForeground=true }
    override fun onStart() {
        super.onStart()
        if(!noisyRegistered){
            androidx.core.content.ContextCompat.registerReceiver(this,noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyRegistered=true
        }
    }
    override fun onStop() {
        if(noisyRegistered){unregisterReceiver(noisyReceiver);noisyRegistered=false}
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=(
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        // Never force the display awake for passive listening or editing.
        try { store.getString("last",null)?.let { world=AtlasCodec.decode(it) } }
        catch (_:Exception) { world=AtlasPresets.create(0) }
        surface=AtlasCanvas()
        setContentView(surface)
        audio.update(world)
        if(intent?.action==Intent.ACTION_VIEW) importIncomingScene(intent.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if(intent.action==Intent.ACTION_VIEW) importIncomingScene(intent.data)
    }

    private fun readCompactScene(uri:Uri): World {
        val input=contentResolver.openInputStream(uri) ?: error("Cannot open scene")
        val bytes=input.use{ stream->
            val out=ByteArrayOutputStream()
            val chunk=ByteArray(8192)
            while(true){
                val n=stream.read(chunk)
                if(n<0)break
                require(out.size()+n<=250_000){"Scene exceeds 250 KB"}
                out.write(chunk,0,n)
            }
            out.toByteArray()
        }
        return AtlasCodec.decode(bytes.toString(Charsets.UTF_8))
    }
    private fun importIncomingScene(uri:Uri?) {
        if(uri==null)return
        try {
            replace(readCompactScene(uri))
            save()
            Toast.makeText(this,"Sound Atlas world imported",Toast.LENGTH_SHORT).show()
        } catch(e:Exception) {
            Toast.makeText(this,"Could not open world: "+e.message,Toast.LENGTH_LONG).show()
        }
    }

    private fun changed() {
        audio.update(world)
        surface.invalidate()
    }
    private fun replace(newWorld:World) {
        world=newWorld
        surface.resetCamera()
        changed()
    }
    private fun save() {
        store.edit().putString("last",AtlasCodec.encode(world)).apply()
        Toast.makeText(this,"World saved on device",Toast.LENGTH_SHORT).show()
    }
    private fun importScene() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/json","text/plain","application/octet-stream"))
        }
        startActivityForResult(intent,71)
    }
    private fun exportScene() {
        pendingExport=AtlasCodec.encode(world)
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
            putExtra(Intent.EXTRA_TITLE,world.name.replace(" ","-")+".satlas.json")
        }
        startActivityForResult(intent,72)
    }
    private fun share() {
        // Share the tiny *scene file*, never enormous rendered audio.
        val directory=File(cacheDir,"share")
        if(!directory.exists())directory.mkdirs()
        val name=world.name.replace(Regex("[^a-zA-Z0-9_-]"),"-").take(48)
        val file=File(directory,(name.ifBlank{"sound-atlas"})+".satlas.json")
        file.writeText(AtlasCodec.encode(world),Charsets.UTF_8)
        val uri=FileProvider.getUriForFile(this,
            packageName+".files",file)
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="application/json"
            putExtra(Intent.EXTRA_SUBJECT,"Sound Atlas: "+world.name)
            putExtra(Intent.EXTRA_STREAM,uri)
            clipData=ClipData.newUri(contentResolver,"Sound Atlas world",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent,"Share acoustic world"))
    }
    @Deprecated("Activity result API kept for zero-dependency Android 8 support")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            when(requestCode) {
                71 -> {
                    replace(readCompactScene(data.data!!))
                    save()
                    Toast.makeText(this,"World imported",Toast.LENGTH_SHORT).show()
                }
                72 -> {
                    val output=contentResolver.openOutputStream(data.data!!) ?: error("Unable to export")
                    output.use{it.write((pendingExport?:AtlasCodec.encode(world)).toByteArray(Charsets.UTF_8))}
                    Toast.makeText(this,".satlas.json exported",Toast.LENGTH_SHORT).show()
                    pendingExport=null
                }
            }
        } catch(e:Exception) {
            Toast.makeText(this,"Scene error: "+(e.message?:"Invalid document"),Toast.LENGTH_LONG).show()
        }
    }
    override fun onPause() {
        store.edit().putString("last",AtlasCodec.encode(world)).apply()
        inForeground=false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopPlayback()
        super.onPause()
    }
    override fun onDestroy() { audio.close();super.onDestroy() }
    @Deprecated("Activity back handling on minSdk 26")
    override fun onBackPressed() {
        if(surface.closeSheetOrExitExplore())return
        super.onBackPressed()
    }

    private enum class Sheet { NONE, LIBRARY, SOUNDS, MATERIALS, ZONES, ATMOSPHERE, INSPECT }
    private enum class Mode { EDIT, DRAW_WALL, ADD_ZONE, EXPLORE }
    private data class Hit(val rect: RectF, val label:String, val action: () -> Unit)

    private inner class AtlasCanvas : View(this@MainActivity) {
        private val density=resources.displayMetrics.density
        // Custom Canvas elements must be real TalkBack virtual buttons, not an
        // unlabeled bitmap. This also makes QA inspect actual active controls.
        private val accessibilityDelegate=object:ExploreByTouchHelper(this) {
            override fun getVirtualViewAt(x:Float,y:Float):Int =
                buttons.indices.reversed().firstOrNull {
                    buttons[it].rect.contains(x/density,y/density)
                } ?: INVALID_ID

            override fun getVisibleVirtualViews(virtualViewIds:MutableList<Int>) {
                virtualViewIds.addAll(buttons.indices.toList())
            }
            override fun onPopulateNodeForVirtualView(
                virtualViewId:Int,node:AccessibilityNodeInfoCompat
            ) {
                val hit=buttons.getOrNull(virtualViewId)
                if(hit==null){
                    node.contentDescription="Unavailable control"
                    node.setBoundsInParent(Rect(0,0,1,1))
                    return
                }
                val bounds=hit.rect
                node.contentDescription=hit.label
                node.className="android.widget.Button"
                node.isClickable=true
                node.isEnabled=true
                node.isFocusable=true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                node.setBoundsInParent(Rect(
                    (bounds.left*density).roundToInt(),(bounds.top*density).roundToInt(),
                    (bounds.right*density).roundToInt(),(bounds.bottom*density).roundToInt()
                ))
            }
            override fun onPerformActionForVirtualView(
                virtualViewId:Int,action:Int,arguments:Bundle?
            ):Boolean {
                if(action!=AccessibilityNodeInfoCompat.ACTION_CLICK)return false
                val hit=buttons.getOrNull(virtualViewId)?:return false
                hit.action()
                invalidate()
                invalidateRoot()
                return true
            }
        }
        init { ViewCompat.setAccessibilityDelegate(this,accessibilityDelegate) }
        override fun dispatchHoverEvent(event:MotionEvent):Boolean =
            accessibilityDelegate.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val background=0xFF08121B.toInt()
        private val card=0xFF101F2A.toInt()
        private val subtle=0xFF8395A5.toInt()
        private val text=0xFFF1EEE7.toInt()
        private val teal=0xFF9DD9CD.toInt()
        private var ww=0f;private var hh=0f
        private var camera=world.listener.copy()
        private var zoom=1f
        private var meter=1f
        private var sheet=Sheet.NONE
        private var soundPage=0
        private var accessibilityState=""
        private var mode=Mode.EDIT
        private var wallMaterial=Material.STONE
        private var selectedId:Int?=null
        private var selectedWall=-1
        private var selectedZone=-1
        private var dragTarget:Int?=null // -1 listener, -2 pan, -3 draw wall, >=1 emitter
        private var down=V2(0f,0f)
        private var last=V2(0f,0f)
        private var drawingWall:V2?=null
        private var drawingEnd:V2?=null
        private var moved=false
        private var pinching=false
        private var pinchSpan=0f
        private var selectedZoneType=0
        private val exploreRoute=mutableListOf<V2>()
        private val buttons=mutableListOf<Hit>()
        private var tick=0L

        fun resetCamera() { camera=world.listener.copy();zoom=1f;sheet=Sheet.NONE;mode=Mode.EDIT;selectedId=null;selectedWall=-1;selectedZone=-1;invalidate() }
        fun closeSheetOrExitExplore():Boolean {
            if(sheet!=Sheet.NONE) {sheet=Sheet.NONE;invalidate();return true}
            if(mode!=Mode.EDIT) {mode=Mode.EDIT;invalidate();return true}
            return false
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            ww=width/density;hh=height/density
            val state="Sound Atlas. World "+world.name+". Panel "+sheet.name+(if(sheet==Sheet.SOUNDS)" page "+(soundPage+1) else "")+
                ". Mode "+mode.name+". "+world.sounds.size+" sound sources."
            if(state!=accessibilityState){
                accessibilityState=state
                contentDescription=state
            }
            canvas.save();canvas.scale(density,density)
            canvas.drawColor(background)
            buttons.clear()
            meter=(min((ww-30)/world.width,(hh-210)/world.height).coerceAtLeast(.08f)*zoom)
            drawWorld(canvas)
            drawSoundFieldReadout(canvas)
            drawChrome(canvas)
            if(sheet!=Sheet.NONE)drawSheet(canvas)
            canvas.restore()
            if(mode==Mode.EXPLORE){
                tick++
                if(tick%2L==0L)walkToTarget()
                if(exploreRoute.isNotEmpty()) postInvalidateDelayed(33)
            }
        }
        private fun xy(v:V2)=V2(ww*.5f+(v.x-camera.x)*meter,hh*.47f+(v.y-camera.y)*meter)
        private fun unxy(v:V2)=V2((v.x-ww*.5f)/meter+camera.x,
            (v.y-hh*.47f)/meter+camera.y)
        private fun paint(color:Int,style:Paint.Style=Paint.Style.FILL,stroke:Float=1f):Paint {
            paint.color=color;paint.style=style;paint.strokeWidth=stroke;paint.strokeCap=Paint.Cap.ROUND
            paint.strokeJoin=Paint.Join.ROUND;paint.shader=null
            return paint
        }
        private fun box(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int,radius:Float=16f) {
            c.drawRoundRect(l,t,r,b,radius,radius,paint(color))
        }
        private fun line(c:Canvas,a:V2,b:V2,color:Int,weight:Float) {
            c.drawLine(a.x,a.y,b.x,b.y,paint(color,Paint.Style.STROKE,weight))
        }
        private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float=13f,color:Int=text,
                          bold:Boolean=false) {
            paint(color);paint.textSize=size;paint.typeface=if(bold)
                android.graphics.Typeface.create("sans-serif-medium",0)
            else android.graphics.Typeface.create("sans-serif",0)
            c.drawText(s,x,y,paint)
        }
        private fun drawWorld(c:Canvas) {
            val origin=xy(V2(0f,0f));val far=xy(V2(world.width,world.height))
            box(c,origin.x,origin.y,far.x,far.y,0xFF0D2029.toInt(),3f)
            // Measured world-space grid; pan/zoom never changes material locations.
            val grid=10
            for(x in 0..world.width.toInt() step grid) {
                val a=xy(V2(x.toFloat(),0f));val b=xy(V2(x.toFloat(),world.height))
                line(c,a,b,0xFF183139.toInt(),.7f)
            }
            for(y in 0..world.height.toInt() step grid) {
                val a=xy(V2(0f,y.toFloat()));val b=xy(V2(world.width,y.toFloat()))
                line(c,a,b,0xFF183139.toInt(),.7f)
            }
            for(zone in world.zones) {
                val a=xy(V2(zone.x,zone.y));val b=xy(V2(zone.x+zone.w,zone.y+zone.h))
                box(c,a.x,a.y,b.x,b.y,0x2233B4AA,4f)
                c.drawRoundRect(a.x,a.y,b.x,b.y,3f,3f,paint(0x5543AAAC,Paint.Style.STROKE,1f))
                label(c,zone.name.uppercase(),a.x+8,a.y+17,9f,0xFFA3BFC2.toInt(),true)
            }
            for((index,wall) in world.walls.withIndex()) {
                val a=xy(wall.a);val b=xy(wall.b)
                if(index==selectedWall) line(c,a,b,0xFFEDF0DC.toInt(),7f)
                line(c,a,b,wall.material.color,if(index==selectedWall)3.6f else 3f)
            }
            for(sound in world.sounds){
                val p=xy(sound.at);val color=if(sound.enabled)sound.kind.color else subtle
                val ring=13f+4f*sin(tick*.02+sound.id).toFloat()
                c.drawCircle(p.x,p.y,ring,paint(color and 0x33FFFFFF))
                c.drawCircle(p.x,p.y,9f,paint(0xFF122932.toInt()))
                c.drawCircle(p.x,p.y,6f,paint(color))
                if(sound.id==selectedId){
                    c.drawCircle(p.x,p.y,17f,paint(teal,Paint.Style.STROKE,1.4f))
                    label(c,sound.kind.title,p.x+14,p.y-12,11f,text,true)
                }
            }
            if(drawingWall!=null && drawingEnd!=null){
                line(c,xy(drawingWall!!),xy(drawingEnd!!),wallMaterial.color,3f)
            }
            val head=xy(world.listener)
            c.drawCircle(head.x,head.y,18f,paint(0x2258D1CA))
            c.drawCircle(head.x,head.y,10f,paint(teal))
            c.drawCircle(head.x,head.y,4f,paint(background))
            val facing=world.facing
            val target=V2(head.x+sin(facing)*23f,head.y-cos(facing)*23f)
            line(c,head,target,teal,2f)
            c.drawCircle(target.x,target.y,2f,paint(teal))
            if(mode==Mode.EXPLORE){
                exploreRoute.lastOrNull()?.let { targetPoint->
                    val p=xy(targetPoint)
                    c.drawCircle(p.x,p.y,8f,paint(teal,Paint.Style.STROKE,1f))
                    label(c,"DESTINATION",p.x+12f,p.y+4f,9f,teal,true)
                }
            }
        }

        private val vectorIcons by lazy {
            mapOf(
                "play" to R.drawable.atlas_ic_play,
                "pause" to R.drawable.atlas_ic_pause,
                "save" to R.drawable.atlas_ic_save,
                "load" to R.drawable.atlas_ic_load,
                "share" to R.drawable.atlas_ic_share,
                "library" to R.drawable.atlas_ic_library,
                "sounds" to R.drawable.atlas_ic_sounds,
                "wall" to R.drawable.atlas_ic_wall,
                "zones" to R.drawable.atlas_ic_zones,
                "walk" to R.drawable.atlas_ic_walk,
                "close" to R.drawable.atlas_ic_close,
                "plus" to R.drawable.atlas_ic_plus,
                "minus" to R.drawable.atlas_ic_minus,
                "weather" to R.drawable.atlas_ic_weather
            ).mapValues { resources.getDrawable(it.value,theme).mutate() }
        }
        private fun icon(c:Canvas,name:String,cx:Float,cy:Float,color:Int) {
            val drawable=vectorIcons[name] ?: return
            drawable.setTint(color)
            drawable.setBounds((cx-11f).toInt(),(cy-11f).toInt(),
                (cx+11f).toInt(),(cy+11f).toInt())
            drawable.draw(c)
        }
        private fun action(c:Canvas,name:String,x:Float,y:Float,w:Float,h:Float,
                           caption:String,active:Boolean=false,onClick:()->Unit) {
            box(c,x,y,x+w,y+h,if(active)0xFF214843.toInt() else card,12f)
            icon(c,name,x+w*.5f,y+19f,if(active)teal else text)
            if(caption.isNotEmpty()) {
                val size=10f
                paint(text);paint.textSize=size
                val width=paint.measureText(caption)
                label(c,caption,x+(w-width)*.5f,y+h-10f,size,
                    if(active)teal else 0xFFC5D2D4.toInt(),true)
            }
            val spoken=if(caption.isNotBlank())caption else when(name){
                "load"->"Import acoustic world"
                "save"->"Save acoustic world"
                "share"->"Share acoustic world"
                "play"->"Play spatial audio"
                "pause"->"Pause spatial audio"
                "close"->"Close panel"
                "plus"->"Increase value"
                "minus"->"Decrease value"
                else->name.replaceFirstChar{it.uppercase()}
            }
            buttons += Hit(RectF(x,y,x+w,y+h),spoken,onClick)
        }
        private fun drawSoundFieldReadout(c:Canvas) {
            if (hh<530f || sheet!=Sheet.NONE) return
            // Read-only acoustic status occupies the previously unused bottom area.
            // It is deliberately NOT a second mixer or duplicate playback control.
            val top=hh-313f
            box(c,16f,top,ww-16f,hh-153f,0xED10242E.toInt(),16f)
            label(c,"SOUND FIELD",29f,top+24f,12f,text,true)
            val room=world.zones.lastOrNull{it.contains(world.listener)}
            val place=(room?.name?:"Open air").take(20)
            val suffix="   " + world.walls.size + " walls"
            label(c,place+suffix,29f,top+42f,10f,teal)
            val nearest=world.sounds.filter{it.enabled}.sortedBy{it.at.distance(world.listener)}.take(3)
            if(nearest.isEmpty()) {
                label(c,"No active sound sources",29f,top+82f,12f,subtle)
                return
            }
            nearest.forEachIndexed { index,source ->
                val y=top+71f+index*27f
                val data=AcousticSolver.evaluate(world,source)
                c.drawCircle(34f,y-4f,3.5f,paint(source.kind.color))
                label(c,source.kind.title.take(17),45f,y,11f,text,true)
                val status=if(data.occlusions>0) "MUFFLED" else
                    if(data.pan<-.28f) "LEFT" else if(data.pan>.28f) "RIGHT" else "AHEAD"
                val distance=data.distance.toInt().toString()+"m  "+status
                paint(subtle);paint.textSize=10f
                val width=paint.measureText(distance)
                label(c,distance,ww-30f-width,y,10f,if(data.occlusions>0)
                    0xFFD3B993.toInt() else subtle)
            }
        }

        private fun drawChrome(c:Canvas) {
            box(c,10f,12f,ww-10f,96f,0xEA101E29.toInt(),18f)
            label(c,"SOUND ATLAS",24f,38f,16f,text,true)
            label(c,world.name.take(22),24f,56f,11f,subtle)
            label(c,(if(mode==Mode.EXPLORE)"EXPLORE" else "EDIT")+"  /  "+
                world.sounds.size+" SOURCES",24f,78f,10f,teal,true)
            val topWidth=35f
            val first=ww-16f-4*topWidth-3*5f
            action(c,"load",first,23f,topWidth,39f,"",onClick={ importScene() })
            action(c,"save",first+40f,23f,topWidth,39f,"",onClick={ save() })
            action(c,"share",first+80f,23f,topWidth,39f,"",onClick={ share() })
            action(c,if(audio.isPlaying)"pause" else "play",first+120f,23f,topWidth,39f,"",
                active=audio.isPlaying,onClick={
                    if(audio.isPlaying)stopPlayback() else startPlayback()
                    invalidate()
                })
            box(c,ww-159f,68f,ww-17f,90f,0xFF20403F.toInt(),9f)
            icon(c,"weather",ww-142f,79f,teal)
            label(c,"ATMOSPHERE",ww-125f,83f,10f,teal,true)
            buttons+=Hit(RectF(ww-159f,68f,ww-17f,90f),"Edit rain, wind and storm"){
                sheet=Sheet.ATMOSPHERE;invalidate()
            }
            if(sheet==Sheet.NONE) {
                box(c,10f,hh-102f,ww-10f,hh-12f,0xF0101E29.toInt(),19f)
                val names=listOf("library","sounds","wall","zones","walk")
                val captions=listOf("WORLD","SOUNDS","WALLS","ROOMS",if(mode==Mode.EXPLORE)"EDIT" else "EXPLORE")
                val inner=(ww-32f)/5f
                names.forEachIndexed { index,name->
                    val x=16f+index*inner
                    val active=when(index){
                        2->mode==Mode.DRAW_WALL;3->mode==Mode.ADD_ZONE
                        4->mode==Mode.EXPLORE;else->false
                    }
                    action(c,name,x,hh-94f,inner-5f,74f,captions[index],active) {
                        when(index){
                            0->{sheet=Sheet.LIBRARY;mode=Mode.EDIT}
                            1->{sheet=Sheet.SOUNDS;soundPage=0;mode=Mode.EDIT}
                            2->{sheet=Sheet.MATERIALS;mode=Mode.DRAW_WALL}
                            3->{sheet=Sheet.ZONES;mode=Mode.ADD_ZONE}
                            4->{
                                sheet=Sheet.NONE;mode=if(mode==Mode.EXPLORE)Mode.EDIT else Mode.EXPLORE
                                if(mode==Mode.EXPLORE && !audio.isPlaying)startPlayback()
                                if(mode==Mode.EXPLORE) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }
                            }
                        }
                        invalidate()
                    }
                }
            }
            val hint=when(mode) {
                Mode.EDIT -> "Drag sound dots or your listener  •  Pinch to zoom"
                Mode.DRAW_WALL -> "Drag across the map to build a wall"
                Mode.ADD_ZONE -> "Touch the map to place an acoustic room"
                Mode.EXPLORE -> "Tap a destination to walk and hear the world change"
            }
            box(c,16f,hh-(if(sheet==Sheet.NONE)140f else 124f),ww-16f,
                hh-(if(sheet==Sheet.NONE)112f else 96f),0xD5101E29.toInt(),9f)
            label(c,hint,23f,hh-(if(sheet==Sheet.NONE)122f else 106f),10f,0xFFB7C7CA.toInt())
            if(mode==Mode.EXPLORE && sheet==Sheet.NONE) {
                val y=hh-173f
                val x=ww-42f
                action(c,"minus",x-66,y,27f,27f,"",onClick={
                    world.facing-=PI.toFloat()/12f;changed()
                })
                action(c,"plus",x-29,y,27f,27f,"",onClick={
                    world.facing+=PI.toFloat()/12f;changed()
                })
                label(c,"TURN",x-68,y-6,9f,teal,true)
            }
        }
        private fun drawSheet(c:Canvas) {
            val height=when(sheet){Sheet.SOUNDS->min(500f,hh*.75f);Sheet.INSPECT->min(400f,hh*.77f)
                Sheet.LIBRARY->min(530f,hh*.77f);Sheet.MATERIALS->340f
                Sheet.ZONES->320f;Sheet.ATMOSPHERE->354f
                else->0f}
            val top=hh-height
            box(c,6f,top,ww-6f,hh+25f,0xFF122430.toInt(),23f)
            box(c,ww*.5f-20f,top+8f,ww*.5f+20f,top+12f,0xFF5C7379.toInt(),3f)
            val title=when(sheet){
                Sheet.LIBRARY->"WORLDS";Sheet.SOUNDS->"SOUND LIBRARY"
                Sheet.MATERIALS->"WALL MATERIAL";Sheet.ZONES->"ACOUSTIC ZONES"
                Sheet.ATMOSPHERE->"ENVIRONMENT"
                Sheet.INSPECT->if(selectedId!=null)"SOUND SOURCE"
                    else if(selectedWall>=0)"WALL" else "ROOM"
                else->""
            }
            label(c,title,23f,top+41f,15f,text,true)
            action(c,"close",ww-53f,top+19f,35f,35f,"",onClick={sheet=Sheet.NONE;invalidate()})
            when(sheet) {
                Sheet.LIBRARY->{
                    val gap=9f;val cell=(ww-54f-gap)/2f
                    AtlasPresets.titles.forEachIndexed { i,name->
                        val x=22f+(i%2)*(cell+gap);val y=top+61f+(i/2)*77f
                        box(c,x,y,x+cell,y+69f,0xFF1B3440.toInt(),12f)
                        label(c,("%02d".format(i+1)),x+10,y+20,10f,teal,true)
                        label(c,name.take(19),x+10,y+45,12f,text,true)
                        buttons+=Hit(RectF(x,y,x+cell,y+69f),"Open "+name){
                            replace(AtlasPresets.create(i));exploreRoute.clear()
                            sheet=Sheet.NONE;invalidate()
                        }
                    }
                    smallButton(c,"NEW EMPTY WORLD",20f,top+455f,ww*.48f-24f,39f){
                        replace(World("Untitled Atlas",72f,84f,V2(36f,42f)))
                    }
                    smallButton(c,"EXPORT .SATLAS",ww*.52f,top+455f,ww*.48f-20f,39f){
                        sheet=Sheet.NONE;exportScene();invalidate()
                    }
                }
                Sheet.SOUNDS->{
                    val totalPages=(SoundKind.entries.size+9)/10
                    val start=soundPage*10
                    val pageItems=SoundKind.entries.drop(start).take(10)
                    val cell=(ww-47f)/2f;val gap=7f
                    // Deliberately paginated: 18 cards never overlap the bottom
                    // edge on smaller devices. Proper 48dp minimum tap targets.
                    pageItems.forEachIndexed { i,kind->
                        val x=20f+(i%2)*(cell+gap)
                        val step=(height-130f)/5f
                        val y=top+78f+(i/2)*step
                        val ch=(step-7f).coerceAtLeast(48f)
                        box(c,x,y,x+cell,y+ch,0xFF1B3440.toInt(),10f)
                        c.drawCircle(x+15f,y+ch/2,5f,paint(kind.color))
                        label(c,kind.title,x+28f,y+ch/2+4f,11f,text,true)
                        buttons+=Hit(RectF(x,y,x+cell,y+ch),"Add "+kind.title){
                            if(world.sounds.size>=WorldLimits.MAX_SOURCES){
                                Toast.makeText(this@MainActivity,
                                    "Maximum ${WorldLimits.MAX_SOURCES} layers for reliable audio",
                                    Toast.LENGTH_SHORT).show()
                                return@Hit
                            }
                            val id=world.newId()
                            val offset=world.sounds.size%8
                            val pos=world.bound(world.listener.moved(
                                ((offset%4)-1.5f)*5f,((offset/4)-.5f)*6f))
                            world.sounds+=Emitter(id,kind,pos,.7f,true,false,id*7919+world.seed)
                            selectedId=id;selectedWall=-1;selectedZone=-1;sheet=Sheet.INSPECT
                            changed()
                        }
                    }
                    val pageLabel=(soundPage+1).toString()+" / "+totalPages
                    label(c,pageLabel,ww*.5f-12f,top+65f,11f,teal,true)
                    if(soundPage>0) {
                        smallButton(c,"PREVIOUS",20f,hh-57f,ww*.43f,42f){
                            soundPage--;invalidate()
                        }
                    }
                    if(soundPage<totalPages-1) {
                        smallButton(c,"NEXT SOUNDS",ww*.56f,hh-57f,ww*.40f,42f){
                            soundPage++;invalidate()
                        }
                    }
                }
                Sheet.MATERIALS->{
                    Material.entries.forEachIndexed { i,material->
                        val y=top+56f+i*39f
                        box(c,22f,y,ww-22f,y+34f,if(wallMaterial==material)
                            0xFF24514B.toInt() else 0xFF1B3440.toInt(),9f)
                        c.drawCircle(40f,y+17f,5f,paint(material.color))
                        label(c,material.title,55f,y+22f,12f,text,true)
                        buttons+=Hit(RectF(22f,y,ww-22f,y+34f),"Choose "+material.title+" walls"){
                            wallMaterial=material;mode=Mode.DRAW_WALL;sheet=Sheet.NONE;invalidate()
                        }
                    }
                }
                Sheet.ZONES->{
                    val options=listOf("Small room" to 2.1f,"Grand hall" to 4.7f,
                        "Open courtyard" to .4f,"Damped studio" to .22f)
                    options.forEachIndexed { i,option->
                        val y=top+57f+i*54f
                        box(c,22f,y,ww-22f,y+47f,0xFF1B3440.toInt(),10f)
                        label(c,option.first,36f,y+21f,12f,text,true)
                        label(c,"Reverb  "+option.second.toString()+" s",36f,y+37f,10f,teal)
                        buttons+=Hit(RectF(22f,y,ww-22f,y+47f),"Choose "+option.first+" room"){
                            selectedZoneType=i;mode=Mode.ADD_ZONE;sheet=Sheet.NONE;invalidate()
                        }
                    }
                }
                Sheet.ATMOSPHERE->drawAtmosphere(c,top)
                Sheet.INSPECT->drawInspector(c,top)
                else -> {}
            }
        }
        private fun smallButton(c:Canvas,title:String,x:Float,y:Float,w:Float,h:Float,click:()->Unit) {
            box(c,x,y,x+w,y+h,0xFF24514B.toInt(),11f)
            label(c,title,x+11f,y+h*.63f,11f,text,true)
            buttons+=Hit(RectF(x,y,x+w,y+h),title,click)
        }
        private fun drawAtmosphere(c:Canvas,top:Float) {
            label(c,"Changes are audible while you explore.",22f,top+65f,11f,subtle)
            val climate=listOf(Triple("RAIN DENSITY",world.rain,"Rain texture and intensity"),
                Triple("WIND STRENGTH",world.wind,"Air movement and foliage"),
                Triple("STORM ACTIVITY",world.storm,"Frequency and depth of thunder"))
            climate.forEachIndexed { index,option->
                val row=top+82f+index*78f
                box(c,19f,row,ww-19f,row+70f,0xFF1B3440.toInt(),12f)
                label(c,option.first,31f,row+22f,12f,text,true)
                label(c,option.third,31f,row+42f,10f,subtle)
                label(c,((option.second*100).roundToInt()).toString()+"%",31f,row+60f,11f,teal,true)
                action(c,"minus",ww-116f,row+16f,37f,37f,"",onClick={
                    when(index) {
                        0->world.rain=(world.rain-.1f).coerceIn(0f,1f)
                        1->world.wind=(world.wind-.1f).coerceIn(0f,1f)
                        else->world.storm=(world.storm-.1f).coerceIn(0f,1f)
                    };changed()
                })
                action(c,"plus",ww-67f,row+16f,37f,37f,"",onClick={
                    when(index) {
                        0->world.rain=(world.rain+.1f).coerceIn(0f,1f)
                        1->world.wind=(world.wind+.1f).coerceIn(0f,1f)
                        else->world.storm=(world.storm+.1f).coerceIn(0f,1f)
                    };changed()
                })
            }
        }
        private fun drawInspector(c:Canvas,top:Float) {
            if(selectedId!=null) {
                val source=world.sounds.firstOrNull {it.id==selectedId}
                if(source==null){sheet=Sheet.NONE;return}
                c.drawCircle(31f,top+78f,8f,paint(source.kind.color))
                label(c,source.kind.title,50f,top+82f,17f,text,true)
                label(c,source.kind.family+"  /  "+if(source.moving)"MOVING" else "STATIC",
                    22f,top+113f,10f,subtle)
                label(c,"GAIN   "+(source.gain*100).toInt()+"%",22f,top+152f,12f,teal,true)
                action(c,"minus",ww-107f,top+128f,38f,38f,"",onClick={
                    source.gain=(source.gain-.1f).coerceAtLeast(0f);changed()
                })
                action(c,"plus",ww-61f,top+128f,38f,38f,"",onClick={
                    source.gain=(source.gain+.1f).coerceAtMost(1.5f);changed()
                })
                smallButton(c,if(source.enabled)"MUTE" else "UNMUTE",
                    20f,top+180f,(ww-50)/2f,39f){
                    source.enabled=!source.enabled;changed()
                }
                smallButton(c,if(source.moving)"MAKE STATIC" else "ANIMATE",
                    ww*.5f+5f,top+180f,(ww-50)/2f,39f){
                    source.moving=!source.moving;changed()
                }
                label(c,"POSITION · MOVE 1 METER",22f,top+249f,11f,teal,true)
                val cell=(ww-43f)/4f
                val commands=listOf(
                    Triple("WEST",-1f,0f),Triple("NORTH",0f,-1f),
                    Triple("SOUTH",0f,1f),Triple("EAST",1f,0f))
                commands.forEachIndexed { index,(caption,dx,dy)->
                    smallButton(c,caption,18f+index*cell,top+267f,cell-6f,44f){
                        source.at=world.bound(source.at.moved(dx,dy))
                        changed()
                    }
                }
                smallButton(c,"REMOVE SOURCE",20f,top+335f,ww-40f,42f){
                    world.sounds.removeAll {it.id==selectedId};selectedId=null
                    sheet=Sheet.NONE;changed()
                }
            } else if(selectedWall>=0 && selectedWall<world.walls.size) {
                val wall=world.walls[selectedWall]
                label(c,wall.material.title,22f,top+83f,19f,text,true)
                label(c,"Material changes sound transmission and filtering",
                    22f,top+113f,11f,subtle)
                smallButton(c,"CHANGE MATERIAL",20f,top+145f,ww-40f,44f){
                    wall.material=Material.entries[(wall.material.ordinal+1)%Material.entries.size]
                    changed()
                }
                smallButton(c,"DELETE WALL",20f,top+213f,ww-40f,40f){
                    world.walls.removeAt(selectedWall);selectedWall=-1;sheet=Sheet.NONE;changed()
                }
            } else if(selectedZone>=0 && selectedZone<world.zones.size) {
                val zone=world.zones[selectedZone]
                label(c,zone.name,22f,top+85f,16f,text,true)
                label(c,"REVERB   "+("%.1f".format(zone.decay))+" s",
                    20f,top+126f,12f,teal,true)
                action(c,"minus",ww-108f,top+101f,38f,38f,"",onClick={
                    zone.decay=(zone.decay-.2f).coerceAtLeast(.1f);changed()
                })
                action(c,"plus",ww-60f,top+101f,38f,38f,"",onClick={
                    zone.decay=(zone.decay+.2f).coerceAtMost(12f);changed()
                })
                label(c,"ABSORPTION   "+(zone.damping*100).toInt()+"%",
                    20f,top+178f,12f,teal,true)
                action(c,"minus",ww-108f,top+152f,38f,38f,"",onClick={
                    zone.damping=(zone.damping-.1f).coerceAtLeast(0f);changed()
                })
                action(c,"plus",ww-60f,top+152f,38f,38f,"",onClick={
                    zone.damping=(zone.damping+.1f).coerceAtMost(1f);changed()
                })
                smallButton(c,"DELETE ROOM",20f,top+223f,ww-40f,38f){
                    world.zones.removeAt(selectedZone);selectedZone=-1;sheet=Sheet.NONE;changed()
                }
            }
        }

        private fun wallDistance(point:V2,wall:Wall):Float {
            val a=xy(wall.a);val b=xy(wall.b)
            val vx=b.x-a.x;val vy=b.y-a.y
            val t=(((point.x-a.x)*vx+(point.y-a.y)*vy)/
                (vx*vx+vy*vy).coerceAtLeast(.01f)).coerceIn(0f,1f)
            return hypot(point.x-(a.x+t*vx),point.y-(a.y+t*vy))
        }
        private fun findTarget(at:V2):Int {
            val lp=xy(world.listener)
            if(hypot(lp.x-at.x,lp.y-at.y)<20f)return -1
            for(s in world.sounds.asReversed()) {
                val p=xy(s.at)
                if(hypot(p.x-at.x,p.y-at.y)<20f)return s.id
            }
            return -2
        }
        private fun walkToTarget() {
            val target=exploreRoute.firstOrNull()?:return
            val dx=target.x-world.listener.x;val dy=target.y-world.listener.y
            val distance=hypot(dx,dy)
            if(distance<.18f){exploreRoute.removeAt(0);return}
            val amount=min(.24f,distance)
            val proposed=world.bound(world.listener.moved(dx/distance*amount,dy/distance*amount))
            if(!AtlasNavigator.traversable(world,world.listener,proposed)){
                exploreRoute.clear();return
            }
            world.listener=proposed
            world.facing=atan2(dx,-dy)
            camera=world.listener.copy()
            audio.update(world)
        }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            val pos=V2(event.x/density,event.y/density)
            when(event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN->{
                    if(event.pointerCount==2){pinching=true
                        pinchSpan=hypot(event.getX(0)-event.getX(1),
                            event.getY(0)-event.getY(1))
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP->{
                    pinching=false;pinchSpan=0f;dragTarget=null;return true
                }
                MotionEvent.ACTION_DOWN->{
                    down=pos;last=pos;moved=false;pinching=false
                    dragTarget=null
                    // UI consumes on finger release. Tapping the world always works between chrome areas.
                    if(buttons.any{it.rect.contains(pos.x,pos.y)})return true
                    if(sheet!=Sheet.NONE)return true
                    if(pos.y<102f || pos.y>hh-150f)return true
                    if(mode==Mode.EXPLORE)return true
                    dragTarget=if(mode==Mode.DRAW_WALL) -3
                        else if(mode==Mode.ADD_ZONE) -4 else findTarget(pos)
                    if(dragTarget==-3){
                        drawingWall=world.bound(unxy(pos));drawingEnd=drawingWall?.copy()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE->{
                    if(event.pointerCount>=2 && pinching) {
                        val span=hypot(event.getX(0)-event.getX(1),
                            event.getY(0)-event.getY(1))
                        if(pinchSpan>0f){
                            zoom=(zoom*span/pinchSpan).coerceIn(.4f,6f)
                            pinchSpan=span
                        }
                        invalidate();return true
                    }
                    if(hypot(pos.x-down.x,pos.y-down.y)>5f)moved=true
                    when(val target=dragTarget) {
                        -1 -> {world.listener=world.bound(unxy(pos));changed()}
                        -2 -> {camera=camera.moved((last.x-pos.x)/meter,(last.y-pos.y)/meter);invalidate()}
                        -3 -> {drawingEnd=world.bound(unxy(pos));invalidate()}
                        -4 -> {}
                        null -> {}
                        else -> {world.sounds.firstOrNull{it.id==target}?.let{
                            it.at=world.bound(unxy(pos));changed()
                        }}
                    }
                    last=pos
                    return true
                }
                MotionEvent.ACTION_CANCEL->{
                    dragTarget=null;drawingWall=null;drawingEnd=null;invalidate();return true
                }
                MotionEvent.ACTION_UP->{
                    // Handle sheet/control taps first, never leak taps into the world underneath.
                    if(!moved) {
                        val hit=buttons.lastOrNull{it.rect.contains(pos.x,pos.y)}
                        if(hit!=null){hit.action();dragTarget=null;invalidate();return true}
                    }
                    if(sheet!=Sheet.NONE){dragTarget=null;return true}
                    if(pos.y<102f || pos.y>hh-150f){dragTarget=null;return true}
                    if(mode==Mode.EXPLORE) {
                        val next=world.bound(unxy(pos))
                        exploreRoute.clear()
                        exploreRoute.addAll(AtlasNavigator.route(world,world.listener,next))
                        if(exploreRoute.isEmpty())
                            Toast.makeText(this@MainActivity,"No walkable route",Toast.LENGTH_SHORT).show()
                        invalidate();return true
                    }
                    when(val target=dragTarget) {
                        -3 -> {
                            val a=drawingWall;val b=drawingEnd?:world.bound(unxy(pos))
                            if(a!=null && a.distance(b)>1f){
                                if(world.walls.size>=WorldLimits.MAX_WALLS){
                                    Toast.makeText(this@MainActivity,
                                        "Wall limit reached for smooth playback",Toast.LENGTH_SHORT).show()
                                }else{world.walls+=Wall(a,b,wallMaterial);changed()}
                            }
                        }
                        -4 -> {
                            val p=world.bound(unxy(pos))
                            val props=listOf(Triple("Small room",2.1f,.35f),
                                Triple("Grand hall",4.7f,.19f),
                                Triple("Open courtyard",.4f,.8f),
                                Triple("Damped studio",.22f,.92f))[selectedZoneType]
                            val x=(p.x-12f).coerceIn(0f,(world.width-24f).coerceAtLeast(0f))
                            val y=(p.y-10f).coerceIn(0f,(world.height-20f).coerceAtLeast(0f))
                            if(world.zones.size>=WorldLimits.MAX_ZONES){
                                Toast.makeText(this@MainActivity,
                                    "Room limit reached for smooth playback",Toast.LENGTH_SHORT).show()
                            }else{
                                world.zones+=Zone(props.first,x,y,24f,20f,props.second,props.third)
                                mode=Mode.EDIT;changed()
                            }
                        }
                        -1 -> if(!moved) {
                            selectedId=null;selectedWall=-1;selectedZone=-1;invalidate()
                        }
                        -2 -> if(!moved){
                            val wIndex=world.walls.indexOfFirst{wallDistance(pos,it)<10f}
                            val zIndex=world.zones.indexOfLast{it.contains(unxy(pos))}
                            if(wIndex>=0){
                                selectedWall=wIndex;selectedId=null;selectedZone=-1;sheet=Sheet.INSPECT
                            }else if(zIndex>=0){
                                selectedZone=zIndex;selectedWall=-1;selectedId=null;sheet=Sheet.INSPECT
                            }
                        }
                        null -> {}
                        else -> {
                            selectedId=target;selectedWall=-1;selectedZone=-1
                            if(!moved)sheet=Sheet.INSPECT
                        }
                    }
                    dragTarget=null;drawingWall=null;drawingEnd=null
                    invalidate();return true
                }
            }
            return true
        }
    }
}
