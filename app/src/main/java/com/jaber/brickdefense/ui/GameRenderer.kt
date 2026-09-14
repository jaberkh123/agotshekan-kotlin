package com.jaber.brickdefense.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.jaber.brickdefense.game.Assets
import com.jaber.brickdefense.game.Cfg
import com.jaber.brickdefense.game.Enemy
import com.jaber.brickdefense.game.Fx
import com.jaber.brickdefense.game.GameEngine
import com.jaber.brickdefense.game.RectD
import com.jaber.brickdefense.game.clamp
import com.jaber.brickdefense.game.faNum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * پورت کامل رسم بازی از game.js (متدهای draw) — همان ترتیب و همان ظاهر.
 * همه‌ی مختصات «منطقی» است؛ GameView قبل از فراخوانی canvas را به dpr مقیاس می‌دهد.
 */
class GameRenderer(private val g: GameEngine) {

    // ---------- پینت‌های مشترک ----------
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pBmp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rectF = RectF()

    // ---------- کش پس‌زمینه ----------
    private var bgGrad: Shader? = null
    private var dzGrad: Shader? = null
    private var stars = mutableListOf<FloatArray>() // x, y, r, ph
    private var builtW = -1.0
    private var builtH = -1.0

    private fun maybeRebuildBackground() {
        val w = g.W
        val h = g.H
        if (w <= 0 || h <= 0) return
        if (w == builtW && h == builtH) return
        builtW = w
        builtH = h
        bgGrad = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(parse("#0c0c16"), parse("#14121f"), parse("#1d1428")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        val ts = g.ts
        dzGrad = LinearGradient(
            0f, (g.oy + (Cfg.GRID_ROWS - 2.6) * ts).toFloat(), 0f, (g.oy + Cfg.GRID_ROWS * ts).toFloat(),
            intArrayOf(parse("rgba(239,83,80,0)"), parse("rgba(239,83,80,0.22)")),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        stars.clear()
        val n = max(18, (w * h / 12000).toInt())
        for (i in 0 until n) {
            stars.add(
                floatArrayOf(
                    (Random.nextDouble() * w).toFloat(),
                    (Random.nextDouble() * h).toFloat(),
                    (Random.nextDouble() * 1.3 + 0.4).toFloat(),
                    (Random.nextDouble() * PI * 2).toFloat()
                )
            )
        }
    }

    // ---------- ابزارها ----------

    private fun parse(s: String): Int {
        return try {
            if (s.startsWith("rgba")) {
                val parts = s.substring(5, s.length - 1).split(",")
                Color.argb(
                    (parts[3].trim().toFloat() * 255).roundToInt(),
                    parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt()
                )
            } else {
                Color.parseColor(s)
            }
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    /** پورت rr() — مستطیل گرد */
    private fun rr(canvas: Canvas, x: Double, y: Double, w: Double, h: Double, rIn: Double) {
        var r = min(rIn, min(w / 2, h / 2))
        if (r < 0) r = 0.0
        rectF.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        path.reset()
        path.addRoundRect(rectF, r.toFloat(), r.toFloat(), Path.Direction.CW)
    }

    /** متن وسط‌چین افقی و عمودی (textBaseline=middle) */
    private fun drawTextC(canvas: Canvas, text: String, x: Double, y: Double, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = (y - (fm.ascent + fm.descent) / 2).toFloat()
        canvas.drawText(text, x.toFloat(), baseline, paint)
    }

    private fun setFill(c: String): Paint {
        pFill.color = parse(c)
        pFill.shader = null
        return pFill
    }

    // ---------- رسم اصلی ----------
    fun draw(canvas: Canvas) {
        maybeRebuildBackground()
        val W = g.W
        val H = g.H
        val ox = g.ox
        val oy = g.oy
        val ts = g.ts

        // پس‌زمینه کهکشانی
        pFill.style = Paint.Style.FILL
        if (bgGrad != null) {
            pFill.shader = bgGrad
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), pFill)
            pFill.shader = null
        } else {
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), setFill("#12121a"))
        }
        if (stars.isNotEmpty()) {
            for (s in stars) {
                pFill.color = parse("#cfd8ff")
                pFill.alpha = (255 * (0.25 + 0.25 * sin(g.time * 2 + s[3]))).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(s[0], s[1], s[2], pFill)
            }
            pFill.alpha = 255
        }

        // زمین: قاب درخشان
        val fw = Cfg.GRID_COLS * ts
        val fh = Cfg.GRID_ROWS * ts
        pFill.color = parse("rgba(18,18,30,0.92)")
        rr(canvas, ox - 3, oy - 3, fw + 6, fh + 6, ts * 0.16)
        canvas.drawPath(path, pFill)
        pStroke.color = parse("rgba(130,140,220,0.35)")
        pStroke.strokeWidth = 1.5f
        rr(canvas, ox - 3, oy - 3, fw + 6, fh + 6, ts * 0.16)
        canvas.drawPath(path, pStroke)

        // خطوط گرید
        pStroke.color = parse("rgba(255,255,255,0.05)")
        pStroke.strokeWidth = 1f
        pStroke.pathEffect = null
        path.reset()
        for (c in 1 until Cfg.GRID_COLS) {
            val x = (ox + c * ts).roundToInt() + 0.5
            path.moveTo(x.toFloat(), oy.toFloat())
            path.lineTo(x.toFloat(), (oy + fh).toFloat())
        }
        for (r in 1 until Cfg.GRID_ROWS) {
            val y = (oy + r * ts).roundToInt() + 0.5
            path.moveTo(ox.toFloat(), y.toFloat())
            path.lineTo((ox + fw).toFloat(), y.toFloat())
        }
        canvas.drawPath(path, pStroke)

        // منطقه خطر (دو ردیف آخر) با پالس
        if (dzGrad != null) {
            pFill.shader = dzGrad
            pFill.alpha = (255 * (0.75 + 0.25 * sin(g.time * 3))).roundToInt().coerceIn(0, 255)
            canvas.drawRect(
                ox.toFloat(), (oy + (Cfg.GRID_ROWS - 2.6) * ts).toFloat(),
                (ox + fw).toFloat(), (oy + Cfg.GRID_ROWS * ts).toFloat(), pFill
            )
            pFill.alpha = 255
            pFill.shader = null
        }

        // خط دفاعی (پالس قرمز)
        val dy = g.defenseY
        pStroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf((ts * 0.3).toFloat(), (ts * 0.2).toFloat()), 0f)
        pStroke.color = parse(if (sin(g.time * 4) > 0) "#ef5350" else "#ff8a80")
        pStroke.strokeWidth = max(2.0, ts * 0.06).toFloat()
        canvas.drawLine(ox.toFloat(), dy.toFloat(), (ox + fw).toFloat(), dy.toFloat(), pStroke)
        pStroke.pathEffect = null

