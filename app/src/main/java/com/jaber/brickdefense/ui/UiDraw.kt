package com.jaber.brickdefense.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.jaber.brickdefense.game.Assets
import com.jaber.brickdefense.game.Cfg
import com.jaber.brickdefense.game.Prefs
import com.jaber.brickdefense.game.Sfx
import com.jaber.brickdefense.game.bossTurns
import com.jaber.brickdefense.game.faNum
import com.jaber.brickdefense.game.turnsPerStage
import com.jaber.brickdefense.ui.UiController.Modal
import com.jaber.brickdefense.ui.UiController.Screen
import com.jaber.brickdefense.ui.UiKit.beginFrame
import com.jaber.brickdefense.ui.UiKit.blockText
import com.jaber.brickdefense.ui.UiKit.blockTextHeight
import com.jaber.brickdefense.ui.UiKit.button
import com.jaber.brickdefense.ui.UiKit.fillRR
import com.jaber.brickdefense.ui.UiKit.panel
import com.jaber.brickdefense.ui.UiKit.paragraph
import com.jaber.brickdefense.ui.UiKit.paragraphHeight
import com.jaber.brickdefense.ui.UiKit.parse
import com.jaber.brickdefense.ui.UiKit.Region
import com.jaber.brickdefense.ui.UiKit.regions
import com.jaber.brickdefense.ui.UiKit.strokeRR
import com.jaber.brickdefense.ui.UiKit.textC
import com.jaber.brickdefense.ui.UiKit.textR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * رندر و لمس UI — بخش دوم UiController (توابع توسعه)
 * پورت draw/refresh بخش‌های ui.js
 */

// ============================================================
//                       رندر کل فریم
// ============================================================

fun UiController.drawAll(canvas: Canvas) {
    beginFrame()
    when (screen) {
        UiController.Screen.MENU -> drawMenuScreen(canvas)
        UiController.Screen.STAGES -> drawStagesScreen(canvas)
        UiController.Screen.SETTINGS -> drawSettingsScreen(canvas)
        UiController.Screen.GUIDE -> drawGuideScreen(canvas)
        UiController.Screen.GAME -> {
            drawGameHud(canvas)
            drawCards(canvas)
            drawStartOverlay(canvas)
            drawGameModals(canvas)
        }
    }
    drawToast(canvas)
    // پنجره تأیید خروج روی هر صفحه‌ای (منو/زیرصفحه‌ها/بازی) رسم می‌شود
    if (modal == Modal.EXIT) drawExitConfirm(canvas)
    // پنجره راهنمای خانه‌های جدید (اگر در صف است)
    flushHouseGuides()
}

// ============================================================
//                       HUD بازی
// ============================================================

private fun UiController.drawGameHud(canvas: Canvas) {
    val h = hudH
    // نوار بالا
    fillRR(canvas, 0f, 0f, W, h + 2f, 0f, parse("rgba(14,14,24,0.95)"))
    strokeRR(canvas, 0f, h + 1f, W, 1f, 0f, parse("#26263c"), 1f)

    val fs = 12.5f * dp
    val cy = h / 2
    val pad = 10f * dp
    var xRight = W - pad
    // مرحله / دور
    val stageLabel: String
    val turnLabel: String
    if (engine.mode == "endless") {
        stageLabel = "♾ دور " + faNum(engine.stage)
        turnLabel = "ترن " + faNum(engine.turnInStage) + "/" + faNum(Cfg.ENDLESS_BLOCK)
    } else {
        stageLabel = "مرحله " + faNum(engine.stage) + " از " + faNum(Cfg.MAX_STAGE)
        val capS = turnsPerStage(engine.stage)
        val extending = engine.turnInStage >= capS &&
                engine.enemies.any { it.def.boss && !it.dead }
        turnLabel = "ترن " + faNum(engine.turnInStage) + (if (extending) "+" else "") + "/" + faNum(capS)
    }
    textR(canvas, stageLabel, xRight, cy, fs, Color.WHITE); xRight -= 88f * dp
    textR(canvas, turnLabel, xRight, cy, fs, parse("#bbb")); xRight -= 78f * dp
    textR(canvas, "🪙 " + faNum(engine.gold), xRight, cy, fs, parse(UiKit.GOLD)); xRight -= 62f * dp
    textR(canvas, "❤️ " + faNum(engine.defenseHp), xRight, cy, fs, parse("#ff8a80"))

    // دکمه منو ☰ (سمت چپ)
    val bs = 40f * dp
    val bx = pad
    val by = (h - bs) / 2
    fillRR(canvas, bx, by, bs, bs, 10f * dp, parse("#23233a"))
    strokeRR(canvas, bx, by, bs, bs, 10f * dp, parse("#3a3a55"), 1.5f)
    textC(canvas, "☰", bx + bs / 2, by + bs / 2, 16f * dp, Color.WHITE)
    regions.add(Region(RectF(bx, by, bx + bs, by + bs)) { openMenu() })
}

// ============================================================
//                    کارت‌های توپ (پایین)
// ============================================================

internal fun UiController.cardRect(i: Int): RectF {
    val cw = min(92f * dp, (W - 10f * dp * 5) / 4f)
    val ch = 72f * dp
    val cx = engine.cannonX(i).toFloat()
    val top = H - cardsH + 8f * dp
    return RectF(cx - cw / 2, top, cx + cw / 2, top + ch)
}

