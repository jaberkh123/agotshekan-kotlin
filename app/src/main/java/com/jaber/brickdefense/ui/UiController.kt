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
import com.jaber.brickdefense.ui.UiKit.button
import com.jaber.brickdefense.ui.UiKit.fillRR
import com.jaber.brickdefense.ui.UiKit.panel
import com.jaber.brickdefense.ui.UiKit.paragraph
import com.jaber.brickdefense.ui.UiKit.paragraphHeight
import com.jaber.brickdefense.ui.UiKit.parse
import com.jaber.brickdefense.ui.UiKit.strokeRR
import com.jaber.brickdefense.ui.UiKit.textC
import com.jaber.brickdefense.ui.UiKit.textR
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

/** پل ارتباطی با MainActivity (تبلیغات + خروج) */
interface Host {
    /** خروجی true یعنی تبلیغ در حال نمایش است و ترن باید بعد از بستن تبلیغ شروع شود */
    fun onTrainStartClicked(): Boolean
    fun armExit()
    fun cancelExit()
    fun exitConfirmed()
    fun setMenuOpen(open: Boolean)
}

/**
 * پورت کامل ui.js + صفحه‌های index.html — همه‌چیز با Canvas بومی رندر می‌شود
 * (هیچ HTML/CSS/JS وجود ندارد).
 */
class UiController(val engine: com.jaber.brickdefense.game.GameEngine, val host: Host) {

    companion object {
        const val HUD_H_DP = 48f
        const val CARDS_H_DP = 86f

        // پنجره‌های آموزش شروع — هر خط یک بولت مستقل (راست‌چین و منظم)
        fun tutorialSteps(): List<Cfg.Guide> = listOf(
            Cfg.Guide(
                "img/top_usual.png",
                "به آجرشکن خوش آمدی! 🎮 توپ‌ها و شلیک",
                "• ۴ توپ داری: معمولی ⚪، انفجاری 💥، روحی 👻 و برقی ⚡\n" +
                    "• معمولی: انعکاس + جوانه‌ی سبز + ضربه‌ی ۱۰ برابر به خانه‌ی نفرین‌شده\n" +
                    "• انفجاری: انفجار با پاشش؛ به سنگی ۲ برابر آسیب\n" +
                    "• روحی: از میان خانه‌ها عبور می‌کند + نفرین ۲۰٪ (خانه‌ی نفرین‌شده ۳ برابر آسیب می‌بیند)\n" +
                    "• برقی: زنجیره به خانه‌های مجاور؛ به ژله‌ای نصف آسیب\n" +
                    "• در هر ترن فقط ۲ توپ فعال است: فرد «معمولی + انفجاری»، زوج «روحی + برقی»\n" +
                    "• توپ غیرفعال کم‌رنگ است و نشانه‌گیری نمی‌شود\n" +
                    "• نشانه‌گیری: انگشتت را روی زمین بکش تا لوله‌ی توپ بچرخد\n" +
                    "• توپ‌های فعال به ترتیب ۱ و ۲ پشت سر هم شلیک می‌شوند"
            ),
            Cfg.Guide(
                "img/top_bomb.png",
                "ارتقا (آپگرید) توپ‌ها ⬆ و شروع ترن ▶",
                "• روی کارت توپ‌های فعال، دکمه «+» سبز هست\n" +
                    "• با زدنش پنجره‌ی ارتقا باز می‌شود؛ با طلا سطح توپ را بالا ببر\n" +
                    "• هر سطح = گلوله‌ی بیشتر، آسیب بیشتر، شعاع بیشتر…\n" +
                    "• پنجره‌ی ارتقا مقایسه‌ی «فعلی ← بعدی» را نشان می‌دهد\n" +
                    "• دکمه‌ی آبی «شروع ترن ▶» روی دو توپ غیرفعال نشسته\n" +
                    "• با زدنش ترن شروع می‌شود؛ نام جفت فعال روی دکمه نوشته شده\n" +
                    "• طلا را از کشتن خانه‌ها و دژخیم بگیر (🪙 بالای صفحه)"
            ),
            Cfg.Guide(
                "img/iron1.png",
                "هدف بازی 🏁",
                "• تا پایان مرحله زنده بمان!\n" +
                    "• خانه‌ها هر ترن یک ردیف پایین می‌آیند\n" +
                    "• عبور از خط دفاع قرمز = کم‌شدن جان (آهنی ۲ جان!)\n" +
                    "• مرحله‌های ۱ تا ۳ = ۱۰ ترن؛ مرحله‌های ۴ تا ۱۰ = ۲۰ ترن\n" +
                    "• از مرحله ۱۱ به بعد هر ۱۰ مرحله، ۱۰ ترن بیشتر (تا سقف ۶۰)\n" +
                    "• دژخیم هر ۱۰ ترن می‌آید: ترن ۱۰ و ۲۰ و ۳۰ و…\n" +
                    "• کشتن دژخیم = طلا + ۲ جان\n" +
                    "• با گذراندن هر مرحله، مرحله‌ی بعد باز می‌شود (کمپین ۱۰۰ مرحله‌ای)"
            )
        )

        // توضیح مکانیک ویژه هر توپ — بولت‌بندی‌شده (پورت SPECIAL)
        val SPECIAL = mapOf(
            "normal" to "• هر گلوله ۲ آسیب می‌زند و با برخورد محو نمی‌شود\n" +
                "• شمارنده‌ی بزرگ زمان (بالای خط دفاع) همه‌ی گلوله‌های معمولی را با هم صفر می‌کند\n" +
                "• شلیک اول ۳۰ ثانیه است؛ هر ارتقا ۸ ثانیه اضافه می‌کند\n" +
                "• هر شلیک بعدی ۱۵ ثانیه به باقی‌مانده اضافه می‌کند\n" +
                "• دکمه‌ی «🧹 جمع کردن» کنار شمارنده: همه‌ی گلوله‌ها فوراً محو می‌شوند\n" +
                "• هر ۵ برخوردِ هر گلوله، یک گلوله‌ی سبز با جهت رندوم آزاد می‌شود\n" +
                "• جوانه‌ها هم جوانه می‌زنند؛ اما هر ۱۵ ضربه و حداکثر ۳ بار\n" +
                "• ضربه به خانه‌ی نفرین‌شده: ۱۰ برابر آسیب!",
            "explosive" to "• انفجار با شعاع ۲ برابر و پاشش (۳۰٪ قدرت)\n" +
                "• به خانه‌های سنگی ۲ برابر آسیب (مستقیم و پاشش)\n" +
                "• در هر ترن به تعداد سطح، شانس ۴۰٪ گلوله‌ی اضافه (لول ۱ = ۱ قُل، لول ۲ = ۲ قُل…)\n" +
                "• دژخیم‌کش: هر ضربه ۲۰٪ شانس هدف‌گیری پرخون‌ترین خانه و کسر ۵٪ از خونِ پُرش\n" +
                "• ژله‌ای مصون است؛ سپر دژخیم این ضربه را هم دفع می‌کند",
            "ghost" to "• از میان خانه‌ها عبور می‌کند و به هر خانه فقط یک‌بار آسیب می‌زند\n" +
                "• آسیب ثابت؛ به ژله‌ای چندبرابر نمی‌زند\n" +
                "• نفرین ۲۰٪: خانه‌ی نفرین‌شده به همه‌ی ضربه‌ها ۳ برابر آسیب می‌بیند (معمولی: ۱۰ برابر)\n" +
                "• ژله‌ای مصون از نفرین است\n" +
                "• همیشه حداقل ۱ گلوله شلیک می‌کند\n" +
                "• هر کشتار روحی = ۱ روح ذخیره (بدون سقف)؛ همه پشت سر هم شلیک می‌شوند\n" +
                "• کشتار روحی کمبو ندارد؛ پاداشش طلای ثابت است",
            "electric" to "• برق آبی جرقه‌ای با زنجیره‌ی پرش به خانه‌های مجاور\n" +
                "• فاصله‌ی هر پرش ۰.۱ ثانیه\n" +
                "• هر پرش ۵۰٪ قدرت پایه را جمعی اضافه می‌کند (۲۷ ← ۴۰ ← ۵۴…)\n" +
                "• به ژله‌ای نصف آسیب (مستقیم، زنجیره و سراسری)\n" +
                "• برخورد مستقیم با خانه‌ی آهنی = برق قرمز فوری به همه‌ی خانه‌ها\n" +
                "• زنجیره‌ی بیش از ۶ خانه = پیام «ضربه فلا»"
        )
    }

