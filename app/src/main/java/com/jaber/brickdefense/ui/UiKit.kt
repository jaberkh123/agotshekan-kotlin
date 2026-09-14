package com.jaber.brickdefense.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/** ابزارهای مشترک رسم UI — پورت ساده‌شده‌ی CSS نسخه اصلی با همان پالت رنگ */
object UiKit {

    // ---------- پالت (از style.css نسخه اصلی) ----------
    const val BG = "#12121a"
    const val PANEL = "#1a1a28"
    const val PANEL_BORDER = "#3a3a55"
    const val TXT = "#ffffff"
    const val TXT_DIM = "#8888a5"
    const val ACCENT = "#4fc3f7"
    const val GREEN = "#69f0ae"
    const val GOLD = "#ffd54f"
    const val RED = "#ef5350"

    val regions = ArrayList<Region>()

    class Region(val rect: RectF, val onClick: () -> Unit)

    /** همه‌ی ناحیه‌های کلیک‌پذیر فریم جاری را پاک می‌کند (ابتدای هر فریم) */
    fun beginFrame() = regions.clear()

    fun clearRegions() = regions.clear()

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rectF = RectF()

    // کش StaticLayout برای متن‌های چندخطی فارسی
    private val layoutCache = HashMap<Long, StaticLayout>()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    fun parse(s: String): Int {
        return try {
            if (s.startsWith("rgba")) {
                val parts = s.substring(5, s.length - 1).split(",")
                Color.argb(
                    (parts[3].trim().toFloat() * 255).toInt(),
                    parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt()
                )
            } else Color.parseColor(s)
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    fun rr(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float): Path {
        rectF.set(x, y, x + w, y + h)
        path.reset()
        path.addRoundRect(rectF, r, r, Path.Direction.CW)
        return path
    }

    fun fillRR(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        pFill.color = color
        canvas.drawPath(rr(canvas, x, y, w, h, r), pFill)
    }

    fun strokeRR(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, sw: Float) {
        pStroke.color = color
        pStroke.strokeWidth = sw
        canvas.drawPath(rr(canvas, x, y, w, h, r), pStroke)
    }

    fun gradRR(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, c1: String, c2: String) {
        pFill.shader = LinearGradient(x, y, x + w * 0.7f, y + h, parse(c1), parse(c2), Shader.TileMode.CLAMP)
        canvas.drawPath(rr(canvas, x, y, w, h, r), pFill)
        pFill.shader = null
    }

    /** متن تک‌خطی وسط‌چین */
    fun textC(
        canvas: Canvas, text: String, cx: Float, cy: Float, sizePx: Float,
        color: Int, bold: Boolean = true
    ) {
        pTxt.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        pTxt.textSize = sizePx
        pTxt.color = color
        val fm = pTxt.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, pTxt)
    }

    /** متن تک‌خطی راست‌چین (RTL) */
    fun textR(canvas: Canvas, text: String, rightX: Float, cy: Float, sizePx: Float, color: Int) {
        pTxt.typeface = Typeface.DEFAULT_BOLD
        pTxt.textSize = sizePx
        pTxt.color = color
        val w = pTxt.measureText(text)
        val fm = pTxt.fontMetrics
        canvas.drawText(text, rightX - w, cy - (fm.ascent + fm.descent) / 2f, pTxt)
    }

    /**
     * متن چندخطی فارسی با RTL + وسط‌چین داخل عرض داده‌شده؛ ارتفاع واقعی را برمی‌گرداند.
     */
    fun paragraph(
        canvas: Canvas, text: String, x: Float, y: Float, w: Float,
        sizePx: Float, color: Int, center: Boolean = true
    ): Float {
        val key = (text.hashCode().toLong() * 1000003) xor (w.toInt().toLong() * 31) xor sizePx.toBits().toLong()
        var sl = layoutCache[key]
        if (sl == null) {
            if (layoutCache.size > 120) layoutCache.clear()
            tp.textSize = sizePx
            tp.color = color
            val align = if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, w.toInt().coerceAtLeast(10))
                .setAlignment(align)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.25f)
                .build()
            layoutCache[key] = sl
        }
        canvas.save()
        canvas.translate(x, y)
        sl.draw(canvas)
        canvas.restore()
        return sl.height.toFloat()
    }

    fun paragraphHeight(text: String, w: Float, sizePx: Float): Float {
        tp.textSize = sizePx
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, w.toInt().coerceAtLeast(10))
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.25f)
            .build()
        return sl.height.toFloat()
    }

    // کش بلوک‌های ساختاریافته (هر خط = یک بلاک راست‌چین)
    private val blockCache = HashMap<Long, List<StaticLayout>>()

    /**
     * متن چندخطی «منظم»: هر خط (جدا شده با \n) یک بلاک مستقل راست‌چین است
     * با فاصله‌ی یکنواخت بین بلاک‌ها — خوانایی بهتر از پاراگراف وسط‌چین.
     * ارتفاع کل کشیده‌شده را برمی‌گرداند.
     */
    fun blockText(
        canvas: Canvas, text: String, x: Float, y: Float, w: Float,
        sizePx: Float, color: Int, gapPx: Float
    ): Float {
        val blocks = buildBlocks(text, w, sizePx, color, gapPx)
        var cy = y
        for (sl in blocks) {
            canvas.save()
            canvas.translate(x, cy)
            sl.draw(canvas)
            canvas.restore()
            cy += sl.height + gapPx
        }
        return cy - gapPx - y
    }

    /** ارتفاع blockText بدون رسم (برای محاسبه‌ی پنل) */
    fun blockTextHeight(text: String, w: Float, sizePx: Float, gapPx: Float): Float {
        val key = blockKey(text, w, sizePx, gapPx, 0)
        var total = 0f
        for (line in text.split("\n")) {
            if (total == 0f && line.isBlank()) continue
            tp.textSize = sizePx
            val sl = StaticLayout.Builder.obtain(line, 0, line.length, tp, w.toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.3f)
                .build()
            total += sl.height + gapPx
        }
        return (total - gapPx).coerceAtLeast(0f)
    }

    private fun blockKey(text: String, w: Float, sizePx: Float, gapPx: Float, color: Int): Long {
        return ((text.hashCode().toLong() * 1000003) xor (w.toInt().toLong() * 31) xor
                sizePx.toBits() * 7L xor gapPx.toBits() * 13L xor color.toLong())
    }

    private fun buildBlocks(text: String, w: Float, sizePx: Float, color: Int, gapPx: Float): List<StaticLayout> {
        val key = blockKey(text, w, sizePx, gapPx, color)
        blockCache[key]?.let { return it }
        if (blockCache.size > 80) blockCache.clear()
        val out = ArrayList<StaticLayout>()
        for (line in text.split("\n")) {
            tp.textSize = sizePx
            tp.color = color
            out.add(
                StaticLayout.Builder.obtain(line, 0, line.length, tp, w.toInt().coerceAtLeast(10))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setLineSpacing(0f, 1.3f)
                    .build()
            )
        }
        blockCache[key] = out
        return out
    }

    /**
     * دکمه منو با استایل نسخه اصلی.
     * kind: primary | ghost | danger | disabled
     * برمی‌گرداند: ارتفاع دکمه
     */
    fun button(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        main: String, sub: String?, kind: String,
        disabled: Boolean = false, onClick: () -> Unit
    ): Float {
        val k = if (disabled) "disabled" else kind
        val r = h * 0.24f
        when (k) {
            "primary" -> {
                gradRR(canvas, x, y, w, h, r, "#2196f3", "#0d47a1")
                fillRR(canvas, x, y + h - h * 0.12f, w, h * 0.12f, r * 0.6f, parse("#082f6b"))
                pTxt.color = Color.WHITE
            }
            "danger" -> {
                gradRR(canvas, x, y, w, h, r, "rgba(74,31,36,0.95)", "rgba(48,20,24,0.95)")
                strokeRR(canvas, x, y, w, h, r, parse("rgba(191,54,54,0.55)"), 1.5f)
                pTxt.color = parse("#ff8a80")
            }
            "ghost" -> {
                fillRR(canvas, x, y, w, h, r, parse("#23233a"))
                strokeRR(canvas, x, y, w, h, r, parse("#3a3a55"), 1.5f)
                pTxt.color = Color.WHITE
            }
            else -> { // disabled
                fillRR(canvas, x, y, w, h, r, parse("#20202f"))
                pTxt.color = parse("#55556e")
            }
        }
        val mainSize = h * 0.32f
        val subSize = h * 0.24f
        if (sub.isNullOrEmpty()) {
            textC(canvas, main, x + w / 2, y + h / 2, mainSize, pTxt.color)
        } else {
            textC(canvas, main, x + w / 2, y + h * 0.34f, mainSize, pTxt.color)
            textC(canvas, sub, x + w / 2, y + h * 0.68f, subSize, parse(if (k == "primary") "#cfe8ff" else "#8888a5"))
        }
        if (!disabled) regions.add(Region(RectF(x, y, x + w, y + h), onClick))
        return h
    }

    /** پنل مرکزی مودال‌ها */
    fun panel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        fillRR(canvas, x, y, w, h, 22f, parse(PANEL))
        strokeRR(canvas, x, y, w, h, 22f, parse(PANEL_BORDER), 1.5f)
    }
}