private fun UiController.drawCards(canvas: Canvas) {
    val activePair = engine.activePairFor()
    for ((i, key) in Cfg.BALL_KEYS.withIndex()) {
        val d = Cfg.BALLS[key]!!
        val r = cardRect(i)
        val active = activePair.contains(key)
        // بدنه کارت
        if (active) {
            fillRR(canvas, r.left, r.top, r.width(), r.height(), 12f * dp, parse("#1a1a28"))
            strokeRR(canvas, r.left, r.top, r.width(), r.height(), 12f * dp, parse("#3a3a55"), 1.5f)
        } else {
            fillRR(canvas, r.left, r.top, r.width(), r.height(), 12f * dp, parse("rgba(26,26,40,0.55)"))
            strokeRR(canvas, r.left, r.top, r.width(), r.height(), 12f * dp, parse("#2c2c42"), 1f)
        }
        // عنوان (آیکن + نام) — توپ‌های خارج از نوبت کمرنگ‌تر
        var titleColor = parse(d.color)
        if (!active) {
            titleColor = Color.argb(
                (255 * 0.45).roundToInt(),
                Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor)
            )
        }
        textC(canvas, d.icon + " " + d.name, r.centerX(), r.top + 18f * dp, 11f * dp, titleColor)

        // دکمه + ارتقا (پایین چپ)
        val plusR = 13f * dp
        val px = r.left + 20f * dp
        val py = r.top + 44f * dp
        val cost = engine.upgradeCost(key)
        val affordable = engine.gold >= cost
        val pcolor = if (affordable) parse(UiKit.GREEN) else parse("#55556e")
        canvas.drawCircle(px, py, plusR, Paint().apply {
            color = pcolor
            style = Paint.Style.STROKE
            strokeWidth = 2f * dp
            isAntiAlias = true
        })
        textC(canvas, "+", px, py, 15f * dp, pcolor)
        regions.add(Region(RectF(px - plusR * 1.6f, py - plusR * 1.6f, px + plusR * 1.6f, py + plusR * 1.6f)) {
            if (engine.state == "aim" || true) {
                openModal(key)
                Sfx.play("click")
            }
        })
    }
}

// ============================================================
//                  دکمه بزرگ «شروع ترن»
// ============================================================

private fun UiController.drawStartOverlay(canvas: Canvas) {
    if (engine.state != "aim" || menuOpen) return
    val activePair = engine.activePairFor()
    val inactive = Cfg.BALL_KEYS.filter { !activePair.contains(it) }
    if (inactive.isEmpty()) return
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (k in inactive) {
        val r = cardRect(Cfg.BALL_KEYS.indexOf(k))
        left = min(left, r.left); top = min(top, r.top)
        right = max(right, r.right); bottom = max(bottom, r.bottom)
    }
    // پس‌زمینه‌ی کاملاً کدر (بدون شفافیت) — هم‌رنگ کارت‌های فعال تا هیچ‌چیز از پشت دکمه دیده نشود
    fillRR(canvas, left, top, right - left, bottom - top, 12f * dp, parse("#1a1a28"))
    strokeRR(canvas, left, top, right - left, bottom - top, 12f * dp, parse("#3a3a55"), 1.5f)
    // دکمه اصلی: یک‌سوم کوتاه‌تر از قبل (ارتفاع = ۲/۳ ناحیه) و وسط‌چین
    val inset = 12f * dp
    val bh = (bottom - top) * 2f / 3f
    val bl = left + inset
    val br = right - inset
    val bt = top + ((bottom - top) - bh) / 2f
    val bb = bt + bh
    UiKit.gradRR(canvas, bl, bt, br - bl, bh, 12f * dp, "#2196f3", "#0d47a1")
    textC(canvas, "▶ شروع ترن", (bl + br) / 2, bt + bh * 0.36f, 14f * dp, Color.WHITE)
    val pairNames = activePair.map { Cfg.BALLS[it]!!.name }.joinToString(" + ")
    textC(canvas, "نوبت: " + pairNames, (bl + br) / 2, bt + bh * 0.72f, 9.5f * dp, parse("#cfe8ff"))
    regions.add(Region(RectF(bl, bt, br, bb)) {
        Sfx.play("click")
        if (host.onTrainStartClicked()) {
            pendingStartAfterAd = true // بعد از بسته شدن تبلیغ شروع می‌شود
            return@Region
        }
        engine.startTurn()
    })
}

/** بعد از بسته شدن تبلیغ میان‌صفحه‌ای، ترنِ معلق شروع شود */
fun UiController.resumeAfterAd() {
    if (pendingStartAfterAd) {
        pendingStartAfterAd = false
        engine.startTurn()
    }
}

// ============================================================
//                       منوی اصلی
// ============================================================

private fun UiController.drawMenuScreen(canvas: Canvas) {
    // پس‌زمینه کهکشانی + تزئینات شناور
    drawMenuBackground(canvas)

    val panelW = min(340f * dp, W * 0.9f)
    val btnH = min(52f * dp, (H - 190f * dp) / 6.5f)
    val gap = 9f * dp
    val logoH = 108f * dp
    val bestH = 26f * dp
    val panelH = logoH + 6 * (btnH + gap) + bestH + 20f * dp
    val px = (W - panelW) / 2
    val py = (H - panelH) / 2
    panel(canvas, px, py, panelW, panelH)

    // لوگو
    val badgeR = 26f * dp
    val bcx = W / 2
    val bcy = py + 40f * dp
    UiKit.gradRR(canvas, bcx - badgeR, bcy - badgeR, badgeR * 2, badgeR * 2, 16f * dp, "#3a3a55", "#23233a")
    strokeRR(canvas, bcx - badgeR, bcy - badgeR, badgeR * 2, badgeR * 2, 16f * dp, parse("#b3a5ff"), 2f)
    textC(canvas, "🧱", bcx, bcy, 26f * dp, Color.WHITE)
    textC(canvas, "آجرشکن", bcx, py + 82f * dp, 26f * dp, Color.WHITE)
    textC(canvas, "BRICK DEFENSE", bcx, py + 100f * dp, 10f * dp, parse("#b3a5ff"))

    var by = py + logoH
    val bx = px + 14f * dp
    val bw = panelW - 28f * dp

    button(canvas, bx, by, bw, btnH, "▶  ادامه بازی", "مرحله " + faNum(Prefs.currentStage) + " از " + faNum(Cfg.MAX_STAGE), "primary") {
        goGame(Prefs.currentStage, "campaign")
        Sfx.play("click")
    }; by += btnH + gap
    button(canvas, bx, by, bw, btnH, "♾  مود بی‌نهایت", "بی‌پایان — هر ۱۰ ترن یک دژخیم!", "ghost") {
        goGame(1, "endless")
        toast("♾ مود بی‌نهایت — تا جایی که می‌تونی مقاومت کن!")
        Sfx.play("click")
    }; by += btnH + gap
    button(canvas, bx, by, bw, btnH, "🗺  انتخاب مرحله", "۱۰۰ مرحله در ۱۰ فصل", "ghost") {
        screen = UiController.Screen.STAGES
        scrollY = 0f
        Sfx.play("click")
    }; by += btnH + gap
    button(canvas, bx, by, bw, btnH, "⚙  تنظیمات", "سرعت، مسیر و صدا", "ghost") {
        screen = UiController.Screen.SETTINGS
        Sfx.play("click")
    }; by += btnH + gap
    button(canvas, bx, by, bw, btnH, "📖  راهنمای دشمنان", "شناخت خانه‌های خاص", "ghost") {
        screen = UiController.Screen.GUIDE
        scrollY = 0f
        Sfx.play("click")
    }; by += btnH + gap
    button(canvas, bx, by, bw, btnH, "⏻  خروج", null, "danger") {
        requestExit()
    }; by += btnH + gap

    // رکورد
    val hasBest = Prefs.bestStage > 0
    val bestLine = if (hasBest)
        "🏆 بهترین رکورد: مرحله " + faNum(Prefs.bestStage) + " — ترن " + faNum(Prefs.bestTurn)
    else
        "هنوز رکوردی ثبت نشده — اولین نفری باش که می‌جنگی!"
    textC(canvas, bestLine, W / 2, by + 10f * dp, 11f * dp, parse("#b3a5ff"))
}