    enum class Screen { MENU, STAGES, SETTINGS, GUIDE, GAME }
    enum class Modal { NONE, UPGRADE, GUIDEWIN, EXIT, OVERLAY }

    var screen: Screen = Screen.MENU
    var menuOpen: Boolean = true
    var modal: Modal = Modal.NONE
    var modalKey: String? = null

    // پنجره راهنما (آموزش + خانه‌های جدید)
    var guideOpen: Boolean = false
    var currentGuide: Cfg.Guide? = null
    internal val guideQueue = ArrayDeque<Cfg.Guide>()
    var tutorialActive: Boolean = false
    internal var tutorialRemaining: List<Cfg.Guide> = emptyList()

    // توست
    internal var toastMsg: String = ""
    internal var toastUntil: Long = 0

    // اسکرول صفحه انتخاب مرحله
    internal var scrollY: Float = 0f
    internal var stagesContentH: Float = 0f

    // لمس
    internal var downX = 0f
    internal var downY = 0f
    internal var lastY = 0f
    internal var moved = false
    internal var dragCannon: Int? = null
    internal var stageScrollDrag = false
    // اسکرول داخل مودال‌ها
    internal var modalScrollY: Float = 0f
    internal var modalContentH: Float = 0f
    internal var modalPanel = RectF()
    internal var modalScrollDrag = false
    // pending: ترن بعد از بسته شدن تبلیغ شروع شود
    internal var pendingStartAfterAd: Boolean = false

