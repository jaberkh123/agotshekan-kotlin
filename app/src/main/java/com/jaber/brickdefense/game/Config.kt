package com.jaber.brickdefense.game

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * پورت کامل config.js — همه‌ی ثابت‌های بازی «آجرشکن — Brick Defense»
 * مقادیر عیناً با نسخه اصلی یکسان‌اند تا گیم‌پلی تغییر نکند.
 */
object Cfg {
    const val GRID_COLS = 8
    const val GRID_ROWS = 12

    // ساختار موج‌ها: ۱ و ۲ ← ۱۱ ترن | ۳ ← ۱۰ | ۴ تا ۱۰ ← ۲۰ | از ۱۱ هر ۱۰ مرحله +۱۰ (سقف ۶۰)
    const val WAVES_FIRST = 11
    const val WAVES_FIRST_LAST = 2
    const val WAVES_EARLY = 10
    const val WAVES_EARLY_LAST = 3
    const val WAVES_BASE = 20
    const val WAVES_STEP = 10
    const val WAVES_CAP = 60
    const val MAX_STAGE = 100
    const val STAGE_HP_RAMP = 1.08
    const val START_GOLD = 30
    const val DEFENSE_HP = 10
    const val MAX_BALL_TIME = 8.0
    const val NORMAL_TIMER_START = 30.0
    const val NORMAL_TIMER_ADD = 15.0
    const val NORMAL_TIMER_PER_LVL = 8.0
    const val NORMAL_TIMER_CAP = 240.0
    const val ELECTRIC_CHAIN_BIG = 6
    const val TURN_FIRE_CAP = 12.0
    const val SPAWN_MIN = 3
    const val SPAWN_MAX = 8
    const val HP_BASE = 10.0
    const val HP_PER_TURN = 7.0
    const val BOSS_HP_RAMP = 0.3
    const val BOSS_GOLD_BASE = 60
    const val BOSS_GOLD_PER = 10
    const val BOSS_HEARTS = 2
    const val COMBO_GOLD_PER = 2
    const val COMBO_MAX = 10
    const val GHOST_KILL_PER = 1
    const val KILL_GOLD_DIV = 3
    const val INTRO_TURNS = 3

    // ورود تدریجی خانه‌های خاص داخل هر مرحله
    val HOUSE_MIN_TURN: Map<String, Int> = mapOf(
        "jelly" to 4,
        "stone" to 5,
        "healer" to 7
    )

    // پنجره راهنمای اولین ظاهر هر خانه خاص (عکس + توضیح)
    data class Guide(val img: String, val title: String, val text: String)

    val HOUSES_GUIDE: Map<String, Guide> = mapOf(
        "jelly" to Guide(
            "img/jele1.png", "خانه ژله‌ای 🟣",
            "• اولین ضربه را نصف جذب می‌کند\n" +
                "• کاملاً مصون از توپ انفجاری (نه مستقیم، نه پاشش)\n" +
                "• توپ برقی به آن نصف آسیب می‌زند\n" +
                "• هیچ‌وقت نفرین نمی‌شود\n" +
                "• برای شکستنش: توپ معمولی یا روحی"
        ),
        "stone" to Guide(
            "img/ston1.png", "خانه سنگی ⚪",
            "• بسیار مقاوم است؛ توپ برق در اولین برخورد می‌ایستد\n" +
                "• توپ بمب به آن ۲ برابر آسیب می‌زند\n" +
                "• هنگام نشانه‌گیری با انفجاری، آلارم قرمز می‌گیرد\n" +
                "• زنجیره‌های بزرگ برقی (بیش از ۶ خانه) هم آلارم قرمز دارند\n" +
                "• برای شکستنش: توپ انفجاری"
        ),
        "healer" to Guide(
            "img/ston1.png", "درمانگر 🟢",
            "• هر ترن دشمنان کناری (چپ و راست خودش) را شفا می‌دهد\n" +
                "• اولویت اول تو برای نابودی باید او باشد!"
        ),
        "splitter" to Guide(
            "img/ston1.png", "شکافتی 🔷",
            "• با نابودی به دو ذرّه‌ی کوچک تقسیم می‌شود\n" +
                "• ذرّه‌ها در خانه‌های کناری خودش ظاهر می‌شوند\n" +
                "• جای خالی کنارش را قبل از کشتن در نظر بگیر!"
        ),
        "iron" to Guide(
            "img/iron1.png", "خانه آهنی 🔵",
            "• بسیار مقاوم است؛ عبورش از خط دفاع ۲ جان کم می‌کند\n" +
                "• گلوله‌ی روحی را می‌بلعد و محو می‌شود\n" +
                "• برخورد مستقیم توپ برقی = برق قرمز فوری به همه‌ی خانه‌های صفحه"
        ),
        "hole" to Guide(
            "img/bos2.png", "سیاهچاله 🕳",
            "• همیشه در حال چرخش است\n" +
                "• توپ‌های معمولی با برخورد محو می‌شوند (بدون انعکاس و آسیب)\n" +
                "• برای شکستنش: توپ انفجاری، روحی یا برقی"
        )
    )

