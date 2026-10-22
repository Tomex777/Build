package com.night.jigsaw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.sin

/** Original illustrated starter artwork; do not label it a photograph. */
object SceneImage {
    fun create(): Bitmap {
        val image=Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888)
        val c=Canvas(image)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,0f,500f,Color.rgb(28,65,111),
            Color.rgb(246,174,126),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,960f,720f,p)
        p.shader=null
        p.color=Color.rgb(255,219,155)
        c.drawCircle(730f,236f,70f,p)
        fun ridge(color:Int, vararg points:Float) {
            p.color=color;p.style=Paint.Style.FILL
            val shape=Path()
            shape.moveTo(points[0],points[1])
            var i=2
            while (i+1<points.size) {shape.lineTo(points[i],points[i+1]);i+=2}
            shape.close()
            c.drawPath(shape,p)
        }
        ridge(Color.rgb(103,110,132),0f,440f,150f,260f,238f,365f,
            350f,185f,450f,360f,590f,245f,720f,420f,840f,305f,
            960f,410f,960f,600f,0f,600f)
        ridge(Color.rgb(237,232,209),296f,239f,350f,185f,416f,283f,
            382f,262f,350f,227f,333f,260f)
        ridge(Color.rgb(62,89,105),0f,470f,152f,338f,300f,450f,
            440f,304f,580f,475f,750f,365f,960f,468f,960f,600f,0f,600f)
        p.shader=LinearGradient(0f,480f,0f,720f,
            Color.rgb(86,153,158),Color.rgb(24,67,92),Shader.TileMode.CLAMP)
        c.drawRect(0f,498f,960f,720f,p);p.shader=null
        p.color=Color.argb(85,222,223,210)
        for (i in 0..22) {
            val y=514f+i*10
            val x=(sin(i.toDouble()*1.9)*54).toFloat()+50f
            c.drawRoundRect(x,y,960f-(i%4)*72f,y+2f,2f,2f,p)
        }
        ridge(Color.rgb(27,59,59),0f,512f,78f,452f,110f,497f,163f,465f,
            208f,514f,960f,518f,960f,532f,0f,536f)
        p.color=Color.rgb(28,66,60)
        for (i in 0..21) {
            val x=i*47f-12f
            val ht=28f+(i*47%66)
            val path=Path().apply {
                moveTo(x,518f-ht)
                lineTo(x-18f,518f)
                lineTo(x+18f,518f)
                close()
            }
            c.drawPath(path,p)
        }
        return image
    }
}