private fun UiController.drawMenuBackground(canvas: Canvas) {
    val bg = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, H,
            parse("#0c0c16"), parse("#1d1428"), Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, W, H, bg)
    // آجرها و توپ‌های شناور
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val items = listOf(
        Triple(0.12f, 0.18f, "#ef5350"), Triple(0.82f, 0.26f, "#4fc3f7"),
        Triple(0.2f, 0.8f, "#69f0ae"), Triple(0.85f, 0.72f, "#ffd54f")
    )
    for ((i, it) in items.withIndex()) {
        val x = W * it.first
        val y = H * it.second + (sin(engine.time * 0.8 + i * 1.7) * 12f * dp).toFloat()
        p.color = parse(it.third)
        p.alpha = 60
        canvas.drawRoundRect(x - 18f * dp, y - 8f * dp, x + 18f * dp, y + 8f * dp, 5f * dp, 5f * dp, p)
    }
    for (i in 0 until 3) {
        val x = W * (0.3f + i * 0.22f)
        val y = H * (0.9f - i * 0.06f) + (cos(engine.time * 0.7 + i) * 10f * dp).toFloat()
        p.color = Color.WHITE
        p.alpha = 40
        canvas.drawCircle(x, y, 6f * dp, p)
    }
}

// ============================================================
//                    صفحه انتخاب مرحله
// ============================================================

private fun UiController.drawStagesScreen(canvas: Canvas) {
    drawMenuBackground(canvas)
    val pad = 14f * dp
    val backH = 46f * dp
    val headH = 64f * dp

    // سرصفحه
    textC(canvas, "انتخاب مرحله", W / 2, pad + 16f * dp, 18f * dp, Color.WHITE)
    val total = Cfg.MAX_STAGE
    val unl = Prefs.unlockedStage
    val pct = ((unl - 1).toFloat() / (total - 1) * 100).toInt()
    textC(
        canvas,
        "باز شده: " + faNum(unl) + " از " + faNum(total) + " (" + faNum(pct) + "٪)",
        W / 2, pad + 36f * dp, 11.5f * dp, parse("#8888a5")
    )
    // نوار پیشرفت
    val barW = W - pad * 4
    val barX = (W - barW) / 2
    val barY = pad + 50f * dp
    fillRR(canvas, barX, barY, barW, 7f * dp, 3.5f * dp, parse("#23233a"))
    if (pct > 0) {
        fillRR(canvas, barX, barY, barW * pct / 100f, 7f * dp, 3.5f * dp, parse("#69f0ae"))
    }

    // ناحیه اسکرول
    val viewTop = headH + 10f * dp
    val viewBottom = H - backH - 18f * dp
    val viewH = viewBottom - viewTop

    // محاسبه ارتفاع محتوا
    val CHAPTERS = 10
    val cellW = (W - pad * 2 - 4 * 6f * dp) / 5f
    val cellH = 46f * dp
    val headRowH = 40f * dp
    val gridH = 2 * (cellH + 6f * dp)
    stagesContentH = CHAPTERS * (headRowH + gridH + 12f * dp)

    val maxScroll = max(0f, stagesContentH - viewH)
    scrollY = scrollY.coerceIn(0f, maxScroll)

    canvas.save()
    val clip = RectF(0f, viewTop, W, viewBottom)
    canvas.clipRect(clip)
    var y = viewTop - scrollY
    for (ch in 0 until CHAPTERS) {
        val start = ch * 10 + 1
        val end = start + 9
        val chapterDone = unl > end
        val chapterOpen = unl >= start

        // سرگروه
        val badgeC = if (chapterDone) "#69f0ae" else if (chapterOpen) "#1e88e5" else "#55556e"
        fillRR(canvas, pad, y, 26f * dp, 26f * dp, 8f * dp, parse(badgeC))
        textC(canvas, faNum(ch + 1), pad + 13f * dp, y + 13f * dp, 13f * dp, if (chapterOpen || chapterDone) Color.BLACK else Color.WHITE)
        val wStart = turnsPerStage(start)
        val wEnd = turnsPerStage(end)
        val waveLabel = if (wStart == wEnd) faNum(wStart) + " ترن" else faNum(wStart) + "-" + faNum(wEnd) + " ترن"
        textR(canvas, "مرحله " + faNum(start) + " تا " + faNum(end), W - pad, y + 13f * dp, 12.5f * dp, Color.WHITE)
        textR(canvas, waveLabel, W - pad - 120f * dp, y + 13f * dp, 10.5f * dp, parse("#8888a5"))
        val state = if (chapterDone) "✓ تمام شد" else if (chapterOpen) "● باز" else "🔒 قفل"
        textC(canvas, state, pad + 60f * dp, y + 13f * dp, 10.5f * dp, parse(if (chapterDone) "#69f0ae" else if (chapterOpen) "#64b5f6" else "#55556e"))
        y += headRowH

        // گرید مراحل
        for (s in start..end) {
            val idx = s - start
            val row = idx / 5
            val col = idx % 5
            // RTL: مرحله اول سمت راست
            val cxRight = W - pad - col * (cellW + 6f * dp)
            val cx = cxRight - cellW / 2
            val cy = y + row * (cellH + 6f * dp)
            val unlocked = s <= unl
            val isBossStage = s % 10 == 0
            val isCurrent = s == Prefs.currentStage
            val cleared = unlocked && s < unl
            val cellBg = when {
                !unlocked -> "#1b1b2a"
                cleared -> "#1e3a24"
                else -> "#233a52"
            }
            fillRR(canvas, cx - cellW / 2, cy, cellW, cellH, 10f * dp, parse(cellBg))
            val borderColor = when {
                isCurrent -> "#64b5f6"
                isBossStage -> "#b39ddb"
                else -> "#3a3a55"
            }
            strokeRR(canvas, cx - cellW / 2, cy, cellW, cellH, 10f * dp, parse(borderColor), if (isCurrent) 2f * dp else 1f * dp)
            if (unlocked) {
                val label = if (cleared) "✓ " + faNum(s) else faNum(s)
                textC(canvas, label, cx, cy + cellH / 2 - (if (isBossStage) 5f * dp else 0f), 14f * dp, Color.WHITE)
                if (isBossStage) textC(canvas, "👑", cx, cy + cellH - 10f * dp, 10f * dp, Color.WHITE)
                val st = s
                regions.add(Region(RectF(cx - cellW / 2, cy, cx + cellW / 2, cy + cellH)) {
                    Prefs.currentStage = st
                    goGame(st, "campaign")
                    Sfx.play("click")
                })
            } else {
                textC(canvas, "🔒", cx, cy + cellH / 2, 13f * dp, parse("#55556e"))
            }
        }
        y += gridH + 12f * dp
    }
    canvas.restore()

    // دکمه بازگشت (ثابت)
    button(canvas, W / 2 - 90f * dp, H - backH - 12f * dp, 180f * dp, backH, "بازگشت", null, "primary") {
        screen = UiController.Screen.MENU
    }
}

