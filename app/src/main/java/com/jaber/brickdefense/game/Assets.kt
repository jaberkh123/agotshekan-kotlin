package com.jaber.brickdefense.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * پورت assets.js — بارگذاری تصاویر بازی از assets/img
 * (همان اسپرایت‌های نسخه اصلی؛ در نبود تصویر، رسم برداری جانشین می‌شود)
 */
object Assets {
    val loaded: Boolean get() = map.isNotEmpty()
    private val map = HashMap<String, Bitmap>()

    private val NAMES = listOf(
        // حالت‌های آسیب دشمنان: ۱ سالم، ۲ کمی آسیب، ۳ آسیب جدی
        "ston1", "ston2", "ston3",
        "jele1", "jele2", "jele3",
        "iron1", "iron2", "iron3",
        // خانه‌های معمولی (قرمز): سه حالت آسیب
        "agor_palce1", "agor_palce2", "agor_palce3",
        // پوسته‌های دژخیم (رندوم) — bos2 برای سیاهچاله هم استفاده می‌شود
        "bos1", "bos2", "bos3", "bos4",
        // گلوله‌ها
        "tir_usual", "tir_bomb", "tir_ruh",
        // توپ‌خانه‌ها
        "top_usual", "top_bomb", "top_ruh", "top_laser"
    )

    fun load(ctx: Context) {
        if (map.isNotEmpty()) return
        for (n in NAMES) {
            try {
                ctx.assets.open("img/$n.png").use { ins ->
                    val bmp = BitmapFactory.decodeStream(ins)
                    if (bmp != null) map[n] = bmp
                }
            } catch (e: Exception) { /* تصویر نبود؟ بازی با رسم برداری ادامه می‌دهد */ }
        }
    }

    fun has(name: String): Boolean = map.containsKey(name)
    fun get(name: String): Bitmap? = map[name]
}