        // دشمنان: اول خانه‌های عادی، بعد دژخیم
        val chainAlarmSet = g.electricChainAlarm()
        for (e in g.enemies) {
            if (!e.def.boss) drawEnemy(canvas, e, chainAlarmSet)
        }
        for (e in g.enemies) {
            if (e.def.boss) drawEnemy(canvas, e, chainAlarmSet)
        }

        // نوار جان دژخیم (بالای زمین)
        val boss = g.enemies.firstOrNull { it.def.boss }
        if (boss != null) {
            val bw = fw * 0.8
            val bh = max(6.0, ts * 0.22)
            val bx = ox + (fw - bw) / 2
            val by = oy + ts * 0.14
            val ratio = clamp(boss.hp.toDouble() / boss.maxHp, 0.0, 1.0)
            pFill.color = parse("rgba(0,0,0,0.6)")
            rr(canvas, bx - 2, by - 2, bw + 4, bh + 4, (bh + 4) / 2)
            canvas.drawPath(path, pFill)
            pStroke.color = parse("rgba(225,190,231,0.8)")
            pStroke.strokeWidth = 1.5f
            rr(canvas, bx - 2, by - 2, bw + 4, bh + 4, (bh + 4) / 2)
            canvas.drawPath(path, pStroke)
            if (ratio > 0) {
                val grd = LinearGradient(
                    bx.toFloat(), 0f, (bx + bw).toFloat(), 0f,
                    parse("#c158dc"), parse("#7b1fa2"), Shader.TileMode.CLAMP
                )
                pFill.shader = grd
                if (boss.shield) pFill.color = parse("#80d8ff")
                rr(canvas, bx, by, bw * ratio, bh, bh / 2)
                canvas.drawPath(path, pFill)
                pFill.shader = null
            }
            pText.color = Color.WHITE
            pText.textSize = (bh * 0.72).toFloat()
            drawTextC(
                canvas,
                "👑 " + faNum(max(0, kotlin.math.ceil(boss.hp.toDouble()).toInt())) + if (boss.shield) " 🛡" else "",
                bx + bw / 2, by + bh / 2, pText
            )
        }

        // خطوط پیش‌نمایش مسیر همه توپ‌ها (فقط در حالت نشانه‌گیری)
        if (g.state == "aim" && com.jaber.brickdefense.game.Prefs.showTrajectory) {
            g.ensureSim()
            drawTrajectories(canvas)
        }

        drawEffects(canvas)