// ============================================================
//                        تنظیمات
// ============================================================

private fun UiController.drawSettingsScreen(canvas: Canvas) {
    drawMenuBackground(canvas)
    val panelW = min(340f * dp, W * 0.9f)
    val rowH = 52f * dp
    val panelH = 42f * dp + 3 * (rowH + 10f * dp) + 60f * dp
    val px = (W - panelW) / 2
    val py = (H - panelH) / 2
    panel(canvas, px, py, panelW, panelH)
    textC(canvas, "تنظیمات", W / 2, py + 26f * dp, 18f * dp, Color.WHITE)

    var ry = py + 48f * dp
    val innerX = px + 14f * dp
    val innerW = panelW - 28f * dp

    // سرعت توپ‌ها
    textR(canvas, "سرعت توپ‌ها", innerX + innerW, ry + rowH / 2, 13f * dp, Color.WHITE)
    val segW = 168f * dp
    val segX = innerX
    val segBtnW = segW / 3f
    val speeds = listOf(Triple(0.7, "کند", true), Triple(1.0, "معمولی", false), Triple(1.4, "سریع", false))
    for ((i, sp) in speeds.withIndex()) {
        // RTL ترتیب: کند از راست
        val bx = segX + segX * 0f + (2 - i) * segBtnW
        val active = Prefs.speedMul == sp.first
        fillRR(canvas, bx, ry + 8f * dp, segBtnW - 3f * dp, rowH - 16f * dp, 9f * dp,
            parse(if (active) "#1e88e5" else "#23233a"))
        strokeRR(canvas, bx, ry + 8f * dp, segBtnW - 3f * dp, rowH - 16f * dp, 9f * dp,
            parse(if (active) "#64b5f6" else "#3a3a55"), 1f * dp)
        textC(canvas, sp.second, bx + (segBtnW - 3f * dp) / 2, ry + rowH / 2, 12f * dp,
            if (active) Color.WHITE else parse("#8888a5"))
        val v = sp.first
        regions.add(Region(RectF(bx, ry + 8f * dp, bx + segBtnW, ry + rowH - 8f * dp)) {
            Prefs.speedMul = v
            engine.simDirty = true
            Sfx.play("click")
        })
    }
    ry += rowH + 10f * dp

    // نمایش خط مسیر
    textR(canvas, "نمایش خط مسیر توپ‌ها", innerX + innerW, ry + rowH / 2, 13f * dp, Color.WHITE)
    drawToggle(canvas, innerX, ry + 8f * dp, 76f * dp, rowH - 16f * dp, Prefs.showTrajectory) {
        Prefs.showTrajectory = !Prefs.showTrajectory
        engine.simDirty = true
    }
    ry += rowH + 10f * dp

    // صدا
    textR(canvas, "جلوه‌های صوتی", innerX + innerW, ry + rowH / 2, 13f * dp, Color.WHITE)
    drawToggle(canvas, innerX, ry + 8f * dp, 76f * dp, rowH - 16f * dp, Sfx.enabled) {
        Prefs.snd = !Prefs.snd
        Sfx.enabled = Prefs.snd
    }
    ry += rowH + 10f * dp

    button(canvas, innerX, ry + 6f * dp, innerW, 46f * dp, "بازگشت", null, "primary") {
        screen = UiController.Screen.MENU
    }
}

private fun UiController.drawToggle(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, on: Boolean, onClick: () -> Unit) {
    fillRR(canvas, x, y, w, h, h * 0.35f, parse(if (on) "#2e7d32" else "#23233a"))
    strokeRR(canvas, x, y, w, h, h * 0.35f, parse(if (on) "#69f0ae" else "#3a3a55"), 1.5f)
    textC(canvas, if (on) "روشن" else "خاموش", x + w / 2, y + h / 2, 11.5f * dp,
        if (on) Color.WHITE else parse("#8888a5"))
    regions.add(Region(RectF(x, y, x + w, y + h), onClick))
}

// ============================================================
//                      راهنمای دشمنان
// ============================================================

