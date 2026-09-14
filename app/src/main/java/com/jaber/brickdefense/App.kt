package com.jaber.brickdefense

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.adivery.sdk.Adivery

/**
 * راه‌اندازی سراسری SDK ادیوری + تبلیغ «بازگشت به برنامه» (App Open) — پورت App.java
 *
 * طبق فایل آموزشی ادیوری، تبلیغ App Open فقط وقتی نمایش داده می‌شود که کاربر
 * بیش از ۵ ثانیه از برنامه خارج بوده و برگشته باشد؛ تبلیغ «یک در میان» است
 * (بازگشت اول: بدون تبلیغ، دوم: تبلیغ، سوم: بدون، ...).
 */
class App : Application(), Application.ActivityLifecycleCallbacks {

    /** آخرین زمانی که برنامه به پس‌زمینه رفت */
    @Volatile
    private var lastPauseTime: Long = 0

    /** شمارنده بازگشت‌های پس از ۵ ثانیه غیبت — بازگشت‌های زوج تبلیغ دارند */
    private var returnCount = 0

    override fun onCreate() {
        super.onCreate()
        // مقداردهی اولیه SDK ادیوری با شناسه اپلیکیشن
        Adivery.configure(this, Ads.APP_ID)
        Adivery.setLoggingEnabled(true) // برای عیب‌یابی راحت‌تر در Logcat
        registerActivityLifecycleCallbacks(this)
        // تا اولین onResume بلافاصله بعد از لانچ، «بازگشت» حساب نشود
        lastPauseTime = System.currentTimeMillis()
    }

    override fun onActivityResumed(activity: Activity) {
        val awayMillis = System.currentTimeMillis() - lastPauseTime
        // فقط بازگشت واقعی بعد از بیش از ۵ ثانیه خارج‌بودن از برنامه
        if (awayMillis > 5000L) {
            returnCount++
            // یک در میان: فقط بازگشت‌های زوج تبلیغ دارند
            if (returnCount % 2 == 0 && Adivery.isLoaded(Ads.APP_OPEN_ID)) {
                Adivery.showAppOpenAd(activity, Ads.APP_OPEN_ID)
            }
            // ادیوری بعد از هر نمایش، تبلیغ بعدی را خودش آماده می‌کند
        }
    }

    override fun onActivityPaused(activity: Activity) {
        lastPauseTime = System.currentTimeMillis()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