        // توپ‌ها با رد حرکت
        for (b in g.activeBalls) {
            if (b.delay > 0) continue // هنوز شلیک نشده
            val col = if (b.sproutColor) Cfg.BALLS["normal"]!!.sproutColor else Cfg.BALLS[b.type]!!.color
            // برقی: پرتو الکتریکی واقعی
            if (b.type == "electric") {
                drawElectricBolt(canvas, b)
                continue
            }
            // رد (trail)
            for (i in b.trail.indices) {
                val t = b.trail[i]
                val k = (i + 1).toDouble() / b.trail.size
                pFill.color = parse(col)
                pFill.alpha = (255 * k * 0.35).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(t[0], t[1], (b.radius * 0.75 * k).toFloat(), pFill)
            }
            pFill.alpha = 255
            // هاله برای انفجاری
            if (b.type == "explosive") {
                val gr = RadialGradient(
                    b.x.toFloat(), b.y.toFloat(), (b.radius * 2.6).toFloat(),
                    intArrayOf(parse("rgba(255,152,0,0.55)"), parse("rgba(255,152,0,0)")),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                pFill.shader = gr
                canvas.drawCircle(b.x.toFloat(), b.y.toFloat(), (b.radius * 2.6).toFloat(), pFill)
                pFill.shader = null
            }
            // هاله سبز برای گلوله جوانه‌زده
            if (b.sproutColor) {
                val gr = RadialGradient(
                    b.x.toFloat(), b.y.toFloat(), (b.radius * 2.2).toFloat(),
                    intArrayOf(parse("rgba(105,240,174,0.6)"), parse("rgba(105,240,174,0)")),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                pFill.shader = gr
                canvas.drawCircle(b.x.toFloat(), b.y.toFloat(), (b.radius * 2.2).toFloat(), pFill)
                pFill.shader = null
            }
            pFill.color = parse(col)
            pFill.alpha = if (b.type == "ghost") (255 * 0.65).roundToInt() else 255
            canvas.drawCircle(b.x.toFloat(), b.y.toFloat(), b.radius.toFloat(), pFill)
            pFill.alpha = 255
            pStroke.color = parse("rgba(255,255,255,0.7)")
            pStroke.strokeWidth = 1.5f
            canvas.drawCircle(b.x.toFloat(), b.y.toFloat(), b.radius.toFloat(), pStroke)
            // تصویر گلوله روی دایره پایه
            val imgKey = when (b.type) {
                "normal" -> "tir_usual"
                "explosive" -> "tir_bomb"
                "ghost" -> "tir_ruh"
                else -> null
            }
            if (imgKey != null && Assets.has(imgKey)) {
                val s = b.radius * 3.4
                rectF.set(
                    (b.x - s / 2).toFloat(), (b.y - s / 2).toFloat(),
                    (b.x + s / 2).toFloat(), (b.y + s / 2).toFloat()
                )
                canvas.drawBitmap(Assets.get(imgKey)!!, null, rectF, pBmp)
            }
        }

        // توپ‌خانه‌ها؛ بعدشان شمارنده بزرگ
        drawCannons(canvas)
        // شمارنده بزرگ محو شدن توپ معمولی
        drawNormalTimer(canvas)
    }

    private fun drawNormalTimer(canvas: Canvas) {
        g.collectBtnRect = null
        val ts = g.ts
        val target = if (g.normalTimerVisible) 1.0 else 0.0
        g.normalTimerAlpha += (target - g.normalTimerAlpha) * 0.18
        if (g.normalTimerAlpha < 0.02) return
        if (g.normalTimerVisible && g.normalBallTimer != null) {
            g.normalTimerShown = max(1, kotlin.math.ceil(g.normalBallTimer!!).toInt())
        }
        val t = g.normalTimerShown
        val cx = g.W / 2
        val cy = g.defenseY - ts * 0.85 // بالاتر از خط دفاع، جدا از توپ‌خانه‌ها
        val low = t <= 5 // ۵ ثانیه آخر: قرمز و تپنده
        val pulse = if (low) 1 + 0.07 * sin(g.time * 12) else 1.0
        val bw = ts * 2.5 * pulse
        val bh = ts * 0.92 * pulse
        val a = g.normalTimerAlpha
        pFill.style = Paint.Style.FILL
        pFill.color = parse("rgba(8,10,20,0.85)")
        pFill.alpha = (255 * a).roundToInt().coerceIn(0, 255)
        rr(canvas, cx - bw / 2, cy - bh / 2, bw, bh, bh / 2)
        canvas.drawPath(path, pFill)
        // حاشیه درخشان (شبیه‌سازی shadow با لایه اضافه)
        pStroke.color = parse(if (low) "#ff1744" else Cfg.BALLS["normal"]!!.color)
        pStroke.strokeWidth = 2.5f
        pStroke.alpha = (255 * a * 0.35).roundToInt().coerceIn(0, 255)
        pStroke.strokeWidth = 6f
        rr(canvas, cx - bw / 2, cy - bh / 2, bw, bh, bh / 2)
        canvas.drawPath(path, pStroke)
        pStroke.strokeWidth = 2.5f
        pStroke.alpha = (255 * a).roundToInt().coerceIn(0, 255)
        rr(canvas, cx - bw / 2, cy - bh / 2, bw, bh, bh / 2)
        canvas.drawPath(path, pStroke)
        pFill.alpha = 255
        // عدد بزرگ ثانیه‌ها
        pFill.color = parse(if (low) "#ff5252" else "#ffffff")
        pFill.alpha = (255 * a).roundToInt().coerceIn(0, 255)
        pText.color = pFill.color
        pText.alpha = pFill.alpha
        pText.textSize = (ts * 0.5).toFloat()
        drawTextC(canvas, "⏱ " + faNum(t), cx, cy + ts * 0.02, pText)
        pFill.alpha = 255
        pText.alpha = 255

        // دکمه «جمع کردن» (کنار شمارنده)
        if (g.normalTimerVisible && g.normalBallTimer != null) {
            val bwid = ts * 2.15
            val bhei = bh
            val bx = cx - bw / 2 - ts * 0.25 - bwid // سمت چپ شمارنده
            val by = cy - bhei / 2
            val hover = g.collectBtnHot
            pFill.color = parse("rgba(8,20,14,0.88)")
            pFill.alpha = (255 * a).roundToInt().coerceIn(0, 255)
            rr(canvas, bx, by, bwid, bhei, bhei / 2)
            canvas.drawPath(path, pFill)
            pStroke.color = parse(if (hover) "#69f0ae" else "rgba(105,240,174,0.75)")
            pStroke.strokeWidth = (if (hover) 5f else 3.5f)
            rr(canvas, bx, by, bwid, bhei, bhei / 2)
            canvas.drawPath(path, pStroke)
            pText.color = parse(if (hover) "#b9f6ca" else "#69f0ae")
            pText.textSize = (ts * 0.34).toFloat()
            drawTextC(canvas, "🧹 جمع کردن", bx + bwid / 2, cy + ts * 0.02, pText)
            pFill.alpha = 255
            // مستطیل کلیک (بدون پالس برای ثبات لمس)
            g.collectBtnRect = RectD(
                cx - bw / 2 - ts * 0.25 - ts * 2.15,
                cy - bh / 2 - ts * 0.18,
                ts * 2.15,
                bh + ts * 0.36
            )
        }
    }

    // حالت آسیب: ۱ سالم، ۲ کمی کتک خورده، ۳ آسیب جدی
    private fun dmgState(e: Enemy): Int {
        val r = e.hp.toDouble() / e.maxHp
        return if (r > 0.66) 1 else if (r > 0.33) 2 else 3
    }

    // نام تصویر پوسته این دشمن (یا null)
    private fun spriteName(e: Enemy): String? {
        if (e.def.boss) return if (e.skin.isNotEmpty()) e.skin else null
        when (e.type) {
            "stone" -> return "ston" + dmgState(e)
            "jelly" -> return "jele" + dmgState(e)
            "iron" -> return "iron" + dmgState(e)
            "normal" -> return "agor_palce" + dmgState(e)
            "hole" -> return "bos2"
        }
        return null
    }

    private fun drawImg(canvas: Canvas, name: String, x: Double, y: Double, w: Double, h: Double) {
        val bmp = Assets.get(name) ?: return
        rectF.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        canvas.drawBitmap(bmp, null, rectF, pBmp)
    }

    private fun drawEnemy(canvas: Canvas, e: Enemy, chainAlarmSet: Set<Enemy>?) {
        val ts = g.ts
        val c = g.enemyRect(e)
        val inset = max(1.5, ts * 0.05)
        var x = c.x + inset
        var y = c.y + inset
        var w = c.w - inset * 2
        var h = c.h - inset * 2
        val d = e.def

        // سیاهچاله: خانه‌ی چرخان
        if (d.hole) {
            val cx = c.x + c.w / 2
            val cy = c.y + c.h / 2
            val aura = RadialGradient(
                cx.toFloat(), cy.toFloat(), (w * 0.95).toFloat(),
                intArrayOf(parse("rgba(90,0,140,0.5)"), parse("rgba(90,0,140,0)")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            pFill.style = Paint.Style.FILL
            pFill.shader = aura
            canvas.drawRect(
                (x - w * 0.5).toFloat(), (y - h * 0.5).toFloat(),
                (x + w * 1.5).toFloat(), (y + h * 1.5).toFloat(), pFill
            )
            pFill.shader = null
            canvas.save()
            canvas.translate(cx.toFloat(), cy.toFloat())
            canvas.rotate(Math.toDegrees(e.rot).toFloat())
            val sprHole = spriteName(e)
            if (sprHole != null && Assets.has(sprHole)) {
                val s = min(c.w, c.h) * 1.18
                drawImg(canvas, sprHole, -s / 2, -s / 2, s, s)
            } else {
                // گرداب برداری
                pFill.color = parse("#160420")
                canvas.drawCircle(0f, 0f, (w * 0.44).toFloat(), pFill)
                pStroke.color = parse("#ab47bc")
                pStroke.strokeWidth = max(1.5, ts * 0.05).toFloat()
                for (arm in 0 until 3) {
                    path.reset()
                    for (k in 0..12) {
                        val a = (arm / 3.0) * PI * 2 + k * 0.42
                        val rr2 = w * 0.07 + (k / 12.0) * w * 0.36
                        val px = cos(a) * rr2
                        val py = sin(a) * rr2
                        if (k == 0) path.moveTo(px.toFloat(), py.toFloat())
                        else path.lineTo(px.toFloat(), py.toFloat())
                    }
                    canvas.drawPath(path, pStroke)
                }
            }
            canvas.restore()
            if (e.hitFlash > 0) {
                pFill.color = Color.WHITE
                pFill.alpha = (e.hitFlash * 0.65 * 255).roundToInt().coerceIn(0, 255)
                rr(canvas, x, y, w, h, ts * 0.22)
                canvas.drawPath(path, pFill)
                pFill.alpha = 255
            }
        } else if (d.boss) {
            val cx = x + w / 2
            val cy = y + h / 2
            // هاله تهدید پشت بدنه
            val aura = RadialGradient(
                cx.toFloat(), cy.toFloat(), (w * 0.95).toFloat(),
                intArrayOf(parse("rgba(156,39,176,0.35)"), parse("rgba(156,39,176,0)")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            pFill.shader = aura
            canvas.drawRect(
                (x - w * 0.5).toFloat(), (y - h * 0.5).toFloat(),
                (x + w * 1.5).toFloat(), (y + h * 1.5).toFloat(), pFill
            )
            pFill.shader = null
            // بدنه: پوسته تصویری رندوم دژخیم یا برداری
            val sprB = spriteName(e)
            if (sprB != null && Assets.has(sprB)) {
                drawImg(canvas, sprB, c.x, c.y, c.w, c.h)
                if (e.hitFlash > 0) {
                    pFill.color = Color.WHITE
                    pFill.alpha = (e.hitFlash * 0.65 * 255).roundToInt().coerceIn(0, 255)
                    rr(canvas, x, y, w, h, ts * 0.22)
                    canvas.drawPath(path, pFill)
                    pFill.alpha = 255
                }
            } else {
                pFill.color = parse(if (e.hitFlash > 0) "#ffffff" else d.color)
                rr(canvas, x, y, w, h, ts * 0.22)
                canvas.drawPath(path, pFill)
                pFill.color = parse("rgba(0,0,0,0.35)")
                rr(canvas, x, y + h * 0.68, w, h * 0.32, ts * 0.22)
                canvas.drawPath(path, pFill)
                // خارهای اطراف
                pFill.color = parse("#4a148c")
                val spikes = 8
                for (i in 0 until spikes) {
                    val a = (i.toDouble() / spikes) * PI * 2 + g.time * 0.6
                    val sx = cx + cos(a) * (w / 2 + ts * 0.1)
                    val sy = cy + sin(a) * (h / 2 + ts * 0.1)
                    path.reset()
                    path.moveTo(
                        (sx - cos(a) * ts * 0.16).toFloat(),
                        (sy - sin(a) * ts * 0.16).toFloat()
                    )
                    path.lineTo(
                        (sx - sin(a) * ts * 0.09 + cos(a) * ts * 0.02).toFloat(),
                        (sy + cos(a) * ts * 0.09 - sin(a) * ts * 0.02).toFloat()
                    )
                    path.lineTo(
                        (sx + sin(a) * ts * 0.09 + cos(a) * ts * 0.02).toFloat(),
                        (sy - cos(a) * ts * 0.09 - sin(a) * ts * 0.02).toFloat()
                    )
                    path.close()
                    canvas.drawPath(path, pFill)
                }
                // چشم تپنده
                val eyeR = ts * (0.18 + 0.03 * sin(g.time * 5))
                pFill.color = parse("#ff1744")
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), eyeR.toFloat(), pFill)
                pFill.color = Color.WHITE
                canvas.drawCircle(
                    (cx + eyeR * 0.25).toFloat(), (cy - eyeR * 0.25).toFloat(),
                    (eyeR * 0.3).toFloat(), pFill
                )
            }
            // حباب سپر (وقتی فعال است)
            if (e.shield) {
                pStroke.color = parse("#80d8ff")
                pStroke.strokeWidth = max(2.0, ts * 0.07).toFloat()
                pStroke.alpha = (255 * (0.55 + 0.35 * sin(g.time * 14))).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), (w * 0.62).toFloat(), pStroke)
                pStroke.alpha = 255
                pFill.color = parse("#80d8ff")
                pFill.alpha = (255 * 0.14).roundToInt()
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), (w * 0.62).toFloat(), pFill)
                pFill.alpha = 255
            }
        } else {
            drawNormalEnemy(canvas, e, x, y, w, h, d, inset)
        }

