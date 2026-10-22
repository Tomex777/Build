package com.night.jigsaw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SavedPuzzlePhoto(val key: String, val title: String,
                            val source: String = "Personal")
data class ImportedPuzzlePhoto(val photo: SavedPuzzlePhoto, val bitmap: Bitmap)

/** Atomic app-private progress and multi-photo storage; no storage permissions. */
class PuzzleStore(private val context: Context) {
    private val saveFile get() = File(context.filesDir,"active-jigsaw.json")
    private val photoFile get() = File(context.filesDir,"jigsaw-photo.jpg")
    private val photoDirectory get() = File(context.filesDir,"saved-photos").also{it.mkdirs()}
    private val photoIndex get() = File(context.filesDir,"saved-photos.json")

    private fun imageFile(key: String): File? {
        if(key=="custom")return photoFile  // migration for early development saves.
        if(!key.matches(Regex("photo-[a-f0-9-]{36,40}")))return null
        return File(photoDirectory,key + ".jpg")
    }

    fun listPhotos(): List<SavedPuzzlePhoto> = runCatching {
        val results=mutableListOf<SavedPuzzlePhoto>()
        if(photoFile.exists())results.add(SavedPuzzlePhoto("custom","Previously imported photo"))
        if(photoIndex.exists()) {
            val items=JSONArray(photoIndex.readText())
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i)
                val key=item.getString("key")
                val saved=imageFile(key)
                if(saved?.isFile==true)results.add(
                    SavedPuzzlePhoto(key,item.optString("title","My photo").take(70),
                        item.optString("source","Personal").take(30)))
            }
        }
        results
    }.getOrDefault(emptyList())

    private fun appendPhoto(photo: SavedPuzzlePhoto) {
        val previous=if(photoIndex.exists())runCatching{
            JSONArray(photoIndex.readText())
        }.getOrElse{JSONArray()} else JSONArray()
        previous.put(JSONObject().put("key",photo.key).put("title",photo.title)
            .put("source",photo.source))
        val tmp=File(context.filesDir,"saved-photos.json.tmp")
        tmp.writeText(previous.toString())
        if(!tmp.renameTo(photoIndex)) {
            photoIndex.writeText(previous.toString())
            tmp.delete()
        }
    }

    fun save(g: PuzzleGame) {
        val root=JSONObject()
        root.put("cols",g.cols);root.put("rows",g.rows)
        root.put("seed",g.seed);root.put("image",g.imageKey)
        root.put("hints",g.hintsRemaining);root.put("moves",g.moves)
        root.put("preview",g.preview)
        root.put("landscape",g.landscapeMode)
        root.put("zone",g.activeZone)
        root.put("edgesOnly",g.edgesOnly)
        root.put("mode",g.mode.name)
        root.put("previewsRemaining",g.previewsRemaining)
        root.put("elapsedSeconds",g.elapsedSeconds)
        root.put("groups",JSONArray().also{group->
            g.pieces.forEach{piece->group.put(piece.groupId)}
        })
        val arr=JSONArray()
        g.pieces.forEach { p ->
            arr.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble()).put(p.placed))
        }
        root.put("pieces",arr)
        val tmp=File(context.filesDir,"active-jigsaw.json.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(saveFile)) {
            saveFile.writeText(root.toString())
            tmp.delete()
        }
    }

    fun load(): PuzzleGame? = runCatching {
        if (!saveFile.exists()) return null
        val obj=JSONObject(saveFile.readText())
        val arr=obj.getJSONArray("pieces")
        val positions=(0 until arr.length()).map { i ->
            val p=arr.getJSONArray(i)
            Triple(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),p.getBoolean(2))
        }
        val image=obj.getString("image")
        if (image!="starter" && imageFile(image)?.isFile!=true) return null
        val rawGroups=obj.optJSONArray("groups")
        val groups=rawGroups?.let{ array->
            (0 until array.length()).map{array.getInt(it)}
        }
        val mode=runCatching{
            PuzzleMode.valueOf(obj.optString("mode",PuzzleMode.RELAXED.name))
        }.getOrDefault(PuzzleMode.RELAXED)
        PuzzleGame.restore(obj.getInt("cols"),obj.getInt("rows"),obj.getLong("seed"),
            image, positions, obj.optInt("hints",3),
            obj.optInt("moves",0),obj.optBoolean("preview",false),
            obj.optBoolean("landscape",false),obj.optInt("zone",0),groups,
            obj.optBoolean("edgesOnly",false),mode,
            obj.optInt("previewsRemaining",mode.allowedPreviews),
            obj.optInt("elapsedSeconds",0))
    }.getOrNull()

    fun loadPhoto(key: String="custom"): Bitmap? =
        imageFile(key)?.takeIf{it.isFile}?.let{ BitmapFactory.decodeFile(it.absolutePath) }

    fun loadThumbnail(key: String): Bitmap? = imageFile(key)?.takeIf{it.isFile}?.let{
        BitmapFactory.decodeFile(it.absolutePath,
            BitmapFactory.Options().apply{inSampleSize=8})
    }

    /** Resize/crop the selected local photo before storing it; do not retain a fragile gallery URI. */
    fun importPhoto(uri: Uri, titleOverride: String?=null,
                    source: String="Personal"): ImportedPuzzlePhoto {
        val resolver=context.contentResolver
        val probe=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,probe) }
        require(probe.outWidth>0 && probe.outHeight>0) { "This file is not a supported image." }
        var sample=1
        while (max(probe.outWidth,probe.outHeight)/sample>2200) sample*=2
        val opts=BitmapFactory.Options().apply { inSampleSize=sample }
        val decoded=resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it,null,opts)
        } ?: error("The photo could not be opened.")
        val orientation=resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotation=when(orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright=if(rotation!=0f) Bitmap.createBitmap(decoded,0,0,
            decoded.width,decoded.height,Matrix().apply { postRotate(rotation) },true)
            else decoded
        if (upright !== decoded) decoded.recycle()
        val result=Bitmap.createBitmap(2400,1800,Bitmap.Config.ARGB_8888)
        val sw=upright.width;val sh=upright.height
        val ratio=4f/3f
        val cropW=if(sw.toFloat()/sh>ratio) (sh*ratio).roundToInt().coerceIn(1,sw) else sw
        val cropH=if(sw.toFloat()/sh>ratio) sh else (sw/ratio).roundToInt().coerceIn(1,sh)
        val left=(sw-cropW)/2;val top=(sh-cropH)/2
        Canvas(result).drawBitmap(upright,Rect(left,top,left+cropW,top+cropH),
            RectF(0f,0f,2400f,1800f),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        upright.recycle()
        val key="photo-"+UUID.randomUUID().toString()
        val title=titleOverride?.take(70)?.ifBlank{null} ?: runCatching{
            context.contentResolver.query(uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
                if(cursor.moveToFirst())cursor.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast(".")?.take(70)?.ifBlank{null} ?: "My photo"
        val item=SavedPuzzlePhoto(key,title,source.take(30))
        val destination=checkNotNull(imageFile(key))
        val tmp=File(photoDirectory,key+".tmp")
        tmp.outputStream().use { stream ->
            check(result.compress(Bitmap.CompressFormat.JPEG,90,stream))
        }
        if (!tmp.renameTo(destination)) {
            tmp.copyTo(destination,overwrite=true);tmp.delete()
        }
        appendPhoto(item)
        return ImportedPuzzlePhoto(item,result)
    }
}
