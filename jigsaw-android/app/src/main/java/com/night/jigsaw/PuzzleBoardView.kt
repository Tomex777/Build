package com.night.jigsaw

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.View
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native hardware-accelerated tabletop.
 * Piece silhouettes and photo cutouts are rasterized ONCE when a puzzle changes;
 * dragging only composites cached bitmaps and never rescales the source 144 times/frame.
 */
class PuzzleBoardView(context: Context) : View(context) {
    private data class Sprite(val bitmap: Bitmap, val silhouette: Path, val margin: Float)
    private var game: PuzzleGame? = null
    private var photo: Bitmap? = null
    private var notifyChange: () -> Unit = {}
    private val sprites = mutableMapOf<Int, Sprite>()
    private val drawOrder = mutableListOf<Int>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private var baseScale = 1f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var activePiece = -1
    private var snapAnimator: ValueAnimator? = null
    // Keep a grabbed piece under the finger while the tabletop scrolls toward an offscreen target.
    private val edgeScroller = object : Runnable {
        override fun run() {
            val id=activePiece
            val g=game
            if (id<0 || g==null) return
            val band=56f*resources.displayMetrics.density
            val maxShift=7f*resources.displayMetrics.density
            val shiftX=when {
                lastX<band -> ((band-lastX)/band).coerceIn(0f,1f)*maxShift
                lastX>width-band -> -((lastX-width+band)/band).coerceIn(0f,1f)*maxShift
                else -> 0f
            }
            val shiftY=when {
                lastY<band -> ((band-lastY)/band).coerceIn(0f,1f)*maxShift
                lastY>height-band -> -((lastY-height+band)/band).coerceIn(0f,1f)*maxShift
                else -> 0f
            }
            if (shiftX!=0f || shiftY!=0f) {
                panX+=shiftX;panY+=shiftY
                val p=g.pieces[id]
                g.movePiece(id,p.x-shiftX/(baseScale*zoom),p.y-shiftY/(baseScale*zoom))
                invalidate()
            }
            postOnAnimation(this)
        }
    }

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun bind(newGame: PuzzleGame, newPhoto: Bitmap, onChange: () -> Unit) {
        notifyChange = onChange
        if (game === newGame && photo === newPhoto && sprites.isNotEmpty()) { invalidate(); return }
        snapAnimator?.cancel()
        snapAnimator=null
        game = newGame
        photo = newPhoto
        sprites.values.forEach { it.bitmap.recycle() }
        sprites.clear()
        drawOrder.clear()
        drawOrder.addAll(newGame.pieces.indices)
        val scaled = Bitmap.createScaledBitmap(newPhoto,
            PuzzleGame.BOARD_WIDTH.toInt(), PuzzleGame.BOARD_HEIGHT.toInt(), true)
        newGame.pieces.forEach { sprites[it.id] = makeSprite(newGame, it, scaled) }
        if (scaled !== newPhoto) scaled.recycle()
        centerBoard()
    }

    fun centerBoard() {
        baseScale = (width.coerceAtLeast(1)).toFloat() / PuzzleGame.WORLD_WIDTH
        zoom = 1f
        panX = (width - PuzzleGame.WORLD_WIDTH * baseScale) / 2f
        panY = height / 2f -
            ((game?.boardTop ?: 640f) + PuzzleGame.BOARD_HEIGHT / 2f) * baseScale
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerBoard()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        canvas.drawColor(Color.rgb(239, 235, 227))
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(baseScale * zoom, baseScale * zoom)

        paper.color = Color.rgb(224, 219, 208)
        canvas.drawRoundRect(RectF(30f, 25f, 970f, g.worldHeight), 18f, 18f, paper)
        val board = RectF(PuzzleGame.BOARD_LEFT, g.boardTop,
            PuzzleGame.BOARD_LEFT + PuzzleGame.BOARD_WIDTH,
            g.boardTop + PuzzleGame.BOARD_HEIGHT)
        paper.color = Color.rgb(252, 250, 246)
        canvas.drawRect(board, paper)
        if (g.preview) {
            paint.alpha = 64
            canvas.drawBitmap(photo!!, null, board, paint)
            paint.alpha = 255
        }
        stroke.style = Paint.Style.STROKE
        stroke.color = Color.rgb(176, 169, 154)
        stroke.strokeWidth = 2.1f
        canvas.drawRect(board, stroke)

        // Placed pieces stay beneath movable ones and no longer intercept input.
        for (p in g.pieces) if (p.placed) renderPiece(canvas, p, false)
        for (id in drawOrder) {
            val p = g.pieces[id]
            if (!p.placed) renderPiece(canvas, p, id == activePiece)
        }
        canvas.restore()
    }

