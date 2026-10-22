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
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Atomic app-private progress and photo storage. No permissions/network required. */
class PuzzleStore(private val context: Context) {
    private val saveFile get() = File(context.filesDir,"active-jigsaw.json")
    private val photoFile get() = File(context.filesDir,"jigsaw-photo.jpg")

    fun save(g: PuzzleGame) {
        val root=JSONObject()
        root.put("cols",g.cols);root.put("rows",g.rows)
        root.put("seed",g.seed);root.put("image",g.imageKey)
        root.put("hints",g.hintsRemaining);root.put("moves",g.moves)
        root.put("preview",g.preview)
        root.put("landscape",g.landscapeMode)
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
        if (image=="custom" && !photoFile.exists()) return null
        PuzzleGame.restore(obj.getInt("cols"),obj.getInt("rows"),obj.getLong("seed"),
            image, positions, obj.optInt("hints",3),
            obj.optInt("moves",0),obj.optBoolean("preview",false),
            obj.optBoolean("landscape",false))
    }.getOrNull()

    fun loadPhoto(): Bitmap? = BitmapFactory.decodeFile(photoFile.absolutePath)

    /** Resize/crop the selected local photo before storing it; do not retain a fragile gallery URI. */
    fun importPhoto(uri: Uri): Bitmap {
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
        val result=Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888)
        val sw=upright.width;val sh=upright.height
        val ratio=4f/3f
        val cropW=if(sw.toFloat()/sh>ratio) (sh*ratio).roundToInt().coerceIn(1,sw) else sw
        val cropH=if(sw.toFloat()/sh>ratio) sh else (sw/ratio).roundToInt().coerceIn(1,sh)
        val left=(sw-cropW)/2;val top=(sh-cropH)/2
        Canvas(result).drawBitmap(upright,Rect(left,top,left+cropW,top+cropH),
            RectF(0f,0f,960f,720f),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        upright.recycle()
        val tmp=File(context.filesDir,"jigsaw-photo.jpg.tmp")
        tmp.outputStream().use { stream ->
            check(result.compress(Bitmap.CompressFormat.JPEG,90,stream))
        }
        if (!tmp.renameTo(photoFile)) {
            tmp.copyTo(photoFile,overwrite=true);tmp.delete()
        }
        return result
    }
}