private fun UiController.drawGuideScreen(canvas: Canvas) {
    drawMenuBackground(canvas)
    val pad = 14f * dp
    val backH = 46f * dp
    textC(canvas, "راهنمای دشمنان", W / 2, pad + 16f * dp, 18f * dp, Color.WHITE)
    val viewTop = 48f * dp
    val viewBottom = H - backH - 18f * dp

    // محاسبه ارتفاع محتوا
    var contentH = 0f
    for ((key, d) in Cfg.ENEMIES) {
        if (key == "mini") continue
        contentH += 30f * dp + blockTextHeight(d.desc, W - pad * 3, 10.5f * dp, 4f * dp) + 14f * dp
    }
    val maxScroll = max(0f, contentH - (viewBottom - viewTop))
    scrollY = scrollY.coerceIn(0f, maxScroll)

    canvas.save()
    canvas.clipRect(RectF(0f, viewTop, W, viewBottom))
    var y = viewTop - scrollY
    for ((key, d) in Cfg.ENEMIES) {
        if (key == "mini") continue
        // سطر: رنگ + نام + ضریب HP
        fillRR(canvas, W - pad - 20f * dp, y + 6f * dp, 20f * dp, 20f * dp, 6f * dp, parse(d.color))
        textR(canvas, d.name, W - pad - 28f * dp, y + 16f * dp, 13f * dp, Color.WHITE)
        textR(canvas, "×" + faNum(d.hpMul), W - pad - 100f * dp, y + 16f * dp, 12f * dp, parse("#ffd54f"))
        y += 30f * dp
        // توضیح (بولت‌های راست‌چین)
        val desc = d.desc.ifEmpty { "• آجر ساده بدون ویژگی خاص" }
        val hh = blockText(canvas, desc, pad * 1.5f, y, W - pad * 3, 10.5f * dp, parse("#aaaabf"), 4f * dp)
        y += hh + 14f * dp
    }
    canvas.restore()

    button(canvas, W / 2 - 90f * dp, H - backH - 12f * dp, 180f * dp, backH, "بازگشت", null, "primary") {
        screen = UiController.Screen.MENU
    }
}

// ============================================================
//                         مودال‌ها
// ============================================================

private fun UiController.drawGameModals(canvas: Canvas) {
    // پنجره پایان مرحله / گیم‌اور
    if (modal == Modal.OVERLAY || engine.state == "stageclear" || engine.state == "gameover" || engine.state == "victory") {
        drawOverlayWindow(canvas)
        return // بقیه مودال‌ها زیر پنجره پایان معنا ندارند
    }
    if (modal == Modal.UPGRADE) drawUpgradeModal(canvas)
    if (modal == Modal.GUIDEWIN && guideOpen) drawGuideWinModal(canvas)
    // مودال خروج در drawAll برای همه‌ی صفحه‌ها رسم می‌شود
}

private fun UiController.drawOverlayWindow(canvas: Canvas) {
    val g = engine
    if (g.state != "stageclear" && g.state != "gameover" && g.state != "victory") return
    val isRecord = saveBest()

    val panelW = min(340f * dp, W * 0.9f)
    val px = (W - panelW) / 2
    val py = H * 0.22f
    val title: String
    val stats: String
    var showNext = false
    when (g.state) {
        "stageclear" -> {
            val bts = bossTurns(g.stage)
            title = "🏁 مرحله " + faNum(g.stage) + " تمام شد!"
            stats = faNum(turnsPerStage(g.stage)) + " ترن را موفق گذراندی" +
                    " (دژخیم: ترن " + bts.joinToString(" و ") { faNum(it) } + ")\n" +
                    "طلا: " + faNum(g.gold) + " | جان: " + faNum(g.defenseHp) + "\n" +
                    if (g.stage >= Cfg.MAX_STAGE)
                        "آخرین مرحله کمپین بود — 🏆 قهرمان آجرشکنی!"
                    else
                        "مرحله " + faNum(g.stage + 1) + " باز شد — سخت‌تر از قبل!"
            showNext = g.stage < Cfg.MAX_STAGE
        }
        "victory" -> {
            title = "🏆 پیروزی!"
            stats = "هر ۱۰۰ مرحله را فتح کردی! قهرمان آجرشکنی 🎉\n" +
                    "طلا: " + faNum(g.gold) + " | جان: " + faNum(g.defenseHp)
        }
        else -> {
            title = "بازی تمام شد!"
            stats = (if (isRecord) "🏆 رکورد جدید!\n" else "") +
                    (if (g.mode == "endless")
                        "در مود بی‌نهایت تا دور " + faNum(g.stage) + " و ترن " + faNum(g.turn) + " مقاومت کردی\n"
                    else
                        "در مرحله " + faNum(g.stage) + " از " + faNum(Cfg.MAX_STAGE) + " و ترن " +
                                faNum(g.turnInStage) + "/" + faNum(turnsPerStage(g.stage)) + " شکست خوردی\n") +
                    "طلا: " + faNum(g.gold)
        }
    }
    val innerW = panelW - 32f * dp
    val statsH = paragraphHeight(stats, innerW, 12.5f * dp)
    val btnH = 46f * dp
    val panelH = 64f * dp + statsH + btnH + (if (showNext) btnH + 10f * dp else 0f) + 40f * dp
    panel(canvas, px, py, panelW, panelH)
    textC(canvas, title, W / 2, py + 30f * dp, 17f * dp, Color.WHITE)
    paragraph(canvas, stats, px + 16f * dp, py + 54f * dp, innerW, 12.5f * dp, parse("#bbb"))
    var by = py + panelH - 24f * dp - btnH - (if (showNext) btnH + 10f * dp else 0f)
    if (showNext) {
        button(canvas, px + 16f * dp, by, innerW, btnH, "مرحله بعد ➜", null, "primary") {
            val next = min(g.stage + 1, Cfg.MAX_STAGE)
            Prefs.currentStage = next
            goGame(next, "campaign")
        }
        by += btnH + 10f * dp
    }
    val restartLabel = if (g.state == "stageclear") "تکرار همین مرحله" else "تلاش دوباره"
    button(canvas, px + 16f * dp, by, innerW, btnH, restartLabel, null, "ghost") {
        goGame(g.stage, g.mode)
        startTutorialIfNeeded()
    }
}