    /** تعریف یک توپ جنگی (پورت BALLS در config.js) */
    data class BallDef(
        val key: String,
        val name: String,
        val icon: String,
        val color: String,
        val baseDamage: Double,
        val dmgPerLvl: Double = 0.0,
        val baseCount: Int = 1,
        val countPerLvl: Int = 0,
        val baseCost: Int,
        // معمولی
        val sproutEvery: Int = 5,
        val sproutEveryChild: Int = 15,
        val sproutMax: Int = 3,
        val sproutColor: String = "#69f0ae",
        // انفجاری
        val baseRadius: Double = 0.0,
        val radiusPerLvl: Double = 0.0,
        val baseSplash: Double = 0.0,
        val splashPerLvl: Double = 0.0,
        val extraBallBase: Double = 0.0,
        val stoneMul: Double = 1.0,
        val executionerChance: Double = 0.0,
        val executionerDmg: Double = 0.0,
        // روحی
        val baseDuration: Double = 0.0,
        val durationPerLvl: Double = 0.0,
        val ghostPerKill: Int = 1,
        val curseChance: Double = 0.0,
        val curseMul: Double = 1.0,
        val curseNormalMul: Double = 1.0,
        // برقی
        val jumpGap: Double = 0.0,
        val jumpGain: Double = 0.0,
        val jellyMul: Double = 1.0
    )

    val BALLS: Map<String, BallDef> = mapOf(
        "normal" to BallDef(
            key = "normal", name = "معمولی", icon = "⚪", color = "#4fc3f7",
            baseDamage = 2.0, dmgPerLvl = 0.0, baseCount = 2, countPerLvl = 2, baseCost = 50,
            sproutEvery = 5, sproutEveryChild = 15, sproutMax = 3, sproutColor = "#69f0ae"
        ),
        "explosive" to BallDef(
            key = "explosive", name = "انفجاری", icon = "💥", color = "#ff7043",
            baseDamage = 23.0, dmgPerLvl = 4.0, baseCost = 60,
            baseRadius = 3.4, radiusPerLvl = 0.32, baseSplash = 0.6, splashPerLvl = 0.03,
            baseCount = 1, extraBallBase = 0.4, stoneMul = 2.0,
            executionerChance = 0.2, executionerDmg = 0.05
        ),
        "ghost" to BallDef(
            key = "ghost", name = "روحی", icon = "👻", color = "#b39ddb",
            baseDamage = 7.0, dmgPerLvl = 2.0, baseDuration = 2.6, durationPerLvl = 0.0,
            baseCount = 1, countPerLvl = 1, ghostPerKill = 1, baseCost = 55,
            curseChance = 0.2, curseMul = 3.0, curseNormalMul = 10.0
        ),
        "electric" to BallDef(
            key = "electric", name = "برقی", icon = "⚡", color = "#40c4ff",
            baseDamage = 27.0, dmgPerLvl = 10.0, baseCost = 80,
            jumpGap = 0.1, jumpGain = 1.0 / 2.0, jellyMul = 0.5
        )
    )

    val BALL_KEYS = listOf("normal", "explosive", "ghost", "electric")

    const val HOLE_MIN_STAGE = 20
    const val HOLE_CHANCE = 0.05
    const val ENDLESS_BLOCK = 10

    /** تعریف یک نوع دشمن (پورت ENEMIES در config.js) */
    data class EnemyDef(
        val key: String,
        val name: String,
        val color: String,
        val hpMul: Double,
        val gold: Int,
        val minStage: Int,
        val weight: Int,
        val dmg: Int,
        val soft: Boolean = false,
        val crackAt: Double = 0.0,
        val heal: Double = 0.0,
        val split: Boolean = false,
        val hole: Boolean = false,
        val boss: Boolean = false,
        val shieldEvery: Int = 0,
        val shieldTime: Double = 0.0,
        val desc: String = ""
    )