    private fun renderPiece(canvas: Canvas, p: PuzzlePiece, selected: Boolean) {
        val sprite = sprites[p.id] ?: return
        paint.alpha = 255
        canvas.drawBitmap(sprite.bitmap, p.x - sprite.margin,
            p.y - sprite.margin, paint)
        if (selected) {
            canvas.save()
            canvas.translate(p.x, p.y)
            stroke.style = Paint.Style.STROKE
            stroke.color = Color.rgb(42, 100, 72)
            stroke.strokeWidth = 2.7f
            canvas.drawPath(sprite.silhouette, stroke)
            canvas.restore()
        }
    }

    private fun makeSprite(g: PuzzleGame, p: PuzzlePiece, scaled: Bitmap): Sprite {
        val cw = g.pieceWidth
        val ch = g.pieceHeight
        val margin = min(cw, ch) * 0.38f + 4f
        val path = piecePath(g, p)
        val bitmap = Bitmap.createBitmap(
            ceil(cw + 2f * margin).toInt().coerceAtLeast(8),
            ceil(ch + 2f * margin).toInt().coerceAtLeast(8),
            Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.translate(margin, margin)
        c.save()
        c.translate(1.4f, 2.5f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(92, 38, 30, 20)
            style = Paint.Style.FILL
        }
        c.drawPath(path, shadow)
        c.restore()
        c.save()
        c.clipPath(path)
        val target = RectF(-p.col * cw, -p.row * ch,
            g.pieceWidth * g.cols - p.col * cw,
            g.pieceHeight * g.rows - p.row * ch)
        c.drawBitmap(scaled, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(185, 56, 48, 40)
            strokeWidth = 1.35f
        }
        c.drawPath(path, line)
        return Sprite(bitmap, path, margin)
    }

    private fun piecePath(g: PuzzleGame, p: PuzzlePiece): Path {
        val w = g.pieceWidth
        val h = g.pieceHeight
        val depth = min(w, h) * 0.30f
        val path = Path()
        path.moveTo(0f, 0f)
        addEdge(path, 0f, 0f, w, 0f, 0f, -1f, g.edge(p.row,p.col,0),depth)
        addEdge(path, w, 0f, w, h, 1f, 0f, g.edge(p.row,p.col,1),depth)
        addEdge(path, w, h, 0f, h, 0f, 1f, g.edge(p.row,p.col,2),depth)
        addEdge(path, 0f, h, 0f, 0f, -1f, 0f, g.edge(p.row,p.col,3),depth)
        path.close()
        return path
    }

    private fun addEdge(path: Path, sx: Float, sy: Float, ex: Float, ey: Float,
                        nx: Float, ny: Float, sign: Int, depth: Float) {
        if (sign == 0) { path.lineTo(ex, ey); return }
        fun px(t: Float, d: Float) = sx + (ex-sx)*t + nx * sign * depth * d
        fun py(t: Float, d: Float) = sy + (ey-sy)*t + ny * sign * depth * d
        path.lineTo(px(.32f,0f), py(.32f,0f))
        path.cubicTo(px(.42f,0f),py(.42f,0f),
            px(.34f,.33f),py(.34f,.33f),px(.4f,.38f),py(.4f,.38f))
        path.cubicTo(px(.35f,.98f),py(.35f,.98f),
            px(.65f,.98f),py(.65f,.98f),px(.6f,.38f),py(.6f,.38f))
        path.cubicTo(px(.66f,.33f),py(.66f,.33f),
            px(.58f,0f),py(.58f,0f),px(.68f,0f),py(.68f,0f))
        path.lineTo(ex, ey)
    }

    private fun worldX(x: Float) = (x - panX) / (baseScale * zoom)
    private fun worldY(y: Float) = (y - panY) / (baseScale * zoom)

    private fun hitTest(wx: Float, wy: Float): Int {
        val g = game ?: return -1
        for (id in drawOrder.asReversed()) {
            val p = g.pieces[id]
            if (p.placed) continue
            val s = sprites[id] ?: continue
            val clip = Region(-256, -256, g.pieceWidth.toInt()+256, g.pieceHeight.toInt()+256)
            val shape = Region()
            shape.setPath(s.silhouette,clip)
            if (shape.contains((wx-p.x).roundToInt(),(wy-p.y).roundToInt())) return id
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (game == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX=event.x;lastY=event.y
                activePiece=hitTest(worldX(event.x),worldY(event.y))
                if (activePiece >= 0) {
                    drawOrder.remove(activePiece);drawOrder.add(activePiece)
                    removeCallbacks(edgeScroller)
                    postOnAnimation(edgeScroller)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePiece>=0) notifyChange()
                activePiece=-1
                if (event.pointerCount >= 2) {
                    lastX=(event.getX(0)+event.getX(1))/2f
                    lastY=(event.getY(0)+event.getY(1))/2f
                    pinchDistance=hypot((event.getX(0)-event.getX(1)).toDouble(),
                        (event.getY(0)-event.getY(1)).toDouble()).toFloat()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val fx=(event.getX(0)+event.getX(1))/2f
                    val fy=(event.getY(0)+event.getY(1))/2f
                    val dist=hypot((event.getX(0)-event.getX(1)).toDouble(),
                        (event.getY(0)-event.getY(1)).toDouble()).toFloat()
                    val wx=worldX(fx); val wy=worldY(fy)
                    if (pinchDistance>5f && dist>5f)
                        zoom=(zoom*(dist/pinchDistance)).coerceIn(.7f,5f)
                    panX=fx-wx*baseScale*zoom;panY=fy-wy*baseScale*zoom
                    lastX=fx;lastY=fy;pinchDistance=dist
                } else if (activePiece>=0) {
                    val g=game!!
                    val p=g.pieces[activePiece]
                    val dx=(event.x-lastX)/(baseScale*zoom)
                    val dy=(event.y-lastY)/(baseScale*zoom)
                    g.movePiece(activePiece,p.x+dx,p.y+dy)
                    lastX=event.x;lastY=event.y
                } else {
                    panX+=event.x-lastX;panY+=event.y-lastY
                    lastX=event.x;lastY=event.y
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Keep viewport stationary when returning from a two-finger gesture.
                activePiece=-1
                val remaining = if (event.actionIndex==0) 1 else 0
                lastX=event.getX(remaining);lastY=event.getY(remaining)
                pinchDistance=0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activePiece>=0) {
                    val id=activePiece
                    val g=game!!
                    val p=g.pieces[id]
                    val fromX=p.x
                    val fromY=p.y
                    if (event.actionMasked==MotionEvent.ACTION_UP && g.release(id)) {
                        val toX=p.x
                        val toY=p.y
                        p.x=fromX;p.y=fromY
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        snapAnimator?.cancel()
                        snapAnimator=ValueAnimator.ofFloat(0f,1f).apply {
                            duration=170L
                            interpolator=DecelerateInterpolator()
                            addUpdateListener { animation ->
                                val t=animation.animatedValue as Float
                                p.x=fromX+(toX-fromX)*t
                                p.y=fromY+(toY-fromY)*t
                                invalidate()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    p.x=toX;p.y=toY
                                    notifyChange()
                                    invalidate()
                                }
                            })
                            start()
                        }
                    } else notifyChange()
                }
                activePiece=-1
                performClick()
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        snapAnimator?.cancel()
        snapAnimator=null
        removeCallbacks(edgeScroller)
        sprites.values.forEach { it.bitmap.recycle() }
        sprites.clear()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