private fun UiController.drawUpgradeModal(canvas: Canvas) {
    val key = modalKey ?: return
    val d = Cfg.BALLS[key]!!
    val lv = engine.levels[key] ?: 1
    val cost = engine.upgradeCost(key)
    val st = engine.statsFor(key)
    val stNext: com.jaber.brickdefense.game.BallStats = run {
        engine.levels[key] = lv + 1
        val s = engine.statsFor(key)
        engine.levels[key] = lv
        s
    }

    val panelW = min(360f * dp, W * 0.92f)
    val px = (W - panelW) / 2
    val innerW = panelW - 28f * dp
    val special = UiController.SPECIAL[key] ?: ""
    val specialGap = 4f * dp
    val specialH = blockTextHeight(special, innerW, 10.5f * dp, specialGap)

    // ردیف‌های آمار
    val rows = mutableListOf<Triple<String, String, String>>() // label, value, unit
    fun row(label: String, cur: String, nxt: String?, unit: String?) {
        val v = if (nxt != null && nxt != cur) cur + " ← " + nxt else cur
        rows.add(Triple(label, v, unit ?: ""))
    }
    val D = { n: Double -> faNum(n) }
    when (key) {
        "normal" -> {
            row("آسیب هر گلوله", faNum(st.damage.roundToInt()), faNum(stNext.damage.roundToInt()), null)
            row("تعداد گلوله", faNum(st.count), faNum(stNext.count), "۲+ با هر ارتقا")
            row("زمان اولین شلیک", faNum(st.timerStart.roundToInt()) + " ثانیه", faNum(stNext.timerStart.roundToInt()) + " ثانیه", "۸+ با هر ارتقا")
            row("هر شلیک بعدی", "+" + faNum(Cfg.NORMAL_TIMER_ADD.roundToInt()) + " ثانیه", "+" + faNum(Cfg.NORMAL_TIMER_ADD.roundToInt()) + " ثانیه", "به باقی‌مانده")
            row("دکمه جمع کردن", "کنار شمارنده", null, "محو فوری همه")
            row("جوانه سبز", "هر " + faNum(Cfg.BALLS["normal"]!!.sproutEvery) + " برخورد", null, null)
            row("جوانه‌ی جوانه", "هر " + faNum(Cfg.BALLS["normal"]!!.sproutEveryChild) + " ضربه", null, "تا ۳ بار")
            row("ضربه به نفرین‌شده", "×" + faNum(Cfg.BALLS["ghost"]!!.curseNormalMul.roundToInt()), null, "نفرین روحی")
        }
        "explosive" -> {
            row("آسیب", faNum(st.damage.roundToInt()), faNum(stNext.damage.roundToInt()), null)
            row("شعاع انفجار", D(st.radius), D(stNext.radius), null)
            row("پاشش", faNum((st.splash * 100).roundToInt()) + "٪", faNum((stNext.splash * 100).roundToInt()) + "٪", null)
            row("تعداد گلوله", faNum(st.count), faNum(stNext.count), "با ارتقای زوج")
            row("شانس هر قُل", faNum((st.extraChance * 100).roundToInt()) + "٪ × " + faNum(st.extraRolls) + " قُل",
                faNum((stNext.extraChance * 100).roundToInt()) + "٪ × " + faNum(stNext.extraRolls) + " قُل", "هر سطح یک قُل مستقل ۴۰٪")
            row("آسیب به سنگی", "×" + faNum(Cfg.BALLS["explosive"]!!.stoneMul.roundToInt()), null, "مقاوم")
            row("دژخیم‌کش", "شانس " + faNum((Cfg.BALLS["explosive"]!!.executionerChance * 100).roundToInt()) + "٪", null,
                faNum((Cfg.BALLS["explosive"]!!.executionerDmg * 100).roundToInt()) + "٪ خونِ پُرِ پرخون‌ترین")
        }
        "ghost" -> {
            row("آسیب پایه", faNum(st.damage.roundToInt()), faNum(stNext.damage.roundToInt()), null)
            row("شانس نفرین در هر عبور", "۲۰٪", null, "ژله‌ای مصون")
            row("ضربه به نفرین‌شده", "×" + faNum(Cfg.BALLS["ghost"]!!.curseMul.roundToInt()), null, "همه توپ‌ها")
            row("ضربه معمولی به نفرین‌شده", "×" + faNum(Cfg.BALLS["ghost"]!!.curseNormalMul.roundToInt()), null, "همراهی با روحی")
            row("روح جدید", "هر ۱ کشتار روحی", null, "بدون سقف")
            row("کمبو", "ندارد", null, "آسیب ثابت")
        }
        "electric" -> {
            row("آسیب", faNum(st.damage.roundToInt()), faNum(stNext.damage.roundToInt()), null)
            row("آسیب به ژله‌ای", "نصف (" + faNum((st.damage / 2).roundToInt()) + ")", "نصف (" + faNum((stNext.damage / 2).roundToInt()) + ")", "نرم")
            row("فاصله هر پرش", faNum((st.jumpGap * 10).roundToInt() / 10.0) + " ثانیه", faNum((stNext.jumpGap * 10).roundToInt() / 10.0) + " ثانیه", null)
            row("قدرت هر پرش", "+۵۰٪ پایه", null, "پرش اول = آسیب پایه")
            row("زنجیره برق", "خانه‌های مجاور", null, "لایه‌به‌لایه")
        }
    }
    val rowH = 22f * dp
    val rowsH = (rows.size + 1) * rowH
    val contentH = 52f * dp + specialH + 12f * dp + rowsH + 60f * dp
    val panelH = min(H - 32f * dp, contentH)
    val py = (H - panelH) / 2
    modalPanel = RectF(px, py, px + panelW, py + panelH)
    modalContentH = contentH

    // پس‌زمینه تیره — کلیک بیرون پنل می‌بندد
    canvas.drawRect(0f, 0f, W, H, Paint().apply { color = parse("rgba(0,0,0,0.72)") })
    regions.add(Region(RectF(0f, 0f, W, H)) {
        closeModal()
    })
    panel(canvas, px, py, panelW, panelH)
    // کلیک داخل پنل نباید پنل را ببندد
    regions.add(Region(modalPanel) { /* مصرف کلیک */ })

    // سرصفحه: آیکن + نام + سطح + دکمه بستن
    textC(canvas, d.icon, px + 26f * dp, py + 28f * dp, 22f * dp, Color.WHITE)
    textR(canvas, "توپ " + d.name, px + panelW - 16f * dp, py + 20f * dp, 15f * dp, parse(d.color))
    textR(canvas, "سطح " + faNum(lv), px + panelW - 16f * dp, py + 40f * dp, 11f * dp, parse("#8888a5"))
    fillRR(canvas, px + 10f * dp, py + 10f * dp, 28f * dp, 28f * dp, 8f * dp, parse("#23233a"))
    textC(canvas, "✕", px + 24f * dp, py + 24f * dp, 13f * dp, Color.WHITE)
    regions.add(Region(RectF(px + 8f * dp, py + 8f * dp, px + 40f * dp, py + 40f * dp)) { closeModal() })

    // اسکرول محتوا در صورت بلند بودن
    canvas.save()
    canvas.clipRect(RectF(px + 4f * dp, py + 52f * dp, px + panelW - 4f * dp, py + panelH - 58f * dp))
    val maxScroll = max(0f, contentH - panelH)
    modalScrollY = modalScrollY.coerceIn(0f, maxScroll)
    var cy = py + 52f * dp - modalScrollY

    // بخش ویژگی‌ها: بولت‌های راست‌چین منظم
    blockText(canvas, special, px + 14f * dp, cy, innerW, 10.5f * dp, parse("#aaaabf"), specialGap)
    cy += specialH + 12f * dp

    // جدول ردیف‌ها
    fillRR(canvas, px + 14f * dp, cy, innerW, 22f * dp, 6f * dp, parse("#23233a"))
    textR(canvas, "مشخصه", px + panelW - 20f * dp, cy + 11f * dp, 10.5f * dp, parse("#8888a5"))
    textR(canvas, "فعلی ← بعدی", px + panelW - 140f * dp, cy + 11f * dp, 10.5f * dp, parse("#8888a5"))
    cy += rowH
    for ((label, value, unit) in rows) {
        textR(canvas, label, px + panelW - 20f * dp, cy + 11f * dp, 11f * dp, Color.WHITE)
        textR(canvas, value, px + panelW - 150f * dp, cy + 11f * dp, 10.5f * dp, parse("#69f0ae"))
        if (unit.isNotEmpty()) {
            textR(canvas, unit, px + panelW - 150f * dp - 150f * dp, cy + 11f * dp, 9f * dp, parse("#55556e"))
        }
        cy += rowH
    }
    canvas.restore()

    // دکمه ارتقا
    val affordable = engine.gold >= cost
    val byBtn = py + panelH - 50f * dp
    button(canvas, px + 14f * dp, byBtn, innerW, 42f * dp,
        "ارتقا 🪙" + faNum(cost), null, "primary", disabled = !affordable) {
        if (engine.upgrade(key)) {
            Sfx.play("up")
            modalScrollY = 0f
        } else {
            toast("طلا کافی نیست!")
        }
    }
}