        // نوار HP (فقط وقتی آسیب دیده)
        if (e.hp < e.maxHp) {
            val bw = if (d.boss) w * 0.9 else w * 0.8
            val bh = max(2.5, ts * (if (d.boss) 0.09 else 0.08))
            val bx = x + (w - bw) / 2
            val by = y + h - bh - 1
            val ratio = clamp(e.hp.toDouble() / e.maxHp, 0.0, 1.0)
            pFill.color = parse("rgba(0,0,0,0.55)")
            rr(canvas, bx, by, bw, bh, bh / 2)
            canvas.drawPath(path, pFill)
            pFill.color = parse(if (ratio > 0.5) "#66bb6a" else if (ratio > 0.25) "#ffca28" else "#ef5350")
            if (ratio > 0) {
                rr(canvas, bx, by, bw * ratio, bh, bh / 2)
                canvas.drawPath(path, pFill)
            }
        }

        // نمایش HP
        if (!d.boss) {
            pText.color = Color.WHITE
            pText.textSize = (ts * 0.3).toFloat()
            drawTextC(
                canvas,
                faNum(max(0, kotlin.math.ceil(e.hp.toDouble()).toInt())),
                c.x + c.w / 2, c.y + c.h / 2 + ts * 0.02, pText
            )
        }

        // آلارم قرمز راهنما
        val alarm = g.alarmTarget()
        val inBigChain = chainAlarmSet != null && chainAlarmSet.contains(e)
        if ((alarm != null && e.type == alarm) || inBigChain) {
            val pulse = 0.38 + 0.3 * sin(g.time * 7) // چشمک مثل آلارم
            pStroke.color = parse("#ff1744")
            pStroke.strokeWidth = max(2.5, ts * 0.09).toFloat()
            pStroke.alpha = (255 * pulse).roundToInt().coerceIn(0, 255)
            rr(canvas, c.x + 1.5, c.y + 1.5, c.w - 3, c.h - 3, ts * 0.18)
            canvas.drawPath(path, pStroke)
            pFill.color = parse("#ff1744")
            pFill.alpha = (255 * pulse * 0.28).roundToInt().coerceIn(0, 255)
            rr(canvas, c.x + 1.5, c.y + 1.5, c.w - 3, c.h - 3, ts * 0.18)
            canvas.drawPath(path, pFill)
            pStroke.alpha = 255
            pFill.alpha = 255
        }