    val ENEMIES: Map<String, EnemyDef> = mapOf(
        "normal" to EnemyDef("normal", "آجر", "#ef5350", 1.1, 5, 1, 10, 1),
        "jelly" to EnemyDef(
            "jelly", "ژله‌ای", "#ab47bc", 2.2, 8, 1, 6, 1, soft = true,
            desc = "• ضربه‌ی اول را نیمه جذب می‌کند\n• مصون از بمب و نفرین\n• توپ برقی به آن نصف آسیب می‌زند"
        ),
        "stone" to EnemyDef(
            "stone", "سنگی", "#90a4ae", 5.5, 14, 1, 4, 1, crackAt = 0.5,
            desc = "• مقاوم\n• توپ برق از آن عبور نمی‌کند\n• با توپ انفجاری آلارم قرمز می‌گیرد"
        ),
        "healer" to EnemyDef(
            "healer", "درمانگر", "#66bb6a", 2.75, 16, 4, 3, 1, heal = 0.2,
            desc = "• هر ترن دشمنان کناری را شفا می‌دهد\n• اولین هدف تو باشد!"
        ),
        "iron" to EnemyDef(
            "iron", "آهنی", "#607d8b", 8.8, 20, 10, 3, 2,
            desc = "• بسیار مقاوم\n• عبورش = ۲ ❤️ کم می‌شود\n• گلوله‌ی روحی را می‌بلعد\n• با توپ برق، واکنش سراسری می‌دهد"
        ),
        "splitter" to EnemyDef(
            "splitter", "شکافتی", "#5c6bc0", 3.3, 10, 6, 3, 1, split = true,
            desc = "• با مرگ به دو تکه‌ی کوچک تقسیم می‌شود\n• تکه‌ها در خانه‌های کناری ظاهر می‌شوند"
        ),
        "hole" to EnemyDef(
            "hole", "سیاهچاله", "#3e2765", 3.5, 18, 99, 0, 2, hole = true,
            desc = "• در حال چرخش\n• توپ‌های معمولی را می‌بلعد (محو می‌شوند)\n• از مرحله ۲۰ با شانس ۵٪ می‌آید"
        ),
        "mini" to EnemyDef("mini", "ذرّه", "#7986cb", 0.4, 3, 99, 0, 1),
        "boss" to EnemyDef(
            "boss", "دژخیم", "#6a1b9a", 8.8, 60, 99, 0, 5, boss = true,
            shieldEvery = 3, shieldTime = 0.9,
            desc = "• رئیس بازی؛ هر ۱۰ ترن یک‌بار می‌آید\n• هر ۳ ضربه سپر می‌شود\n• عبورش = ۵ ❤️ کم می‌شود\n• کشتنش = طلا + ۲ جان"
        )
    )
}

/** تعداد ترن هر مرحله */
fun turnsPerStage(stage: Int): Int {
    if (stage <= Cfg.WAVES_FIRST_LAST) return Cfg.WAVES_FIRST
    if (stage <= Cfg.WAVES_EARLY_LAST) return Cfg.WAVES_EARLY
    return min(Cfg.WAVES_CAP, Cfg.WAVES_BASE + floor((stage - 1) / 10.0).toInt() * Cfg.WAVES_STEP)
}

/** دژخیم هر ۱۰ ترن یک‌بار می‌آید: ترن ۱۰ و ۲۰ و ۳۰ و... تا آخرین ترن مرحله */
fun bossTurns(stage: Int): List<Int> {
    val cap = turnsPerStage(stage)
    val out = mutableListOf<Int>()
    var t = 10
    while (t <= cap) {
        out.add(t)
        t += 10
    }
    return out
}

/** اعداد انگلیسی (مطابق entities.js) */
fun faNum(n: Int): String = n.toString()
fun faNum(n: Double): String {
    val r = (n * 10).roundToInt() / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

fun clamp(v: Double, a: Double, b: Double): Double = if (v < a) a else if (v > b) b else v
fun clamp(v: Int, a: Int, b: Int): Int = if (v < a) a else if (v > b) b else v

/**
 * پورت STAGE_PROGRESS / SETTINGS / رکورد / راهنمای دیده‌شده‌ها —
 * در نسخه اصلی localStorage بود؛ اینجا SharedPreferences.
 */
object Prefs {
    private const val NAME = "bd_prefs"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    // ----- پیشرفت مراحل (bd_stages) -----
    var unlockedStage: Int
        get() = sp.getInt("bd_unlocked", 1)
        set(v) = sp.edit().putInt("bd_unlocked", v).apply()

    var currentStage: Int
        get() = sp.getInt("bd_current", 1)
        set(v) = sp.edit().putInt("bd_current", v).apply()

    // ----- تنظیمات (bd_settings) -----
    var speedMul: Double
        get() = sp.getFloat("bd_speed", 1f).toDouble()
        set(v) = sp.edit().putFloat("bd_speed", v.toFloat()).apply()

    var showTrajectory: Boolean
        get() = sp.getBoolean("bd_traj", true)
        set(v) = sp.edit().putBoolean("bd_traj", v).apply()

    var snd: Boolean
        get() = sp.getBoolean("bd_snd", true)
        set(v) = sp.edit().putBoolean("bd_snd", v).apply()

    // ----- رکورد (bd_best) -----
    var bestStage: Int
        get() = sp.getInt("bd_best_stage", 0)
        set(v) = sp.edit().putInt("bd_best_stage", v).apply()

    var bestTurn: Int
        get() = sp.getInt("bd_best_turn", 0)
        set(v) = sp.edit().putInt("bd_best_turn", v).apply()

    // ----- راهنمای خانه‌های دیده‌شده (bd_houses_seen) -----
    fun houseSeen(type: String): Boolean = sp.getBoolean("bd_seen_$type", false)
    fun markHouseSeen(type: String) = sp.edit().putBoolean("bd_seen_$type", true).apply()
}
