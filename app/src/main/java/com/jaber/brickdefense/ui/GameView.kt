package com.jaber.brickdefense.ui

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.jaber.brickdefense.game.GameEngine

/**
 * ویو اختصاصی بازی: حلقه رندر با Choreographer + ورودی لمس.
 * جایگزین کامل WebView/Canvas HTML — همه‌چیز بومی است.
 */
class GameView(context: Context) : View(context) {

    val engine = GameEngine()
    val renderer = GameRenderer(engine)
    lateinit var ui: UiController

    private var dpr = 1f
    private var lastFrameNanos = 0L
    private var running = false
    private var initialized = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            val dt = min(0.05, (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0)
            lastFrameNanos = frameTimeNanos

            // به‌روزرسانی فقط وقتی منو باز نیست (مطابق main.js اصلی)
            if (!ui.menuOpen) engine.update(dt)
            engine.time // زمان برای انیمیشن‌ها در update جلو می‌رود

            invalidate()
        }
    }

    init {
        dpr = resources.displayMetrics.density
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setupUi(host: Host) {
        ui = UiController(engine, host)
        ui.layout(width / dpr, height / dpr, dpr)
        engine.layout(width / dpr.toDouble(), height / dpr.toDouble(), ui.hudH.toDouble(), ui.cardsH.toDouble())
        initialized = true
    }

    fun start() {
        if (!initialized) return
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (!initialized) {
            // اولین اندازه‌گیری قبل از setupUi
            return
        }
        ui.layout(w / dpr, h / dpr, dpr)
        engine.layout(w / dpr.toDouble(), h / dpr.toDouble(), ui.hudH.toDouble(), ui.cardsH.toDouble())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized) return
        canvas.save()
        canvas.scale(dpr, dpr)
        renderer.draw(canvas)
        ui.drawAll(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized) return false
        val x = event.x / dpr
        val y = event.y / dpr
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP -> 2
            MotionEvent.ACTION_CANCEL -> 3
            else -> return false
        }
        return ui.onTouch(action, x, y)
    }

    private fun min(a: Double, b: Double): Double = if (a < b) a else b
}