        // هاله‌ی نفرین روحی: قاب بنفش تپنده + نشان 👻
        if (e.cursed && !e.dead) {
            val cp = 0.45 + 0.3 * sin(g.time * 5 + e.col * 1.3)
            pStroke.color = parse("#b39ddb")
            pStroke.strokeWidth = max(2.5, ts * 0.08).toFloat()
            pStroke.alpha = (255 * cp).roundToInt().coerceIn(0, 255)
            rr(canvas, c.x + 2.5, c.y + 2.5, c.w - 5, c.h - 5, ts * 0.2)
            canvas.drawPath(path, pStroke)
            pStroke.alpha = 255
            pText.color = parse("#ffffff")
            pText.textSize = (ts * 0.26).toFloat()
            pText.typeface = Typeface.DEFAULT
            val fm = pText.fontMetrics
            canvas.drawText(
                "👻",
                (c.x + c.w - 3).toFloat(),
                (c.y + 3 - fm.ascent).toFloat(),
                pText
            )
            pText.typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun drawNormalEnemy(
        canvas: Canvas, e: Enemy, xIn: Double, yIn: Double, wIn: Double, hIn: Double,
        d: Cfg.EnemyDef, inset: Double
    ) {
        val ts = g.ts
        val c = g.enemyRect(e)
        var x = xIn; var y = yIn; var w = wIn; var h = hIn
        // لرزش ژله‌ای
        if (d.soft) {
            val wob = sin(g.time * 3 + e.col * 1.7) * 0.07
            val cy = y + h / 2
            h *= 1 + wob
            w *= 1 - wob
            y = cy - h / 2
        }
        val rad = ts * 0.12

        // پوسته تصویری: سنگی/ژله‌ای/آهنی/معمولی هر کدام ۳ حالت آسیب
        val spr = spriteName(e)
        if (spr != null && Assets.has(spr)) {
            drawImg(canvas, spr, c.x, c.y, c.w, c.h)
            if (e.hitFlash > 0) {
                pFill.color = Color.WHITE
                pFill.alpha = (e.hitFlash * 0.65 * 255).roundToInt().coerceIn(0, 255)
                rr(canvas, c.x, c.y, c.w, c.h, rad)
                canvas.drawPath(path, pFill)
                pFill.alpha = 255
            }
        } else {
            // بدنه با سه‌بعدی برجسته (bevel)
            pFill.color = parse(if (e.hitFlash > 0) "#ffffff" else d.color)
            rr(canvas, x, y, w, h, rad)
            canvas.drawPath(path, pFill)
            // سایه پایین
            pFill.color = parse("rgba(0,0,0,0.28)")
            rr(canvas, x, y + h * 0.62, w, h * 0.38, rad)
            canvas.drawPath(path, pFill)
            // برق بالا
            pFill.color = parse("rgba(255,255,255,0.22)")
            rr(canvas, x + w * 0.1, y + h * 0.07, w * 0.8, h * 0.28, rad * 0.7)
            canvas.drawPath(path, pFill)

            // فلش‌های آهنی (rivet)
            if (d.key == "iron" && e.hitFlash == 0.0) {
                pFill.color = parse("rgba(20,20,25,0.6)")
                val rv = max(1.2, ts * 0.05).toFloat()
                for (p in arrayOf(doubleArrayOf(0.16, 0.16), doubleArrayOf(0.84, 0.16), doubleArrayOf(0.16, 0.84), doubleArrayOf(0.84, 0.84))) {
                    canvas.drawCircle((x + w * p[0]).toFloat(), (y + h * p[1]).toFloat(), rv, pFill)
                }
            }

            // ترک سنگی
            if (d.key == "stone" && e.crackStage > 0 && e.hitFlash == 0.0) {
                pStroke.color = parse("rgba(30,30,35,0.7)")
                pStroke.strokeWidth = max(1.2, ts * 0.04).toFloat()
                path.reset()
                path.moveTo((x + w * 0.25).toFloat(), y.toFloat())
                path.lineTo((x + w * 0.4).toFloat(), (y + h * 0.45).toFloat())
                path.lineTo((x + w * 0.3).toFloat(), (y + h).toFloat())
                path.moveTo((x + w * 0.75).toFloat(), (y + h * 0.15).toFloat())
                path.lineTo((x + w * 0.62).toFloat(), (y + h * 0.55).toFloat())
                canvas.drawPath(path, pStroke)
            }

            // علامت درمانگر
            if (d.heal > 0 && e.hitFlash == 0.0) {
                pFill.color = parse("rgba(255,255,255,0.9)")
                val m = w * 0.16
                val mw = w * 0.14
                canvas.drawRect(
                    (x + w / 2 - mw / 2).toFloat(), (y + h * 0.3).toFloat(),
                    (x + w / 2 + mw / 2).toFloat(), (y + h * 0.7).toFloat(), pFill
                )
                canvas.drawRect(
                    (x + w * 0.3).toFloat(), (y + h / 2 - m / 2).toFloat(),
                    (x + w * 0.7).toFloat(), (y + h / 2 + m / 2).toFloat(), pFill
                )
            }

            // خط تقسیم شکافتی
            if (d.split && e.hitFlash == 0.0) {
                pStroke.color = parse("rgba(255,255,255,0.55)")
                pStroke.strokeWidth = max(1.2, ts * 0.045).toFloat()
                canvas.drawLine(
                    (x + w * 0.5 - w * 0.12).toFloat(), (y + h * 0.1).toFloat(),
                    (x + w * 0.5 + w * 0.12).toFloat(), (y + h * 0.9).toFloat(), pStroke
                )
            }

            // فلش برخورد
            if (e.hitFlash > 0) {
                pFill.color = parse(d.color)
                pFill.alpha = (255 * (1 - e.hitFlash * 0.75)).roundToInt().coerceIn(0, 255)
                rr(canvas, x, y, w, h, rad)
                canvas.drawPath(path, pFill)
                pFill.alpha = 255
            }
        }
    }

    /** پرتو الکتریکی: زیگزاگ چندلایه چشمک‌زن با هسته سفید و هاله آبی */
    private fun drawElectricBolt(canvas: Canvas, b: com.jaber.brickdefense.game.Ball) {
        val pts = ArrayList<FloatArray>(b.laserPath)
        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
        if (pts.size >= 2) {
            val bolt = { amp: Double, width: Double, color: String, alpha: Double ->
                pStroke.color = parse(color)
                pStroke.strokeWidth = width.toFloat()
                pStroke.strokeCap = Paint.Cap.ROUND
                pStroke.strokeJoin = Paint.Join.ROUND
                pStroke.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
                path.reset()
                for (i in 0 until pts.size - 1) {
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val dx = (p2[0] - p1[0]).toDouble()
                    val dy = (p2[1] - p1[1]).toDouble()
                    val len = hypot(dx, dy)
                    path.moveTo(p1[0], p1[1])
                    // نقاط میانی با انحراف رندوم در هر فریم → لرزش برقی واقعی
                    for (k in 1..2) {
                        val t = k / 3.0
                        val off = (Random.nextDouble() - 0.5) * amp
                        path.lineTo(
                            (p1[0] + dx * t - (dy / len) * off).toFloat(),
                            (p1[1] + dy * t + (dx / len) * off).toFloat()
                        )
                    }
                    path.lineTo(p2[0], p2[1])
                }
                canvas.drawPath(path, pStroke)
            }
            // هاله پهن آبی
            bolt(b.radius * 2.2, max(5.0, b.radius * 1.8), "#40c4ff", 0.35)
            // لایه میانی آبی روشن
            bolt(b.radius * 1.4, max(3.0, b.radius), "#82b1ff", 0.7)
            // هسته سفید نازک
            bolt(b.radius * 0.7, max(1.5, b.radius * 0.45), "#ffffff", 0.95)
        }
        // سر جرقه
        val gr = RadialGradient(
            b.x.toFloat(), b.y.toFloat(), (b.radius * 2.4).toFloat(),
            intArrayOf(
                parse("rgba(255,255,255,0.95)"),
                parse("rgba(130,177,255,0.7)"),
                parse("rgba(64,196,255,0)")
            ),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        pFill.shader = gr
        canvas.drawCircle(b.x.toFloat(), b.y.toFloat(), (b.radius * 2.4).toFloat(), pFill)
        pFill.shader = null
        // جرقه‌های پراکنده کوچک
        pStroke.color = parse("#b3e5fc")
        pStroke.strokeWidth = max(1.0, b.radius * 0.22).toFloat()
        pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.alpha = 255
        for (s in 0 until 4) {
            val a1 = Random.nextDouble() * PI * 2
            val len = b.radius * (1.3 + Random.nextDouble() * 1.4)
            canvas.drawLine(
                b.x.toFloat(), b.y.toFloat(),
                (b.x + cos(a1) * len + (Random.nextDouble() - 0.5) * b.radius).toFloat(),
                (b.y + sin(a1) * len + (Random.nextDouble() - 0.5) * b.radius).toFloat(),
                pStroke
            )
        }
    }

    private fun drawCannons(canvas: Canvas) {
        val ts = g.ts
        val TOP_IMG = mapOf(
            "normal" to "top_usual", "explosive" to "top_bomb",
            "ghost" to "top_ruh", "electric" to "top_laser"
        )
        for ((i, key) in Cfg.BALL_KEYS.withIndex()) {
            val cx = g.cannonX(i)
            val cy = g.cannonY
            val col = Cfg.BALLS[key]!!.color
            val a = g.aims[key]!!
            val isActive = g.activePairFor().contains(key)
            val topImg = TOP_IMG[key]
            val baseAlpha = if (isActive) 255 else (255 * 0.35).roundToInt()
            if (topImg != null && Assets.has(topImg)) {
                // تصویر توپ‌خانه: به سمت نشانه‌گیری می‌چرخد
                canvas.save()
                canvas.translate(cx.toFloat(), cy.toFloat())
                canvas.rotate(Math.toDegrees(a + PI / 2).toFloat())
                val s = ts * 1.35
                pBmp.alpha = baseAlpha // توپ‌های خارج از نوبت کمرنگ‌تر رسم شوند
                drawImg(canvas, topImg, -s / 2, -s / 2, s, s)
                pBmp.alpha = 255
                canvas.restore()
            } else {
                // لوله توپ به سمت جهت (برداری)
                canvas.save()
                canvas.translate(cx.toFloat(), cy.toFloat())
                canvas.rotate(Math.toDegrees(a).toFloat())
                pFill.color = parse("#08080c")
                pFill.alpha = baseAlpha
                rr(canvas, -ts * 0.05, -ts * 0.12, ts * 0.68, ts * 0.24, ts * 0.1)
                canvas.drawPath(path, pFill)
                pStroke.color = parse("rgba(255,255,255,0.18)")
                pStroke.strokeWidth = 1f
                pStroke.alpha = baseAlpha
                rr(canvas, -ts * 0.05, -ts * 0.12, ts * 0.68, ts * 0.24, ts * 0.1)
                canvas.drawPath(path, pStroke)
                pFill.color = parse(col)
                rr(canvas, ts * 0.36, -ts * 0.08, ts * 0.24, ts * 0.16, ts * 0.07)
                canvas.drawPath(path, pFill)
                canvas.restore()
            }

            val isFiring = g.state == "fire" && g.activeBalls.isNotEmpty() &&
                    g.activeBalls[0].type == key
            // پایه
            pFill.color = parse("#0d0d14")
            pFill.alpha = baseAlpha
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), (ts * 0.38).toFloat(), pFill)
            pFill.color = parse(col)
            if (!isActive) pFill.alpha = baseAlpha // مرکز پایه هم کمرنگ شود
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), (ts * 0.28).toFloat(), pFill)
            pStroke.color = parse(if (isFiring) "#ffffff" else "rgba(255,255,255,0.5)")
            pStroke.strokeWidth = (if (isFiring) 3.5 else 2.0).toFloat()
            if (!isActive) pStroke.alpha = (255 * 0.18).roundToInt()
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), (ts * 0.28).toFloat(), pStroke)
            if (isFiring) { // هاله دور توپی که نوبتش است
                pStroke.color = parse(col)
                pStroke.strokeWidth = 2f
                pStroke.alpha = (255 * 0.6).roundToInt()
                canvas.drawCircle(cx.toFloat(), cy.toFloat(), (ts * 0.5).toFloat(), pStroke)
                pStroke.alpha = 255
            }
            pFill.alpha = 255

            // نشانه‌گیری فعال: فلش جهت
            if (isActive && g.state == "aim" && key == g.aimKey) {
                drawAimArrow(canvas, cx, cy, a, col, ts)
            }
            // نشان روح‌های ذخیره‌شده روی توپ روحی
            if (key == "ghost" && g.ghostStock > 0) {
                pText.color = Color.WHITE
                if (!isActive) pText.alpha = baseAlpha
                pText.textSize = (ts * 0.22).toFloat()
                drawTextC(canvas, "👻" + faNum(g.ghostStock), cx, cy - ts * 0.62, pText)
                pText.alpha = 255
            }
            // شماره ترتیب حمله (فقط توپ‌های فعال این ترن)
            if (isActive) {
                val activePair = g.activePairFor()
                pText.color = Color.WHITE
                pText.textSize = (ts * 0.26).toFloat()
                drawTextC(canvas, faNum(activePair.indexOf(key) + 1), cx, cy + ts * 0.62, pText)
            }
        }
    }

    private fun drawAimArrow(canvas: Canvas, cx: Double, cy: Double, a: Double, col: String, ts: Double) {
        val len = ts * 1.5
        val ex = cx + cos(a) * len
        val ey = cy + sin(a) * len
        pStroke.color = parse(col)
        pStroke.strokeWidth = (ts * 0.09).toFloat()
        pStroke.strokeCap = Paint.Cap.ROUND
        pStroke.alpha = (255 * 0.55).roundToInt()
        canvas.drawLine(cx.toFloat(), cy.toFloat(), ex.toFloat(), ey.toFloat(), pStroke)
        pStroke.alpha = 255
        val ang = kotlin.math.atan2(ey - cy, ex - cx)
        pFill.color = parse(col)
        path.reset()
        path.moveTo(ex.toFloat(), ey.toFloat())
        path.lineTo((ex - cos(ang - 0.4) * ts * 0.28).toFloat(), (ey - sin(ang - 0.4) * ts * 0.28).toFloat())
        path.lineTo((ex - cos(ang + 0.4) * ts * 0.28).toFloat(), (ey - sin(ang + 0.4) * ts * 0.28).toFloat())
        path.close()
        canvas.drawPath(path, pFill)
    }

    private fun drawEffects(canvas: Canvas) {
        val ts = g.ts
        for (fx in g.effects) {
            val p = fx.t / fx.dur
            when (fx.kind) {
                "explosion" -> {
                    pStroke.color = parse("#ffb74d")
                    pStroke.strokeWidth = 3f
                    pStroke.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    canvas.drawCircle(fx.x.toFloat(), fx.y.toFloat(), (fx.r * (0.4 + 0.6 * p)).toFloat(), pStroke)
                    pFill.color = parse("rgba(255,152,0,0.25)")
                    canvas.drawCircle(fx.x.toFloat(), fx.y.toFloat(), (fx.r * (0.4 + 0.6 * p)).toFloat(), pFill)
                    pStroke.alpha = 255
                }
                "boom" -> {
                    pFill.color = parse(fx.color)
                    pFill.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    canvas.drawCircle(fx.x.toFloat(), fx.y.toFloat(), (ts * 0.45 * (1 - p)).toFloat(), pFill)
                    pFill.alpha = 255
                }
                "gold", "dmg" -> {
                    pText.color = parse(fx.color)
                    pText.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    pText.textSize = (ts * 0.32).toFloat()
                    drawTextC(canvas, fx.text, fx.x, fx.y - p * ts * 0.9, pText)
                    pText.alpha = 255
                }
                "hitline" -> {
                    pFill.color = parse("#ef5350")
                    pFill.alpha = (255 * 0.5 * (1 - p)).roundToInt().coerceIn(0, 255)
                    canvas.drawRect(
                        g.ox.toFloat(), (g.defenseY - ts * 0.5).toFloat(),
                        (g.ox + Cfg.GRID_COLS * ts).toFloat(), (g.defenseY + ts * 0.5).toFloat(), pFill
                    )
                    pFill.alpha = 255
                }
                "heal" -> {
                    pFill.color = parse("#66bb6a")
                    pText.color = pFill.color
                    pText.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    pText.textSize = (ts * 0.42).toFloat()
                    drawTextC(canvas, "✚", fx.x, fx.y - p * ts * 0.8, pText)
                    pText.alpha = 255
                }
                "combo" -> {
                    val scale = 1 + 0.6 * max(0.0, 1 - p * 3.5)
                    pText.color = parse("#ffd54f")
                    pText.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    pText.textSize = (ts * 0.42 * scale).toFloat()
                    drawTextC(canvas, fx.text, fx.x, fx.y - p * ts * 0.5, pText)
                    pText.alpha = 255
                }
                "stageclear" -> {
                    val ap = if (p < 0.2) p / 0.2 else 1 - max(0.0, (p - 0.5) / 0.5)
                    pText.color = parse("#69f0ae")
                    pText.alpha = (255 * ap).roundToInt().coerceIn(0, 255)
                    pText.textSize = (ts * 0.62).toFloat()
                    drawTextC(
                        canvas, fx.text,
                        g.ox + Cfg.GRID_COLS * ts / 2, g.oy + ts * 3.2, pText
                    )
                    pText.alpha = 255
                }
                "bosswarn", "bossdown", "fla" -> {
                    val ap = if (p < 0.15) p / 0.15 else 1 - max(0.0, (p - 0.6) / 0.4)
                    pText.color = parse(
                        when (fx.kind) {
                            "bosswarn" -> "#ff5252"
                            "fla" -> "#40c4ff"
                            else -> "#e1bee7"
                        }
                    )
                    pText.alpha = (255 * ap).roundToInt().coerceIn(0, 255)
                    val pulse = 1 + 0.06 * sin(p * PI * 6)
                    pText.textSize = (ts * 0.55 * pulse).toFloat()
                    drawTextC(
                        canvas, fx.text,
                        g.ox + Cfg.GRID_COLS * ts / 2, g.oy + ts * 4.2, pText
                    )
                    pText.alpha = 255
                }
                "shield" -> {
                    pStroke.color = parse("#80d8ff")
                    pStroke.strokeWidth = 3f
                    pStroke.alpha = (255 * (1 - p) * 0.9).roundToInt().coerceIn(0, 255)
                    canvas.drawCircle(
                        fx.x.toFloat(), fx.y.toFloat(),
                        (ts * (0.8 + p * 0.5)).toFloat(), pStroke
                    )
                    pStroke.alpha = 255
                }
                "laserfade" -> {
                    pStroke.color = parse(fx.color)
                    pStroke.strokeCap = Paint.Cap.ROUND
                    pStroke.strokeWidth = max(2.0, ts * 0.06 * (1 - p)).toFloat()
                    pStroke.alpha = (255 * (1 - p) * 0.9).roundToInt().coerceIn(0, 255)
                    path.reset()
                    for ((i, q) in fx.pts.withIndex()) {
                        if (i == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1])
                    }
                    canvas.drawPath(path, pStroke)
                    pStroke.alpha = 255
                }
                "laserhit" -> {
                    pStroke.color = parse(fx.color)
                    pStroke.strokeWidth = 2.5f
                    pStroke.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    canvas.drawCircle(
                        fx.x.toFloat(), fx.y.toFloat(),
                        (ts * 0.1 + ts * 0.25 * p).toFloat(), pStroke
                    )
                    pStroke.alpha = 255
                }
                "zap" -> {
                    // جرقه برق: خط زیگزاگ بین دو نقطه
                    pStroke.color = parse(fx.color)
                    pStroke.strokeCap = Paint.Cap.ROUND
                    pStroke.strokeWidth = max(1.5, ts * 0.05 * fx.thick).toFloat()
                    pStroke.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    val dx = fx.x2 - fx.x1
                    val dyv = fx.y2 - fx.y1
                    val len = hypot(dx, dyv)
                    val nx = -dyv / len
                    val ny = dx / len
                    val jag = fx.jag
                    path.reset()
                    path.moveTo(fx.x1.toFloat(), fx.y1.toFloat())
                    for (k in 1..3) {
                        val t = k / 4.0
                        val off = jag[(k - 1) % jag.size] * len * 0.22
                        path.lineTo(
                            (fx.x1 + dx * t + nx * off).toFloat(),
                            (fx.y1 + dyv * t + ny * off).toFloat()
                        )
                    }
                    path.lineTo(fx.x2.toFloat(), fx.y2.toFloat())
                    canvas.drawPath(path, pStroke)
                    pStroke.alpha = 255
                }
                "vortex" -> {
                    // بلعیده‌شدن توپ توسط سیاهچاله
                    pStroke.color = parse("#ab47bc")
                    pStroke.strokeWidth = 2f
                    pStroke.alpha = (255 * (1 - p)).roundToInt().coerceIn(0, 255)
                    for (k in 0 until 3) {
                        val rvv = ts * 0.45 * (1 - p) * (1 - k * 0.25)
                        rectF.set(
                            (fx.x - rvv).toFloat(), (fx.y - rvv).toFloat(),
                            (fx.x + rvv).toFloat(), (fx.y + rvv).toFloat()
                        )
                        path.reset()
                        path.arcTo(
                            rectF,
                            Math.toDegrees(g.time * 3 + k * 2).toFloat(),
                            Math.toDegrees(PI * 1.3).toFloat()
                        )
                        canvas.drawPath(path, pStroke)
                    }
                    pStroke.alpha = 255
                }
            }
        }
    }

    private fun drawTrajectories(canvas: Canvas) {
        val ts = g.ts
        val activePair = g.activePairFor()
        for (key in Cfg.BALL_KEYS) {
            val sim = g.simFor(key) ?: continue
            if (!activePair.contains(key)) continue // فقط توپ‌های فعال این ترن
            val bright = key == g.aimKey
            val col = Cfg.BALLS[key]!!.color
            // برقی: پرتوی جرقه‌ای به‌جای خط‌چین + خانه‌های زنجیره‌ای
            if (key == "electric") {
                pStroke.color = parse(col)
                pStroke.strokeWidth = (if (bright) max(2.5, ts * 0.08) else max(1.2, ts * 0.04)).toFloat()
                pStroke.alpha = (255 * (if (bright) 0.9 else 0.25)).roundToInt()
                path.reset()
                for ((i, pt) in sim.pts.withIndex()) {
                    if (i == 0) path.moveTo(pt[0], pt[1]) else path.lineTo(pt[0], pt[1])
                }
                canvas.drawPath(path, pStroke)
                if (bright) {
                    pStroke.alpha = (255 * 0.35).roundToInt()
                    pStroke.color = parse("#e1f5fe")
                    pStroke.strokeWidth = (ts * 0.2).toFloat()
                    canvas.drawPath(path, pStroke)
                    pStroke.color = parse(col)
                    pStroke.strokeWidth = (max(2.5, ts * 0.08)).toFloat()
                    pStroke.alpha = 255
                    pFill.color = parse(col)
                    for (h in sim.hits) {
                        canvas.drawCircle(h[0], h[1], (ts * 0.09).toFloat(), pFill)
                    }
                    // خانه‌هایی که زنجیره برق به آن‌ها پرش می‌کند
                    if (sim.chains.isNotEmpty()) {
                        pStroke.color = parse("#80d8ff")
                        pStroke.strokeWidth = 2f
                        pStroke.alpha = (255 * 0.85).roundToInt()
                        for (pt in sim.chains) {
                            canvas.drawCircle(pt[0], pt[1], (ts * 0.13).toFloat(), pStroke)
                            val cd = (ts * 0.08).toFloat()
                            canvas.drawLine(
                                pt[0] - cd, pt[1], pt[0] + cd, pt[1], pStroke
                            )
                            canvas.drawLine(
                                pt[0], pt[1] - cd, pt[0], pt[1] + cd, pStroke
                            )
                        }
                        pStroke.alpha = 255
                    }
                }
                pStroke.alpha = 255
                continue
            }
            // خط‌چین مسیر — همه توپ‌ها کم‌رنگ، توپِ در حال تغییر جهت برجسته
            pStroke.color = parse(col)
            pStroke.strokeWidth = (if (bright) max(2.5, ts * 0.075) else max(1.2, ts * 0.04)).toFloat()
            pStroke.alpha = (255 * (if (bright) 0.95 else 0.22)).roundToInt()
            pStroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf((ts * 0.16).toFloat(), (ts * 0.14).toFloat()), 0f)
            path.reset()
            for ((i, pt) in sim.pts.withIndex()) {
                if (i == 0) path.moveTo(pt[0], pt[1]) else path.lineTo(pt[0], pt[1])
            }
            canvas.drawPath(path, pStroke)
            pStroke.pathEffect = null
            pStroke.alpha = 255
            if (bright) {
                // نقاط برخورد
                pFill.color = parse(col)
                for (h in sim.hits) {
                    canvas.drawCircle(h[0], h[1], (ts * 0.09).toFloat(), pFill)
                }
                // شعاع انفجار توپ انفجاری
                if (sim.explosion != null) {
                    pStroke.color = parse("#ffb74d")
                    pStroke.strokeWidth = 2f
                    pStroke.alpha = (255 * 0.5).roundToInt()
                    pStroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf((ts * 0.12).toFloat(), (ts * 0.1).toFloat()), 0f)
                    canvas.drawCircle(
                        sim.explosion[0], sim.explosion[1], sim.explosion[2], pStroke
                    )
                    pStroke.pathEffect = null
                    pStroke.alpha = 255
                }
            }
        }
    }

}
