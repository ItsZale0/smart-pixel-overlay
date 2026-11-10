package com.zampediverse.smartpixel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

class SmartPixelView(context: Context) : View(context) {
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val matrix = Matrix()
    private var patternBitmap: Bitmap? = null
    private var cachedKey: String? = null
    private var drawingEnabled = true

    var intensity: Int = 25
        set(value) {
            val v = value.coerceIn(0, 100)
            if (field != v) { field = v; cachedKey = null; invalidate() }
        }
    var pattern: Int = 0
        set(value) {
            if (field != value) { field = value; cachedKey = null; invalidate() }
        }
    var shiftIndex: Int = 0
        set(value) { field = value; invalidate() }

    var isDrawing: Boolean = true
        set(value) { drawingEnabled = value; invalidate() }

    // Logical cell size in device pixels for the mask tile.
    private val cell = 1

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawingEnabled) return
        val pct = intensity
        if (pct <= 0) return
        if (pct >= 100) {
            canvas.drawColor(Color.BLACK)
            return
        }
        ensurePattern()
        val bmp = patternBitmap ?: return
        val ox = (mod(shiftIndex, 4) - 1) * cell.toFloat()
        val oy = (mod(shiftIndex / 4, 4) - 1) * cell.toFloat()
        matrix.reset()
        matrix.setTranslate(ox, oy)
        (paint.shader as? BitmapShader)?.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun ensurePattern() {
        val key = "$pattern:$intensity:$cell"
        if (key == cachedKey && patternBitmap != null) return
        cachedKey = key
        patternBitmap?.recycle()

        val period = when (pattern) {
            0 -> 2
            1 -> 3
            2 -> 4
            3 -> 6
            4 -> 4
            5 -> 2
            else -> 4
        }
        val bmp = Bitmap.createBitmap(period * cell, period * cell, Bitmap.Config.ARGB_8888)
        for (r in 0 until period) {
            for (c in 0 until period) {
                val on = shouldMask(c, r, intensity, pattern)
                val color = if (on) Color.BLACK else Color.TRANSPARENT
                for (dy in 0 until cell) {
                    val py = r * cell + dy
                    for (dx in 0 until cell) {
                        bmp.setPixel(c * cell + dx, py, color)
                    }
                }
            }
        }
        patternBitmap = bmp
        paint.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun shouldMask(c: Int, r: Int, pct: Int, pat: Int): Boolean {
        if (pct >= 100) return true
        val period = when (pat) {
            0 -> 2
            1 -> 3
            2 -> 4
            3 -> 6
            4 -> 4
            5 -> 2
            else -> 4
        }
        val cc = mod(c, period)
        val rr = mod(r, period)
        val idx = when (pat) {
            5 -> if ((cc + rr) % 2 == 0) 0 else 1
            4 -> mod(cc + rr, period)
            else -> rr * period + cc
        }
        val total = if (pat == 5) 2 else if (pat == 4) period else period * period
        val threshold = (pct * total + 50) / 100
        val rank = rankIndex(idx, total, pat)
        return rank < threshold
    }

    private fun rankIndex(idx: Int, total: Int, pat: Int): Int {
        return when (pat) {
            5 -> idx
            4 -> idx
            else -> (idx * 7 + 3) % total
        }
    }

    private fun mod(a: Int, b: Int): Int {
        val m = a % b
        return if (m < 0) m + b else m
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        patternBitmap?.recycle()
        patternBitmap = null
        cachedKey = null
        paint.shader = null
    }
}
