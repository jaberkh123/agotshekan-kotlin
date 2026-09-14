package com.jaber.brickdefense.game

import kotlin.math.cos
import kotlin.math.sin

/** پورت Enemy از entities.js */
class Enemy(type: String, col: Int, row: Int, hpBase: Double) {
    val def: Cfg.EnemyDef = Cfg.ENEMIES[type]!!
    val type: String = type
    var col: Int = col
    var row: Int = row

    // دژخیم: بلوک ۲×۲
    val size: Int = if (def.boss) 2 else 1
    val maxHp: Int = Math.round(hpBase * def.hpMul).toInt()
    var hp: Int = maxHp
    var yOff: Double = -1.0          // از بالا اسلاید می‌شود
    var hitFlash: Double = 0.0
    var dead: Boolean = false

    // ژله‌ای: اولین ضربه نصف جذب می‌شود
    var absorbed: Boolean = false
    // سنگی: ترک‌های ظاهری
    var crackStage: Int = 0
    // نفرین روحی: همه ضربه‌ها ۳ برابر (توپ معمولی ۱۰ برابر) — ژله‌ای هرگز نفرین نمی‌شود
    var cursed: Boolean = false

    // دژخیم: چرخه سپر (هر N ضربه یک‌بار در برابر آسیب مصون است)
    var shield: Boolean = false
    var shieldLeft: Double = 0.0
    var hitsToShield: Int = def.shieldEvery

    // سیاهچاله: زاویه اولیه چرخش
    var rot: Double = Math.random() * Math.PI * 2

    // پوسته رندوم دژخیم (bos1..bos4)
    var skin: String = ""

    init {
        if (def.boss) hitsToShield = def.shieldEvery
    }
}

/** آمار یک توپ بر اساس سطح آن (خروجی statsFor) */
class BallStats {
    var damage: Double = 0.0
    var count: Int = 1
    var radius: Double = 0.0
    var splash: Double = 0.0
    var extraChance: Double = 0.0
    var extraRolls: Int = 1
    var duration: Double = -1.0   // -1 یعنی بی‌نهایت
    var timerStart: Double = 0.0
    var jumpGap: Double = 0.0
    var sprouted: Boolean = false
    var sproutLeft: Int = -1      // -1 یعنی مقدار پیش‌فرض
}

/** پورت Ball از entities.js */
class Ball(
    val type: String,
    var x: Double, var y: Double,
    angle: Double,
    val stats: BallStats,
    ts: Double
) {
    var vx: Double
    var vy: Double
    val radius: Double = ts * 0.16
    val duration: Double = if (stats.duration >= 0) stats.duration else Double.POSITIVE_INFINITY
    var time: Double = 0.0
    val hitSet = HashSet<Enemy>()   // برای توپ روحی: هر دشمن فقط یک بار در هر عبور
    var dead: Boolean = false
    val trail = ArrayList<FloatArray>()  // {x, y}
    val trailMax: Int = 7
    var delay: Double = 0.0         // تأخیر شلیک (پشت سر هم)

    // برقی: بدون انعکاس؛ مسیر پرتو + دشمنی که از رویش عبور می‌کند
    val laserPath = ArrayList<FloatArray>()
    var ignore: Enemy? = null
    var ignoreTime: Double = 0.0

    // معمولی: شمارنده برخورد برای جوانه‌زنی گلوله سبز
    var bounces: Int = 0
    var sproutColor: Boolean = false

    // جوانه‌های سبز هم می‌توانند جوانه بزنند (با ۱۵ ضربه و تا ۳ بار)
    var sproutLeft: Int =
        if (stats.sproutLeft >= 0) stats.sproutLeft else Cfg.BALLS["normal"]!!.sproutMax

    init {
        val speed = ts * 13.5 * Prefs.speedMul
        vx = cos(angle) * speed
        vy = sin(angle) * speed
        sproutColor = stats.sprouted
    }
}

/** افکت‌های بصری (boom/dmg/gold/...) — معادل اشیاء addEffect در game.js */
class Fx {
    var kind: String = ""
    var x: Double = 0.0; var y: Double = 0.0
    var x1: Double = 0.0; var y1: Double = 0.0
    var x2: Double = 0.0; var y2: Double = 0.0
    var t: Double = 0.0
    var dur: Double = 0.3
    var color: String = "#fff"
    var text: String = ""
    var r: Double = 0.0
    var thick: Double = 1.0
    var pts: List<FloatArray> = emptyList()
    var jag: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
}

/** یک پرش زنجیره برق در صف اجرا */
class ChainJump(val enemy: Enemy, val from: Enemy, val dmg: Int, var t: Double)

/** خروجی شبیه‌سازی مسیر یک توپ (خط‌چین نشانه‌گیری) */
class SimPath(
    val key: String,
    val pts: List<FloatArray>,
    val hits: List<FloatArray>,
    val explosion: FloatArray?,  // {x, y, r}
    val chains: List<FloatArray>
)