private fun UiController.drawGuideWinModal(canvas: Canvas) {
    val win = currentGuide ?: return
    canvas.drawRect(0f, 0f, W, H, Paint().apply { color = parse("rgba(0,0,0,0.72)") })
    val panelW = min(340f * dp, W * 0.9f)
    val px = (W - panelW) / 2
    val innerW = panelW - 28f * dp
    val bmp: Bitmap? = Assets.get(win.img.removePrefix("img/").removeSuffix(".png"))
    val imgH = if (bmp != null) 84f * dp else 0f
    val textGap = 5f * dp
    val textH = blockTextHeight(win.text, innerW, 12f * dp, textGap)
    val contentH = 24f * dp + imgH + 30f * dp + textH + 20f * dp + 48f * dp + 24f * dp
    val panelH = min(H - 32f * dp, contentH)
    val py = (H - panelH) / 2
    modalPanel = RectF(px, py, px + panelW, py + panelH)
    modalContentH = contentH
    panel(canvas, px, py, panelW, panelH)
    regions.add(Region(modalPanel) { /* مصرف کلیک */ })

    canvas.save()
    canvas.clipRect(RectF(px + 4f * dp, py + 4f * dp, px + panelW - 4f * dp, py + panelH - 58f * dp))
    val maxScroll = max(0f, contentH - panelH)
    modalScrollY = modalScrollY.coerceIn(0f, maxScroll)
    var cy = py + 16f * dp - modalScrollY
    if (bmp != null) {
        val isz = 84f * dp
        val rect = RectF(W / 2 - isz / 2, cy, W / 2 + isz / 2, cy + isz)
        canvas.drawBitmap(bmp, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        cy += imgH + 10f * dp
    }
    textC(canvas, win.title, W / 2, cy + 12f * dp, 15f * dp, Color.WHITE)
    cy += 30f * dp
    // بدنه‌ی راهنما: بولت‌های راست‌چین منظم (به‌جای پاراگراف وسط‌چین)
    blockText(canvas, win.text, px + 14f * dp, cy, innerW, 12f * dp, parse("#ccc"), textGap)
    canvas.restore()

    button(canvas, px + 14f * dp, py + panelH - 54f * dp, innerW, 44f * dp, "متوجه شدم ✔", null, "primary") {
        guideOk()
    }
}

private fun UiController.drawExitConfirm(canvas: Canvas) {
    canvas.drawRect(0f, 0f, W, H, Paint().apply { color = parse("rgba(0,0,0,0.72)") })
    // لمس بیرون پنل = انصراف (پیش از پنل ثبت می‌شود تا اولویت دکمه‌ها بیشتر باشد)
    regions.add(Region(RectF(0f, 0f, W, H)) {
        modal = Modal.NONE
        host.cancelExit()
    })
    val panelW = min(320f * dp, W * 0.88f)
    val px = (W - panelW) / 2
    val panelH = 190f * dp
    val py = (H - panelH) / 2
    panel(canvas, px, py, panelW, panelH)
    // لمس داخل پنل بسته نشود
    regions.add(Region(RectF(px, py, px + panelW, py + panelH)) { /* مصرف کلیک */ })
    textC(canvas, "خروج از برنامه", W / 2, py + 34f * dp, 17f * dp, Color.WHITE)
    textC(canvas, "آیا می‌خواهید از برنامه خارج شوید؟", W / 2, py + 66f * dp, 12.5f * dp, parse("#bbb"))
    val btnW = (panelW - 40f * dp - 10f * dp) / 2
    val by = py + panelH - 66f * dp
    button(canvas, px + 20f * dp, by, btnW, 44f * dp, "بله، خارج شو", null, "danger") {
        modal = Modal.NONE
        host.exitConfirmed()
    }
    // RTL: «خیر» سمت راست
    button(canvas, px + 20f * dp + btnW + 10f * dp, by, btnW, 44f * dp, "خیر", null, "ghost") {
        modal = Modal.NONE
        host.cancelExit()
    }
}

// ============================================================
//                          توست
// ============================================================

private fun UiController.drawToast(canvas: Canvas) {
    if (System.currentTimeMillis() > toastUntil) return
    val w = paragraphHeight(toastMsg, W - 60f * dp, 12f * dp).coerceAtMost(40f * dp)
    val tw = min(W - 40f * dp, 400f * dp)
    val th = max(34f * dp, w + 18f * dp)
    val x = (W - tw) / 2
    val y = H - cardsH - th - 14f * dp
    fillRR(canvas, x, y, tw, th, th / 2, parse("rgba(26,26,40,0.95)"))
    strokeRR(canvas, x, y, tw, th, th / 2, parse("#3a3a55"), 1f)
    paragraph(canvas, toastMsg, x + 16f * dp, y + th / 2 - w / 2, tw - 32f * dp, 12f * dp, Color.WHITE)
}

// ============================================================
//                     مدیریت لمس (input)
// ============================================================

private fun UiController.nearestCannon(x: Float, y: Float): Int {
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in 0 until 4) {
        val d = kotlin.math.hypot(
            (engine.cannonX(i) - x).toDouble(),
            (engine.cannonY - y).toDouble()
        )
        if (d < bestD) {
            bestD = d; best = i
        }
    }
    return best
}

