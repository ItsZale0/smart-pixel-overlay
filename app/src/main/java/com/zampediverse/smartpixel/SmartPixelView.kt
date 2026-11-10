package com.zampediverse.smartpixel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.ceil

class SmartPixelView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    var intensity: Int = 25
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var pattern: Int = 0
        set(value) { field = value; invalidate() }
    var shiftIndex: Int = 0
        set(value) { field = value; invalidate() }

    // Base cell size in px (logical "pixel block")
    private val cell = 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pct = intensity
        if (pct <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val cols = ceil(w / cell).toInt() + 2
        val rows = ceil(h / cell).toInt() + 2

        val ox = ((shiftIndex % 4) - 1) * cell
        val oy = (((shiftIndex / 4) % 4) - 1) * cell

        for (r in 0 until rows) {
            val y = r * cell + oy
            if (y > h) break
            for (c in 0 until cols) {
                val x = c * cell + ox
                if (x > w) break
                if (shouldMask(c, r, pct, pattern)) {
                    canvas.drawRect(x, y, x + cell + 0.5f, y + cell + 0.5f, paint)
                }
            }
        }
    }

    private fun shouldMask(c: Int, r: Int, pct: Int, pat: Int): Boolean {
        if (pct >= 100) return true
        val period = when (pat) {
            0 -> 2 // 2x2
            1 -> 3 // 3x3
            2 -> 4 // 4x4
            3 -> 6 // 6x6
            4 -> 4 // diagonal
            5 -> 2 // checker
            else -> 4
        }
        val cc = mod(c, period)
        val rr = mod(r, period)
        val idx = when (pat) {
            5 -> { // checker: two states
                if ((cc + rr) % 2 == 0) 0 else 1
            }
            4 -> { // diagonal stripes
                mod(cc + rr, period)
            }
            else -> { // ordered grid
                rr * period + cc
            }
        }
        val total = if (pat == 5) 2 else if (pat == 4) period else period * period
        // threshold: turn off approximately pct% of cells in the repeating unit
        val threshold = (pct * total + 50) / 100
        // distribute selected indices evenly
        val rank = rankIndex(idx, total, pat)
        return rank < threshold
    }

    private fun rankIndex(idx: Int, total: Int, pat: Int): Int {
        // simple permutation for better visual distribution
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
}