    var W: Float = 0f
    var H: Float = 0f
    var dp: Float = 3f
    var hudH: Float = 0f
    var cardsH: Float = 0f

    /** اندازه منطقی صفحه (در GameView ست می‌شود) */
    fun layout(w: Float, h: Float, density: Float) {
        W = w; H = h; dp = density
        hudH = HUD_H_DP * dp
        cardsH = CARDS_H_DP * dp
        UiKit.clearRegions()
    }

    fun menuOpen(open: Boolean) {
        menuOpen = open
        host.setMenuOpen(open)
    }

    fun toast(msg: String) {
        toastMsg = msg
        toastUntil = System.currentTimeMillis() + 1300
    }

    // ---------- چرخه صفحه‌ها ----------

    fun goGame(resetStage: Int, mode: String) {
        engine.reset(resetStage, mode)
        screen = Screen.GAME
        modal = Modal.NONE
        menuOpen(false)
        startTutorialIfNeeded()
    }

    fun backToMenu() {
        closeModal()
        menuOpen(true)
        modal = Modal.NONE
        screen = Screen.MENU
        syncBestLine()
    }

    fun openMenu() {
        menuOpen(true)
        closeModal()
        modal = Modal.NONE
        screen = Screen.MENU
        syncBestLine()
    }

    fun syncBestLine() { /* رکورد هر بار در drawMenu خوانده می‌شود */ }

    fun startTutorialIfNeeded() {
        if (engine.mode == "endless") return // آموزش فقط برای کمپین
        if (engine.stage != 1) return        // فقط ورود به مرحله ۱
        val T = tutorialSteps()
        tutorialActive = true
        tutorialRemaining = T.drop(1)
        showGuideWindow(T[0])
    }

    fun showGuideWindow(win: Cfg.Guide) {
        if (guideOpen) {
            guideQueue.add(win)
            return
        }
        guideOpen = true
        currentGuide = win
        modal = Modal.GUIDEWIN
    }

    fun guideOk() {
        Sfx.play("click")
        guideOpen = false
        currentGuide = null
        if (tutorialActive && tutorialRemaining.isNotEmpty()) {
            // پنجره بعدی آموزش
            val next = tutorialRemaining.first()
            tutorialRemaining = tutorialRemaining.drop(1)
            showGuideWindow(next)
        } else if (tutorialActive) {
            tutorialActive = false
            modal = Modal.NONE
        } else if (guideQueue.isNotEmpty()) {
            val next = guideQueue.removeFirst()
            showGuideWindow(next)
        } else {
            modal = Modal.NONE
        }
    }

    /** پنجره راهنمای اولین ظاهر خانه‌های جدید — هر فریم چک می‌شود */
    internal fun flushHouseGuides() {
        if (tutorialActive) return // اول آموزش امکانات، بعد راهنمای خانه‌ها
        if (guideOpen) return
        if (engine.pendingGuides.isNotEmpty()) {
            val win = engine.pendingGuides.removeFirst()
            showGuideWindow(win)
        }
    }

    // ----- مودال ارتقا -----
    fun openModal(key: String) {
        modalKey = key
        modal = Modal.UPGRADE
    }

    fun closeModal() {
        modalKey = null
        if (modal == Modal.UPGRADE) modal = Modal.NONE
    }

    // ----- تصمیم‌گیر مرکزی دکمه بازگشت (پورت handleBackButton) -----
    fun handleBackButton() {
        // ۱) پنجره تأیید خروج باز است ← بستن آن
        if (modal == Modal.EXIT) {
            modal = Modal.NONE
            host.cancelExit()
            return
        }
        // ۲) مودال ارتقا باز است ← بستن
        if (modal == Modal.UPGRADE) {
            closeModal()
            return
        }
        // ۳) پنجره آموزش/راهنمای خانه‌ها باز است ← بستن کامل (ادامه صف هم لغو شود)
        if (guideOpen) {
            guideOpen = false
            currentGuide = null
            tutorialActive = false
            tutorialRemaining = emptyList()
            guideQueue.clear()
            engine.pendingGuides.clear()
            modal = Modal.NONE
            return
        }
        // ۴) در صفحه بازی هستیم — حتی اگر پنجره گیم‌اور/پایان مرحله باز باشد ← منو
        if (!menuOpen) {
            backToMenu()
            return
        }
        // ۵) یکی از زیرصفحه‌های منو باز است ← برگشت به خانه منو
        if (screen != Screen.MENU) {
            backToMenu()
            return
        }
        // ۶) در خانه منو هستیم ← پنجره تأیید خروج
        requestExit()
    }

    fun requestExit() {
        host.armExit()
        modal = Modal.EXIT
    }

    // ---------- رکورد بهترین نتیجه ----------
    internal fun saveBest(): Boolean {
        val curStage = engine.stage
        val curTurn = engine.turn
        if (curStage > Prefs.bestStage || (curStage == Prefs.bestStage && curTurn > Prefs.bestTurn)) {
            Prefs.bestStage = curStage
            Prefs.bestTurn = curTurn
            return true
        }
        return false
    }
}
