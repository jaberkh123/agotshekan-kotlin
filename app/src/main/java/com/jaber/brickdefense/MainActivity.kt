package com.jaber.brickdefense

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryListener
import com.adivery.sdk.AdiveryNativeAdView
import com.jaber.brickdefense.game.Assets
import com.jaber.brickdefense.game.Prefs
import com.jaber.brickdefense.game.Sfx
import com.jaber.brickdefense.ui.GameView
import com.jaber.brickdefense.ui.resumeAfterAd
import com.jaber.brickdefense.ui.Host

/**
 * پورت MainActivity.java به کاتلین — این بار به‌جای WebView، بازی «بومی» است:
 * GameView (رندر Canvas بومی) + تبلیغ همسان ادیوری در پایین صفحه.
 */
class MainActivity : Activity(), Host {

    private lateinit var gameView: GameView
    private var nativeAd: AdiveryNativeAdView? = null

    /** شمارنده کلیک روی دکمه «شروع ترن» — هر ۱۵ کلیک یک تبلیغ میان‌صفحه‌ای */
    private var trainStartCount = 0

    /** شمارنده تلاش مجدد لود تبلیغ همسان در صورت خطا */
    private var nativeRetryCount = 0

    /** مسلح‌شدن «دوبار بک پشت‌سرهم در خانه منو = خروج مستقیم» */
    @Volatile
    private var exitArmed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ذخیره‌سازی + صداها + اسپرایت‌ها
        Prefs.init(applicationContext)
        Sfx.enabled = Prefs.snd
        Assets.load(applicationContext)

        // لایوت کاملاً برنامه‌نویسی‌شده (بدون XML لایوت):
        // GameView تمام‌ارتفاع + تبلیغ همسان ادیوری در پایین
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF12121A.toInt())

        gameView = GameView(this)
        gameView.setupUi(this)
        root.addView(
            gameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val ad = AdiveryNativeAdView(this)
        ad.visibility = View.GONE
        root.addView(
            ad,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        nativeAd = ad

        setContentView(root)
        hideSystemUI()
        setupAds()
    }

    private fun hideSystemUI() {
        val decor = window.decorView
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    // ---------- تبلیغات ادیوری ----------

    private fun setupAds() {
        // آماده‌سازی تبلیغ‌های تمام‌صفحه؛ ادیوری بعد از هر نمایش، بعدی را خودش آماده می‌کند
        Adivery.prepareInterstitialAd(this, Ads.INTERSTITIAL_ID)
        Adivery.prepareAppOpenAd(this, Ads.APP_OPEN_ID)

        // تبلیغ همسان پایین صفحه (زیر دکمه «شروع ترن»)
        val ad = nativeAd
        if (ad != null) {
            ad.setNativeAdLayout(R.layout.adivery_native_ad_layout)
            ad.setListener(object : AdiveryAdListener() {
                override fun onAdLoaded() {
                    runOnUiThread {
                        nativeAd?.visibility = View.VISIBLE
                    }
                }

                override fun onError(reason: String) {
                    // بدون اینترنت یا بدون تبلیغ → فضایی اشغال نمی‌شود؛ بعداً دوباره تلاش می‌کنیم
                    runOnUiThread {
                        nativeAd?.visibility = View.GONE
                    }
                    nativeRetryCount++
                    if (nativeRetryCount <= 5) {
                        val delay = 20000L * nativeRetryCount // 20s، 40s، 60s و ...
                        mainHandler.postDelayed({
                            nativeAd?.loadAd(Ads.NATIVE_ID)
                        }, delay)
                    }
                }
            })
            ad.loadAd(Ads.NATIVE_ID)
        }

        // وقتی میان‌صفحه‌ای بسته شد، ترنِ معلق‌شده شروع شود
        Adivery.addGlobalListener(object : AdiveryListener() {
            override fun onInterstitialAdClosed(placementId: String) {
                if (Ads.INTERSTITIAL_ID != placementId) return
                runOnUiThread {
                    gameView.ui.resumeAfterAd()
                }
            }
        })
    }

    // ---------- Host (پل UI بازی با تبلیغات) ----------

    /**
     * هر کلیک روی دکمه «شروع ترن» اینجا شمرده می‌شود؛
     * هر ۱۵ کلیک، تبلیغ میان‌صفحه‌ای ادیوری نمایش داده می‌شود.
     * خروجی true یعنی تبلیغ در حال نمایش است و ترن باید بعد از بستن تبلیغ شروع شود.
     */
    override fun onTrainStartClicked(): Boolean {
        trainStartCount++
        if (trainStartCount % 15 == 0 && Adivery.isLoaded(Ads.INTERSTITIAL_ID)) {
            runOnUiThread {
                Adivery.showAd(Ads.INTERSTITIAL_ID)
            }
            return true
        }
        return false
    }

    override fun armExit() {
        exitArmed = true
    }

    override fun cancelExit() {
        exitArmed = false
    }

    /** دکمه «بله، خارج شو» → بستن کامل برنامه با ضربه‌ی امنیتی */
    override fun exitConfirmed() {
        exitArmed = false
        exitNow()
    }

    override fun setMenuOpen(open: Boolean) {
        // وضعیت منو در GameView.ui.menuOpen نگه‌داری می‌شود؛ اینجا فقط برای همگام‌سازی است
    }

    private fun exitNow() {
        runOnUiThread {
            finishAffinity()
            // ضربه‌ی امنیتی: بعضی گوشی‌ها بعد از finishAffinity باقی می‌مانند
            mainHandler.postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 250)
        }
    }

    // ---------- چرخه Activity ----------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        gameView.stop()
    }

    override fun onResume() {
        super.onResume()
        gameView.start()
        hideSystemUI()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // دوبار پشت‌سرهم در خانه منو = خروج مستقیم
        if (gameView.ui.menuOpen && exitArmed) {
            finishAffinity()
            return
        }
        // بقیه تصمیم‌ها با منطق UI (پورت handleBackButton):
        // پنجره‌های باز ← بستن | در بازی ← منو | زیرصفحه منو ← خانه منو | خانه منو ← تأیید خروج
        gameView.ui.handleBackButton()
    }
}
