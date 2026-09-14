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

        // پنجره‌های آموزش شروع (متن HTML نسخه اصلی به متن ساده تبدیل شد)
        fun tutorialSteps(): List<Cfg.Guide> = listOf(
            Cfg.Guide(
                "img/top_usual.png",
                "به آجرشکن خوش آمدی! 🎮 توپ‌ها و ترتیب شلیک",
                "۴ توپ جنگی داری: معمولی ⚪ (انعکاس + جوانه سبز + ضربه ۱۰ برابر به خانه‌های نفرین‌شده)، انفجاری 💥 (انفجار با پاشش، به سنگی ۲ برابر آسیب)، روحی 👻 (عبور از میان خانه‌ها + نفرین ۲۰٪: خانه نفرین‌شده به ضربه‌ها ۳ برابر آسیب می‌بیند) و برقی ⚡ (زنجیره پرش به خانه‌های مجاور، به ژله‌ای نصف آسیب).\n" +
                        "در هر ترن فقط ۲ توپ فعال است: ترن فرد «معمولی + انفجاری» و ترن زوج «روحی + برقی». توپ‌های غیرفعال کم‌رنگ می‌شوند و قابل نشانه‌گیری نیستند.\n" +
                        "هر توپ فعال را جداگانه می‌توانی نشانه‌گیری کنی: انگشتت را روی زمین بکش تا لوله‌ی همان توپ بچرخد (خط‌چینِ مسیرِ همه توپ‌ها همیشه دیده می‌شود). توپ‌های فعال به ترتیب شماره ۱ و ۲ روی کارت‌ها پشت سر هم شلیک می‌شوند — با زدن نشان عددی روی دو کارت، ترتیب شلیک را جابه‌جا کن."
            ),
            Cfg.Guide(
                "img/top_bomb.png",
                "ارتقا (آپگرید) توپ‌ها ⬆ و شروع ترن ▶",
                "روی کارت توپ‌های فعال دکمه «+» سبز هست؛ با زدنش پنجره‌ی ارتقا باز می‌شود و با طلا می‌توانی سطح همان توپ را بالا ببری (گلوله بیشتر، آسیب بیشتر، شعاع بیشتر...). پنجره ارتقا نشان «فعلی ← بعدی» را هم مقایسه می‌کند.\n" +
                        "دکمه بزرگ مربعی «شروع ترن ▶» روی دو توپ غیرفعال این ترن نشسته — با زدنش ترن شروع می‌شود و نام جفت فعال هم رویش نوشته شده.\n" +
                        "طلا را از کشتن خانه‌ها و دژخیم می‌گیری (🪙 در بالای صفحه)."
            ),
            Cfg.Guide(
                "img/iron1.png",
                "هدف بازی 🏁",
                "باید تا پایان مرحله زنده بمونی: خانه‌ها هر ترن یک ردیف پایین می‌آیند و اگر از خط دفاع قرمز رد شوند جان کم می‌کنی (آهنی ۲ جان!).\n" +
                        "مرحله‌های ۱ تا ۳ هرکدام ۱۰ ترن است؛ مرحله‌های ۴ تا ۱۰ هرکدام ۲۰ ترن و از مرحله ۱۱ به بعد هر ۱۰ مرحله ۱۰ ترن بیشتر می‌شود (تا سقف ۶۰).\n" +
                        "دژخیم هر ۱۰ ترن یک‌بار می‌آید: ترن ۱۰ و ۲۰ و ۳۰ و... — با کشتنش طلا و ۲ جان برمی‌گردد. با گذراندن مرحله، مرحله بعدی باز می‌شود (کمپین ۱۰۰ مرحله‌ای)."
            )
        )

        // توضیح مکانیک ویژه هر توپ (پورت SPECIAL)
        val SPECIAL = mapOf(
            "normal" to "هر گلوله ۲ آسیب می‌زند و با تعداد برخورد محو نمی‌شود — گلوله‌ها (اصلی و جوانه‌ها) فقط وقتی شمارنده بزرگ زمان (روی خط دفاع، بالاتر از توپ‌خانه‌ها) صفر شود همه‌با هم محو می‌شوند؛ اولین شلیک ۳۰ ثانیه است و با هر ارتقای توپ معمولی ۸ ثانیه بیشتر می‌شود و هر شلیک بعدی ۱۵ ثانیه به باقی‌مانده اضافه می‌کند. کنار شمارنده دکمه «🧹 جمع کردن» هست: با زدنش همه‌ی گلوله‌های معمولی روی صفحه فوراً محو می‌شوند. اگر خانه‌ای با گلوله روحی نفرین شده باشد، ضربه‌ی توپ معمولی به آن ۱۰ برابر آسیب می‌زند! هر ۵ برخوردِ هر گلوله، یک گلوله‌ی سبزِ روشن از نقطه‌ی برخورد با جهت رندوم آزاد می‌شود — جوانه‌ها هم می‌توانند جوانه بزنند اما به ۱۵ ضربه نیاز دارند (تا ۳ بار).",
            "explosive" to "انفجار با شعاع ۲ برابر و پاشش (قدرت ۳۰٪ بیشتر). به خانه‌های سنگی ۲ برابر آسیب می‌زند (اصابت مستقیم و پاشش). در هر ترن به تعداد سطح، شانس مستقل ۴۰٪ برای گلوله اضافه دارد (لول ۱ = ۱ قُل، لول ۲ = ۲ قُل...). قابلیت «دژخیم‌کش»: هر ضربه‌ی بمب ۲۰٪ شانس دارد خونه‌ای که بیشترین خون را دارد هدف بگیرد و ۵٪ از خونِ پُرِ آن را مستقیم کم کند (ژله‌ای مصون است و سپر دژخیم این ضربه را هم دفع می‌کند).",
            "ghost" to "از میان خانه‌ها عبور می‌کند و به هر خانه فقط یک‌بار آسیب ثابت خودش را می‌زند (به ژله‌ای دیگر چندبرابر نمی‌زند). مهم‌ترین ویژگی‌اش «نفرین» است: از هر خانه‌ای که عبور می‌کند با ۲۰٪ شانس یک نفرین به جا می‌گذارد — خانه‌ی نفرین‌شده به همه‌ی ضربه‌های بعدی ۳ برابر آسیب می‌بیند و اگر توپ معمولی به آن بخورد ۱۰ برابر! خانه‌های ژله‌ای مصون‌اند و هیچ‌وقت نفرین نمی‌شوند. همیشه حداقل ۱ گلوله شلیک می‌کند؛ با هر کشتار روحی ۱ روح می‌گیرد (بدون سقف) و همه پشت سر هم شلیک می‌شوند. کشتارهای روحی کمبو هم ندارند — پاداششان طلای ثابت است.",
            "electric" to "برق آبیِ جرقه‌ای؛ به خانه‌های مجاور خانه‌ی اصابت پرش می‌کند و زنجیره بین خانه‌های مجاور پیش می‌رود — هر پرش ۰.۱ ثانیه فاصله دارد و ۵۰٪ قدرت پایه روی پرش قبلی اضافه می‌شود (جمعی: ۲۷ ← ۴۰ ← ۵۴...). به خانه‌های ژله‌ای نصف آسیب می‌زند (اصابت مستقیم، زنجیره و برق سراسری). برخورد مستقیم با خانه آهنی = برق قرمزِ فوری به همه‌ی خانه‌ها. اگر زنجیره به بیش از ۶ خانه برخورد کند، پیام «ضربه فلا» با ضریب قدرت نشان داده می‌شود."
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

    var orderSel: String? = null

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