private fun UiController.setAimAt(x: Float, y: Float) {
    val dc = dragCannon ?: return
    if (engine.state != "aim") return
    val dragKey = Cfg.BALL_KEYS[dc]
    // فقط توپ‌های فعال این ترن قابل نشانه‌گیری هستند
    if (!engine.activePairFor().contains(dragKey)) {
        toast(Cfg.BALLS[dragKey]!!.name + " این ترن فعال نیست")
        dragCannon = null
        engine.aimKey = null
        return
    }
    var a = atan2((y - engine.cannonY).toDouble(), (x - engine.cannonX(dc)).toDouble())
    // فقط نیمه بالایی: بازه مجاز [-PI+0.12 ، -0.12]
    if (a > 0 && a < PI / 2) a = -0.12
    else if (a >= PI / 2) a = -PI + 0.12
    a = com.jaber.brickdefense.game.clamp(a, -PI + 0.12, -0.12)
    engine.setAim(dragKey, a)
}

/**
 * پردازش رویداد لمس؛ خروجی true یعنی مصرف شد.
 * اولویت: ناحیه‌های UI (از فریم قبلی) ← دکمه جمع کردن ← نشانه‌گیری/اسکرول
 */
fun UiController.onTouch(action: Int, x: Float, y: Float): Boolean {
    val DOWN = 0; val MOVE = 1; val UP = 2; val CANCEL = 3
    when (action) {
        DOWN -> {
            downX = x; downY = y; lastY = y; moved = false
            // کلیک روی ناحیه‌های UI؟ (فقط علامت‌گذاری؛ کلیک روی UP اجرا می‌شود)
            for (r in regions) {
                if (r.rect.contains(x, y)) return true // ناحیه UI — منتظر UP می‌مانیم
            }
            // دکمه «جمع کردن» کنار شمارنده معمولی
            if (!menuOpen && modal == Modal.NONE && !guideOpen &&
                engine.state == "fire" && engine.collectBtnRect != null && engine.normalTimerVisible
            ) {
                val r = engine.collectBtnRect!!
                if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
                    engine.expireNormalBalls()
                    return true
                }
            }
            // شروع اسکرول صفحات قابل اسکرول
            if (menuOpen && (screen == Screen.STAGES || screen == Screen.GUIDE)) {
                stageScrollDrag = true
                return true
            }
            // اسکرول داخل مودال
            if (modal == Modal.UPGRADE || (modal == Modal.GUIDEWIN && guideOpen)) {
                if (modalPanel.contains(x, y) && modalContentH > modalPanel.height()) {
                    modalScrollDrag = true
                    return true
                }
            }
            // نشانه‌گیری روی زمین بازی
            if (!menuOpen && modal == Modal.NONE && !guideOpen && engine.state == "aim") {
                dragCannon = nearestCannon(x, y)
                engine.aimKey = Cfg.BALL_KEYS[dragCannon!!] // خط‌چین این توپ برجسته می‌شود
                setAimAt(x, y)
                return true
            }
            return !menuOpen // در بازی لمس‌های آزاد مصرف می‌شوند
        }
        MOVE -> {
            val dy = y - lastY
            lastY = y
            if (kotlin.math.abs(y - downY) > 8f * dp || kotlin.math.abs(x - downX) > 8f * dp) moved = true
            if (dragCannon != null) {
                setAimAt(x, y)
                return true
            }
            if (stageScrollDrag) {
                scrollY -= dy
                return true
            }
            if (modalScrollDrag) {
                modalScrollY -= dy
                return true
            }
            return dragCannon != null
        }
        UP, CANCEL -> {
            val wasMoved = moved
            dragCannon = null
            engine.aimKey = null
            if (stageScrollDrag) {
                stageScrollDrag = false
                if (wasMoved) return true
            }
            if (modalScrollDrag) {
                modalScrollDrag = false
                if (wasMoved) return true
            }
            // کلیک: اگر حرکت کوچک بود و روی ناحیه‌ای بودیم (روی‌نویس‌ها اول)
            if (!wasMoved) {
                for (r in regions.asReversed()) {
                    if (r.rect.contains(x, y)) {
                        r.onClick()
                        return true
                    }
                }
            }
            return true
        }
    }
    return false
}
