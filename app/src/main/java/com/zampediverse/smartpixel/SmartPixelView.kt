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
    var spacing: Int = 0 // 0 = custom (%/pattern), >=2 = 1 pixel off every N (NxN)
        set(value) {
            val v = value.coerceIn(0, 16)
            if (field != v) { field = v; cachedKey = null; invalidate() }
        }
    var shiftIndex: Int = 0
        set(value) { field = value; invalidate() }

    var isDrawing: Boolean = true
        set(value) { drawingEnabled = value; invalidate() }

    // Logical cell size in device pixels for the mask tile.
    private val cell = 1

    // Rank table for 4x4 checkerboard: black cells get low ranks (0-7),
    // white cells get high ranks (8-15) so intensity slider works linearly.
    private val checkerboardRank = intArrayOf(
        0, 8, 1, 9,
        10, 2, 11, 3,
        4, 12, 5, 13,
        14, 6, 15, 7
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawingEnabled) return
        if (spacing >= 2) {
            ensurePattern()
            if (patternBitmap == null) return
            val ox = (mod(shiftIndex, 4) - 1) * cell.toFloat()
            val oy = (mod(shiftIndex / 4, 4) - 1) * cell.toFloat()
            matrix.reset()
            matrix.setTranslate(ox, oy)
            (paint.shader as? BitmapShader)?.setLocalMatrix(matrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }
        val pct = intensity
        if (pct <= 0) return
        if (pct >= 100) {
            canvas.drawColor(Color.BLACK)
            return
        }
        ensurePattern()
        if (patternBitmap == null) return
        val ox = (mod(shiftIndex, 4) - 1) * cell.toFloat()
        val oy = (mod(shiftIndex / 4, 4) - 1) * cell.toFloat()
        matrix.reset()
        matrix.setTranslate(ox, oy)
        (paint.shader as? BitmapShader)?.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun ensurePattern() {
        val key = "$pattern:$intensity:$spacing:$cell"
        if (key == cachedKey && patternBitmap != null) return
        cachedKey = key
        patternBitmap?.recycle()

        if (spacing >= 2) {
            val period = spacing
            val bmp = Bitmap.createBitmap(period * cell, period * cell, Bitmap.Config.ARGB_8888)
            for (r in 0 until period) {
                for (c in 0 until period) {
                    val on = (c == 0 && r == 0) // 1 pixel spento ogni N (NxN)
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
            return
        }

        val period = when (pattern) {
            0 -> 2 // 2x2 grid
            1 -> 3 // 3x3 grid
            2 -> 4 // 4x4 grid
            3 -> 6 // 6x6 grid
            4 -> 4 // diagonal
            5 -> 4 // checkerboard 4x4 (linear intensity)
            else -> 4
        }
        val total = period * period
        val threshold = (intensity * total + 50) / 100

        val bmp = Bitmap.createBitmap(period * cell, period * cell, Bitmap.Config.ARGB_8888)
        for (r in 0 until period) {
            for (c in 0 until period) {
                val on = shouldMask(c, r, pattern, total, threshold)
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

    private fun shouldMask(c: Int, r: Int, pat: Int, total: Int, threshold: Int): Boolean {
        val period = when (pat) {
            0 -> 2
            1 -> 3
            2 -> 4
            3 -> 6
            4 -> 4
            5 -> 4
            else -> 4
        }
        val cc = mod(c, period)
        val rr = mod(r, period)
        val idx = rr * period + cc

        val rank = when (pat) {
            5 -> checkerboardRank[rr * 4 + cc] // checkerboard with linear intensity
            4 -> mod(cc + rr, period)          // diagonal
            else -> (idx * 7 + 3) % total      // other grids with pseudo-random order
        }

        return rank < threshold
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
