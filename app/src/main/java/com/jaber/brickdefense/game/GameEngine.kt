package com.jaber.brickdefense.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** مستطیل با مختصات اعشاری (خروجی enemyRect و...) */
class RectD(val x: Double, val y: Double, val w: Double, val h: Double)

/**
 * پورت کامل منطق بازی از game.js — بدون هیچ وابستگی به اندروید
 * (فقط رندر جدا شده در GameRenderer است)
 */
class GameEngine {

    var ts: Double = 40.0
    var ox: Double = 0.0
    var oy: Double = 0.0
    var W: Double = 0.0
    var H: Double = 0.0
    var topInset: Double = 0.0
    var bottomInset: Double = 0.0

    var mode: String = "campaign"      // campaign | endless
    var stage: Int = 1
    var gold: Int = Cfg.START_GOLD
    var defenseHp: Int = Cfg.DEFENSE_HP
    var turn: Int = 1                  // شماره مطلق ترن (برای رکورد)
    var turnInStage: Int = 1           // ترن جاری داخل مرحله
    var state: String = "aim"          // aim | fire | step | gameover | victory | stageclear
    var stageClear: Boolean = false
    val enemies = ArrayList<Enemy>()
    val activeBalls = ArrayList<Ball>()
    private val fireGroups = ArrayDeque<List<Ball>>()
    private var fireDelay: Double = 0.0
    private var fireElapsed: Double = 0.0
    val order = mutableListOf("normal", "explosive", "ghost", "electric") // ترتیب حمله
    val effects = ArrayList<Fx>()
    var aimKey: String? = null         // توپی که همین الان جهتش تغییر می‌کند

    var combo: Int = 0
    var shake: Double = 0.0
    var time: Double = 0.0
    private var lastDamageTime: Double = -99.0
    var ghostStock: Int = 0            // روح‌های ذخیره‌شده (بدون سقف)

    val chainQueue = ArrayList<ChainJump>()
    var chainCount: Int = 0

    // شمارنده بزرگ محو شدن توپ معمولی (null = غیرفعال)
    var normalBallTimer: Double? = null
    var normalTimerVisible: Boolean = false
    var normalTimerAlpha: Double = 0.0
    var normalTimerShown: Int = 0

    // دکمه «جمع کردن» کنار شمارنده — رندرر مقداردهی می‌کند
    var collectBtnRect: RectD? = null
    var collectBtnHot: Boolean = false

    val levels = HashMap<String, Int>()
    val aims = HashMap<String, Double>()

    // کش مسیرها (خط‌چین نشانه‌گیری)
    private var simCache: HashMap<String, SimPath>? = null
    var simDirty: Boolean = true

    // صف راهنمای اولین ظاهر خانه‌های خاص
    val pendingGuides = ArrayDeque<Cfg.Guide>()

    private var lastBounceSfx: Double = -1.0

    init {
        reset(Prefs.currentStage, "campaign")
    }

    // ---------- شروع مرحله ----------
    fun reset(stage: Int, mode: String) {
        this.mode = if (mode == "endless") "endless" else "campaign"
        this.stage = clamp(if (stage == 0) 1 else stage, 1, Cfg.MAX_STAGE)
        if (this.mode == "campaign") {
            Prefs.currentStage = this.stage
        }
        gold = Cfg.START_GOLD
        defenseHp = Cfg.DEFENSE_HP
        turn = 1
        turnInStage = 1
        state = "aim"
        stageClear = false
        enemies.clear()
        activeBalls.clear()
        fireGroups.clear()
        fireDelay = 0.0
        fireElapsed = 0.0
        order.clear()
        order.addAll(listOf("normal", "explosive", "ghost", "electric"))
        effects.clear()
        simCache = null
        simDirty = true
        aimKey = null
        combo = 0
        shake = 0.0
        time = 0.0
        lastDamageTime = -99.0
        ghostStock = 0
        chainQueue.clear()
        chainCount = 0
        normalBallTimer = null
        normalTimerVisible = false
        normalTimerAlpha = 0.0
        normalTimerShown = 0
        pendingGuides.clear()
        levels.clear()
        for (k in Cfg.BALL_KEYS) levels[k] = 1
        // جهت پیش‌فرض هر توپ (به رادیان؛ -PI/2 یعنی مستقیم رو به بالا)
        aims.clear()
        aims["normal"] = -PI / 2 - 0.35
        aims["explosive"] = -PI / 2
        aims["ghost"] = -PI / 2 + 0.35
        aims["electric"] = -PI / 2
        spawnWave(true)
    }

    private fun sfx(name: String, arg: Int = 3) {
        Sfx.play(name, arg)
    }

    // ---------- Layout ----------
    fun layout(w: Double, h: Double, topInset: Double, bottomInset: Double) {
        this.W = w
        this.H = h
        this.topInset = topInset
        this.bottomInset = bottomInset
        val hCanvas = h - topInset - bottomInset
        val t = min(w / Cfg.GRID_COLS, hCanvas / (Cfg.GRID_ROWS + 2.4))
        ts = t
        ox = (w - Cfg.GRID_COLS * ts) / 2
        oy = topInset + max(ts * 0.15, (hCanvas - (Cfg.GRID_ROWS + 1.8) * ts) / 2)
        simDirty = true
    }

    fun cellRect(col: Int, row: Int): RectD =
        RectD(ox + col * ts, oy + row * ts, ts, ts)

    fun enemyRect(e: Enemy): RectD {
        val r = RectD(
            ox + e.col * ts,
            oy + (e.row + e.yOff) * ts,
            e.size * ts,
            e.size * ts
        )
        return r
    }

    val defenseY: Double get() = oy + Cfg.GRID_ROWS * ts
    val cannonY: Double get() = min(defenseY + ts * 1.25, H - bottomInset - ts * 0.45)
    // وسط‌چین: مراکز ۱،۳،۵،۷ کاشی → میانگین = ۴ = دقیقاً مرکز میدان (نسخه‌ی قبلی ۰.۵+۲i بود و گروه نیم‌کاشی به چپ می‌افتاد)
    fun cannonX(i: Int): Double = ox + ts * (1 + i * 2)

    // ---------- آمار توپ‌ها بر اساس Level ----------
    fun statsFor(type: String): BallStats {
        val d = Cfg.BALLS[type]!!
        val lv = levels[type] ?: 1
        val s = BallStats()
        s.damage = d.baseDamage + (lv - 1) * d.dmgPerLvl
        when (type) {
            "normal" -> {
                s.count = d.baseCount + (lv - 1) * d.countPerLvl // هر ارتقا فقط ۲ گلوله
                s.damage = d.baseDamage                          // آسیب ثابت (هر گلوله ۲)
                s.timerStart = Cfg.NORMAL_TIMER_START + (lv - 1) * Cfg.NORMAL_TIMER_PER_LVL
            }
            "explosive" -> {
                // یکی درمیون دقیق: ارتقای فرد = قدرت (آسیب/شعاع/پاشش)، ارتقای زوج = ۱ گلوله
                val powerUps = ceil((lv - 1) / 2.0).toInt()
                s.damage = d.baseDamage + powerUps * d.dmgPerLvl
                s.radius = d.baseRadius + powerUps * d.radiusPerLvl
                s.splash = min(0.95, d.baseSplash + powerUps * d.splashPerLvl)
                s.count = d.baseCount + floor((lv - 1) / 2.0).toInt()
                s.extraChance = d.extraBallBase
                s.extraRolls = lv
            }
            "ghost" -> {
                s.duration = d.baseDuration + (lv - 1) * d.durationPerLvl
                s.count = d.baseCount + (lv - 1) * d.countPerLvl
            }
            "electric" -> {
                s.jumpGap = d.jumpGap
            }
        }
        return s
    }

    fun upgradeCost(type: String): Int {
        val base = Cfg.BALLS[type]!!.baseCost
        val lv = levels[type] ?: 1
        return ((base * 1.5.pow(lv - 1)) / 5).roundToInt() * 5
    }

    fun upgrade(type: String): Boolean {
        val cost = upgradeCost(type)
        if (gold < cost) return false
        gold -= cost
        levels[type] = (levels[type] ?: 1) + 1
        simDirty = true // آمار توپ عوض شد → مسیرها دوباره محاسبه شوند
        return true
    }

    /** هر ترن فقط ۲ توپ فعال است (ترن فرد: معمولی + انفجاری | ترن زوج: روحی + برقی) */
    fun activePairFor(): List<String> {
        val pairs = listOf(
            listOf("normal", "explosive"),
            listOf("ghost", "electric")
        )
        return if (turn % 2 == 1) pairs[0] else pairs[1] // ترن ۱ = معمولی + انفجاری
    }

    // ===== آلارم قرمز راهنما =====
    fun alarmTarget(): String? {
        if (state != "aim") return null
        val k = aimKey ?: return null
        if (!activePairFor().contains(k)) return null
        if (k == "explosive") return "stone"
        return null
    }

    // ===== آلارم زنجیره‌های بزرگ برق =====
    fun electricChainAlarm(): Set<Enemy>? {
        if (state != "aim") return null
        val k = aimKey ?: return null
        if (k != "electric" || !activePairFor().contains("electric")) return null
        val seen = HashSet<Enemy>()
        val big = HashSet<Enemy>()
        for (e in enemies) {
            if (e.dead || seen.contains(e)) continue
            val comp = connectedComponent(e)
            seen.addAll(comp)
            if (comp.size > Cfg.ELECTRIC_CHAIN_BIG) big.addAll(comp)
        }
        return big
    }

    // ---------- ترن ----------
    fun startTurn() {
        if (state != "aim") return
        state = "fire"
        combo = 0
        simCache = null
        val activePair = activePairFor()
        val caps = mapOf("normal" to 10, "explosive" to 6, "ghost" to 8, "electric" to 1)
        fireGroups.clear()
        for (key in order) {
            if (!activePair.contains(key)) continue
            val st = statsFor(key)
            val idx = Cfg.BALL_KEYS.indexOf(key)
            val balls = ArrayList<Ball>()
            if (key == "ghost") {
                // روحی: گلوله‌های خودش (۱ پایه + ۱ هر ارتقا) + همه‌ی روح‌های ذخیره‌شده (بدون سقف)
                val own = st.count
                val n = own + ghostStock
                ghostStock = max(0, ghostStock - max(0, n - own))
                for (j in 0 until n) {
                    val b = Ball(key, cannonX(idx), cannonY, aims[key]!!, st, ts)
                    b.delay = j * 0.28
                    balls.add(b)
                }
            } else {
                var n = min(caps[key] ?: 4, st.count)
                if (key == "explosive" && n > 0) {
                    // هر سطح = یک قُل مستقل ۴۰٪ برای گلوله اضافه
                    val rolls = st.extraRolls
                    for (r in 0 until rolls) {
                        if (Random.nextDouble() < st.extraChance) n++
                    }
                }
                for (j in 0 until n) {
                    val b = Ball(key, cannonX(idx), cannonY, aims[key]!!, st, ts)
                    b.delay = j * (if (key == "normal") 0.16 else 0.2) // هم‌راستا، پشت سر هم
                    balls.add(b)
                }
            }
            fireGroups.add(balls)
        }
        activeBalls.clear()
        fireDelay = 0.15
        fireElapsed = 0.0
        lastDamageTime = time
        chainQueue.clear()
        chainCount = 0
    }

    /** جابه‌جایی ترتیب حمله دو توپ */
    fun swapOrder(keyA: String, keyB: String) {
        val i = order.indexOf(keyA)
        val j = order.indexOf(keyB)
        if (i < 0 || j < 0 || i == j) return
        order[i] = keyB
        order[j] = keyA
    }

    /** تعیین جهت یک توپ + باطل‌کردن کش مسیر */
    fun setAim(key: String, angle: Double) {
        aims[key] = angle
        simDirty = true
    }

    /** پایان نوبت حمله: فقط حرکت/عبور — برخورد با مرز = گیم‌اور */
    fun stepTurn() {
        enemies.removeAll { it.dead }
        // دشمنان یک خانه پایین می‌روند
        for (e in enemies) {
            e.row += 1
            e.yOff = -1.0
        }
        // دشمنانی که از خط دفاع عبور کردند
        val crossed = enemies.filter { it.row + it.size - 1 >= Cfg.GRID_ROWS }
        if (crossed.isNotEmpty()) {
            var dmg = 0
            for (e in crossed) {
                dmg += e.def.dmg
                addEffect(Fx().apply {
                    kind = "boom"
                    x = ox + (e.col + e.size / 2.0) * ts
                    y = defenseY - ts / 2
                    t = 0.0; dur = 0.3; color = "#ef5350"
                })
            }
            defenseHp -= dmg
            addEffect(Fx().apply { kind = "hitline"; t = 0.0; dur = 0.5 })
            shake = max(shake, 0.7)
            sfx("breach")
            enemies.removeAll { it.row + it.size - 1 >= Cfg.GRID_ROWS }
        }
        // درمانگرها بعد از حرکت دشمنان شفا می‌دهند
        applyHeals()
        if (defenseHp <= 0) {
            defenseHp = 0
            state = "gameover"
            sfx("gameover")
            return
        }
        if (mode == "endless") {
            // مود بی‌نهایت: هر ENDLESS_BLOCK ترن یک «دور»
            turnInStage = if (turnInStage >= Cfg.ENDLESS_BLOCK) 1 else turnInStage + 1
            if (turnInStage == 1) stage++ // دور بعد = مرحله مجازی بعد (رمپ سختی)
            turn++
        } else {
            val cap = turnsPerStage(stage)
            val wasFinalTurn = turnInStage >= cap
            turnInStage = if (wasFinalTurn) cap else turnInStage + 1
            turn++
            // مرحله فقط وقتی تمام می‌شود که دژخیم نهایی مرده باشد
            val bossAlive = enemies.any { it.def.boss && !it.dead }
            if (wasFinalTurn && !bossAlive) stageClear = true
        }
        combo = 0
        simDirty = true
        state = "step"
    }

    /** پایان مرحله: پنجره پایان مرحله — برخورد = گیم‌اور */
    fun clearStage() {
        stageClear = false
        gold += 25
        sfx("stageclear")
        // مرحله بعد باز می‌شود (پیشرفت ذخیره می‌شود)
        val next = min(stage + 1, Cfg.MAX_STAGE)
        if (next > Prefs.unlockedStage) {
            Prefs.unlockedStage = next
        }
        state = "stageclear"
    }

    // ---------- افکت و آسیب ----------
    fun addEffect(fx: Fx) {
        effects.add(fx)
    }

    fun damageEnemy(e: Enemy, dmgIn: Double, fxX: Double?, fxY: Double?, src: String?) {
        if (e.dead) return
        var dmg = dmgIn
        // دژخیم: هر N ضربه یک‌بار سپر می‌شود (آسیب صفر)
        if (e.def.boss) {
            if (e.shield) {
                if (e.shieldLeft <= 0) e.shieldLeft = 0.001
                val c = enemyRect(e)
                addEffect(Fx().apply {
                    kind = "shield"; x = c.x + c.w / 2; y = c.y + c.h / 2
                    t = 0.0; dur = 0.35
                })
                sfx("shield")
                return
            }
            e.hitsToShield--
            if (e.hitsToShield <= 0) {
                e.shield = true
                e.shieldLeft = e.def.shieldTime
                e.hitsToShield = e.def.shieldEvery
            }
        }
        // توپ برقی: به خانه‌های ژله‌ای نصف آسیب می‌زند
        if (src == "electric" && e.def.soft) {
            dmg = max(1.0, kotlin.math.round(dmg * (Cfg.BALLS["electric"]!!.jellyMul)))
        }
        // نفرین روحی: خانه نفرین‌شده به همه‌ی ضربه‌ها ۳ برابر آسیب می‌بیند (توپ معمولی: ۱۰ برابر)
        var cursedHit = false
        if (e.cursed && !e.def.soft) {
            val mul = if (src == "normal")
                Cfg.BALLS["ghost"]!!.curseNormalMul
            else
                Cfg.BALLS["ghost"]!!.curseMul
            dmg = kotlin.math.round(dmg * mul)
            cursedHit = true
        }
        // ژله‌ای: اولین ضربه نصف جذب می‌شود
        if (e.def.soft && !e.absorbed) {
            e.absorbed = true
            dmg = ceil(dmg / 2)
            if (fxX != null) {
                addEffect(Fx().apply {
                    kind = "dmg"; x = fxX; y = fxY!!
                    text = "نصف شد"; t = 0.0; dur = 0.6; color = "#ce93d8"
                })
            }
        }
        e.hp -= dmg.roundToInt()
        e.hitFlash = 1.0
        // نمایش عدد آسیب — بعد از جذب ژله‌ای absorbed=true است؛
        // پس شرط JS دقیقاً معادل fxX !== undefined می‌شود
        if (fxX != null) {
            addEffect(Fx().apply {
                kind = "dmg"; x = fxX; y = fxY!!
                text = faNum(dmg.roundToInt())
                t = 0.0; dur = 0.6; color = if (cursedHit) "#ce93d8" else "#fff"
            })
            if (cursedHit) {
                addEffect(Fx().apply {
                    kind = "dmg"; x = fxX; y = fxY!! - ts * 0.42
                    text = if (src == "normal") "نفرین ×۱۰" else "نفرین ×۳"
                    t = 0.0; dur = 0.8; color = "#b39ddb"
                })
            }
        }
        // ترک خوردن سنگی
        if (e.def.crackAt > 0 && e.crackStage == 0 && e.hp > 0 && e.hp <= e.maxHp * e.def.crackAt) {
            e.crackStage = 1
        }
        if (e.hp <= 0) killEnemy(e, src)
    }

    fun killEnemy(e: Enemy, src: String?) {
        if (e.dead) return
        e.dead = true
        // روحی: با هر کشتار روحی ۱ روح ذخیره می‌شود (بدون سقف)
        if (src == "ghost") {
            ghostStock++
        }
        // دژخیم: طلا و جان اضافه، بدون کمبو
        if (e.def.boss) {
            val g = max(1, (Cfg.BOSS_GOLD_BASE + (stage - 1) * Cfg.BOSS_GOLD_PER) / Cfg.KILL_GOLD_DIV)
            gold += g
            defenseHp = min(Cfg.DEFENSE_HP, defenseHp + Cfg.BOSS_HEARTS)
            val c = enemyRect(e)
            addEffect(Fx().apply {
                kind = "boom"; x = c.x + c.w / 2; y = c.y + c.h / 2
                t = 0.0; dur = 0.5; color = "#e1bee7"
            })
            addEffect(Fx().apply {
                kind = "bossdown"
                text = "دژخیم نابود شد! 🎁+" + faNum(g) + " | +" + faNum(Cfg.BOSS_HEARTS) + "❤️"
                t = 0.0; dur = 2.0
            })
            shake = max(shake, 1.2)
            sfx("bosskill")
            return
        }
        // کمبو: هر کشتار پشت‌سرهم طلا بیشتر — کشتار روحی هیچ کمبویی ندارد
        val ghostKill = src == "ghost"
        if (!ghostKill) combo++
        val comboBonus = if (ghostKill) 0 else min(combo, Cfg.COMBO_MAX) * Cfg.COMBO_GOLD_PER
        val g = max(1, (e.def.gold + stage + comboBonus) / Cfg.KILL_GOLD_DIV)
        gold += g
        val c = enemyRect(e)
        addEffect(Fx().apply {
            kind = "boom"; x = c.x + c.w / 2; y = c.y + c.h / 2
            t = 0.0; dur = 0.3; color = e.def.color
        })
        addEffect(Fx().apply {
            kind = "gold"; x = c.x + c.w / 2; y = c.y + c.h / 2
            text = "+" + faNum(g); t = 0.0; dur = 0.9
        })
        shake = max(shake, 0.12)
        if (!ghostKill && combo >= 3) {
            addEffect(Fx().apply {
                kind = "combo"; x = c.x + c.w / 2; y = c.y - ts * 0.35
                text = "کمبو ×" + faNum(combo); t = 0.0; dur = 0.8
            })
            sfx("combo", combo)
        } else {
            sfx("kill")
        }
        // شکافتی: با مرگ به دو ذرّه تقسیم می‌شود
        if (e.def.split) {
            for (dc in intArrayOf(-1, 1)) {
                val nc = e.col + dc
                if (nc < 0 || nc >= Cfg.GRID_COLS) continue
                if (enemies.any { o -> !o.dead && o.col == nc && o.row == e.row }) continue
                val mini = Enemy("mini", nc, e.row, Cfg.HP_BASE + (turn - 1) * Cfg.HP_PER_TURN)
                mini.yOff = 0.0 // ذرّه در جای خودش ظاهر می‌شود
                enemies.add(mini)
                val cm = enemyRect(mini)
                addEffect(Fx().apply {
                    kind = "boom"; x = cm.x + cm.w / 2; y = cm.y + cm.h / 2
                    t = 0.0; dur = 0.25; color = mini.def.color
                })
            }
        }
    }

    // ---------- حلقه به‌روزرسانی ----------
    fun update(dt: Double) {
        time += dt
        if (shake > 0) shake = max(0.0, shake - dt * 2.2)
        for (e in enemies) {
            if (e.hitFlash > 0) e.hitFlash = max(0.0, e.hitFlash - dt * 4)
            // چرخش آرام سیاهچاله
            if (e.def.hole) e.rot += dt * 1.4
            // سپر دژخیم
            if (e.def.boss && e.shield) {
                e.shieldLeft -= dt
                if (e.shieldLeft <= 0) e.shield = false
            }
        }
        for (fx in effects) fx.t += dt
        effects.removeAll { it.t >= it.dur }

        // پرش‌های معوق زنجیره برق: هر جامپ با فاصله زمانی مشخص اجرا می‌شود
        if (chainQueue.isNotEmpty()) {
            for (z in chainQueue) z.t -= dt
            val due = chainQueue.filter { it.t <= 0 }
            chainQueue.removeAll { it.t <= 0 }
            for (z in due) {
                if (z.enemy.dead) continue // خانه قبلاً نابود شده
                val cf = enemyRect(z.from)
                val ct = enemyRect(z.enemy)
                addEffect(Fx().apply {
                    kind = "zap"
                    x1 = cf.x + cf.w / 2; y1 = cf.y + cf.h / 2
                    x2 = ct.x + ct.w / 2; y2 = ct.y + ct.h / 2
                    color = Cfg.BALLS["electric"]!!.color
                    thick = 1.0; t = 0.0; dur = 0.28
                    jag = doubleArrayOf(
                        Random.nextDouble() - 0.5,
                        Random.nextDouble() - 0.5,
                        Random.nextDouble() - 0.5
                    )
                })
                damageEnemy(z.enemy, z.dmg.toDouble(), ct.x + ct.w / 2, ct.y, "electric")
                chainCount++
                sfx("zap")
            }
        }

        if (state == "fire") {
            updateFire(dt)
        } else if (state == "step") {
            var done = true
            for (e in enemies) {
                if (e.yOff < 0) {
                    e.yOff = min(0.0, e.yOff + dt * 5)
                    done = false
                }
            }
            if (done) afterStep()
        }
    }

    /** شلیک ترتیبی: هر گروه (توپ‌خانه) تمام می‌شود، بعد گروه بعدی */
    private fun updateFire(dt: Double) {
        if (activeBalls.isNotEmpty()) {
            fireElapsed += dt
            val allNormal = activeBalls.all { it.type == "normal" }
            if (allNormal) {
                // ===== شمارنده بزرگ محو شدن توپ معمولی =====
                if (normalBallTimer == null) normalBallTimer = statsFor("normal").timerStart
                normalBallTimer = normalBallTimer!! - dt
                normalTimerVisible = true
                if (normalBallTimer!! <= 0) {
                    expireNormalBalls()
                    return
                }
            } else if (fireElapsed > Cfg.TURN_FIRE_CAP) {
                // ضد گیرافتادن بقیه توپ‌ها (فقط ایمنی)
                activeBalls.clear()
                fireDelay = 0.3
                return
            }
            updateBalls(dt)
            // اگر همه‌ی گلوله‌های معمولی قبل از صفر شدن زمان خارج شدند
            if (activeBalls.none { it.type == "normal" }) normalTimerVisible = false
            if (activeBalls.isEmpty()) fireDelay = 0.3 // مکث کوتاه بین نوبت‌ها
        } else if (fireGroups.isNotEmpty()) {
            fireDelay -= dt
            if (fireDelay <= 0) {
                activeBalls.clear()
                activeBalls.addAll(fireGroups.removeFirst())
                if (activeBalls.isNotEmpty()) {
                    fireElapsed = 0.0
                    sfx("shot")
                    if (activeBalls.all { it.type == "normal" }) {
                        val startT = statsFor("normal").timerStart
                        val cap = max(Cfg.NORMAL_TIMER_CAP, startT + Cfg.NORMAL_TIMER_ADD)
                        val base = if (normalBallTimer != null && normalBallTimer!! > 0)
                            normalBallTimer!! + Cfg.NORMAL_TIMER_ADD
                        else startT
                        normalBallTimer = min(cap, base)
                        normalTimerVisible = true
                    }
                }
            }
        } else {
            // پرش‌های معوق زنجیره برق باید تمام شوند تا ترن تمام شود
            if (chainQueue.isNotEmpty()) {
                fireElapsed += dt
                if (fireElapsed > Cfg.TURN_FIRE_CAP) chainQueue.clear()
                return
            }
            // پایان شلیک برقی: زنجیره بزرگ → پیام «ضربه فلا»
            if (chainCount > Cfg.ELECTRIC_CHAIN_BIG) {
                addEffect(Fx().apply {
                    kind = "fla"; t = 0.0; dur = 2.0
                    text = "⚡ ضربه فلا " + faNum(chainCount) + " برابر وارد شد!"
                })
                sfx("thunder")
            }
            chainCount = 0
            stepTurn()
        }
    }

    /** اتمام زمان شمارنده: هر چه گلوله معمولی روی صفحه است محو می‌شود */
    fun expireNormalBalls() {
        for (b in activeBalls) {
            if (b.type != "normal" || b.dead) continue
            b.dead = true
            addEffect(Fx().apply {
                kind = "boom"; x = b.x; y = b.y
                t = 0.0; dur = 0.3; color = Cfg.BALLS["normal"]!!.color
            })
        }
        activeBalls.removeAll { it.dead }
        normalBallTimer = null   // شمارنده صفر شد → از نو شروع
        normalTimerVisible = false
        fireDelay = 0.3
        sfx("vanish")
    }

    private fun updateBalls(dt: Double) {
        // گلوله‌های با تأخیر (پشت سر هم)
        for (b in activeBalls) {
            if (!b.dead && b.delay > 0) {
                b.delay -= dt
                if (b.delay <= 0) sfx("shot")
            }
        }
        val SUB = 4 // زیرگام برای جلوگیری از رد شدن از داخل دشمن
        val sdt = dt / SUB
        for (s in 0 until SUB) {
            // کپی لحظه‌ای: consumeNormalHit ممکن است حین حرکت، جوانه‌ی جدید به activeBalls اضافه کند (CME)
            for (b in activeBalls.toList()) {
                if (b.dead || b.delay > 0) continue
                moveBall(b, sdt)
            }
        }
        for (b in activeBalls) {
            if (!b.dead && b.delay <= 0) {
                b.trail.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                if (b.trail.size > b.trailMax) b.trail.removeAt(0)
            }
        }
        activeBalls.removeAll { it.dead }
        enemies.removeAll { it.dead }
    }

    /** منطق مشترک ضربه توپ معمولی: هر ۵ برخوردِ هر گلوله گلوله سبز جدید */
    private fun consumeNormalHit(b: Ball) {
        b.bounces++
        val nCfg = Cfg.BALLS["normal"]!!
        // گلوله مادر هر ۵ برخورد جوانه می‌سازد؛ جوانه‌های سبز با ۱۵ ضربه (تا ۳ بار)
        val every = if (b.stats.sprouted) nCfg.sproutEveryChild else nCfg.sproutEvery
        if (b.sproutLeft > 0 && b.bounces % every == 0) {
            b.sproutLeft--
            val st = copyStats(b.stats)
            st.sprouted = true
            st.sproutLeft = nCfg.sproutMax
            val ang = -PI / 2 + (Random.nextDouble() - 0.5) * PI * 1.7 // جهت رندوم
            val nb = Ball("normal", b.x, b.y, ang, st, ts)
            nb.sproutColor = true
            activeBalls.add(nb)
            addEffect(Fx().apply {
                kind = "boom"; x = b.x; y = b.y
                t = 0.0; dur = 0.25; color = nCfg.sproutColor
            })
            sfx("sprout")
        }
    }

    private fun copyStats(src: BallStats): BallStats {
        val s = BallStats()
        s.damage = src.damage; s.count = src.count; s.radius = src.radius
        s.splash = src.splash; s.extraChance = src.extraChance; s.extraRolls = src.extraRolls
        s.duration = src.duration; s.timerStart = src.timerStart; s.jumpGap = src.jumpGap
        s.sprouted = src.sprouted; s.sproutLeft = src.sproutLeft
        return s
    }

    private fun moveBall(b: Ball, dt: Double) {
        if (b.delay > 0) return // هنوز شلیک نشده
        b.time += dt
        // توپ معمولی: عمرش را شمارنده بزرگ قابل‌مشاهده تعیین می‌کند
        if (b.type != "normal" && (b.time > Cfg.MAX_BALL_TIME || b.time >= b.duration)) {
            b.dead = true
            return
        }
        if (b.type == "electric") {
            if (b.laserPath.isEmpty()) b.laserPath.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
            if (b.ignoreTime > 0) b.ignoreTime -= dt else b.ignore = null
        }

        b.x += b.vx * dt
        b.y += b.vy * dt

        val r = b.radius
        val left = ox + r
        val right = ox + Cfg.GRID_COLS * ts - r
        val top = oy + r
        val bottom = defenseY - r
        // دیوارها — توپ برق هیچ انعکاسی ندارد و با دیوار تمام می‌شود
        if (b.type == "electric") {
            if (b.x <= left || b.x >= right || b.y <= top) {
                killBeam(b)
                b.dead = true
                return
            }
        } else {
            if (b.x < left) {
                b.x = left; b.vx = abs(b.vx); wallBounce(b)
            } else if (b.x > right) {
                b.x = right; b.vx = -abs(b.vx); wallBounce(b)
            }
            if (b.y < top) {
                b.y = top; b.vy = abs(b.vy); wallBounce(b)
            }
            if (b.dead) return // فقط محوهای خاص (سیاهچاله)
        }
        // دیوار پایین: هر توپی که به آن رسید حذف می‌شود (بدون انعکاس)
        if (b.y > bottom && b.vy > 0) {
            if (b.type == "electric") killBeam(b)
            b.dead = true
            return
        }

        // برخورد با دشمنان — کپی لحظه‌ای: کشتن دشمن شکافتی، ذرّه‌های جدید به enemies اضافه می‌کند (CME)
        for (e in enemies.toList()) {
            if (e.dead || e === b.ignore) continue
            val c = enemyRect(e)
            val nx = clamp(b.x, c.x, c.x + c.w)
            val ny = clamp(b.y, c.y, c.y + c.h)
            val dx = b.x - nx
            val dy = b.y - ny
            if (dx * dx + dy * dy <= r * r) {
                hitEnemy(b, e, c)
                if (b.dead) return
            }
        }
    }

    private fun wallBounce(b: Ball) {
        // برخورد با دیوار در شمارش جوانه‌زنی توپ معمولی حساب می‌شود
        if (b.type == "normal") {
            consumeNormalHit(b)
            if (b.dead) return
        }
        // فقط صدا (با محدودیت نرخ)
        if (time - lastBounceSfx > 0.08) {
            sfx("bounce")
            lastBounceSfx = time
        }
    }

    /** پایان مسیر پرتو برق: افکت محو شدن امتداد جرقه */
    private fun killBeam(b: Ball) {
        b.laserPath.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
        addEffect(Fx().apply {
            kind = "laserfade"
            pts = ArrayList(b.laserPath)
            color = Cfg.BALLS["electric"]!!.color
            t = 0.0; dur = 0.3
        })
    }

    private fun afterStep() {
        // مرحله تمام شد → پنجره پایان مرحله
        if (stageClear) {
            for (e in enemies) e.yOff = 0.0
            clearStage()
            return
        }
        // شروع ترن بعدی داخل همین مرحله
        spawnWave(false)
        for (e in enemies) e.yOff = 0.0
        simDirty = true
        state = "aim"
    }

    fun spawnWave(first: Boolean) {
        val tIn = turnInStage
        val cap = if (mode == "endless") Cfg.ENDLESS_BLOCK else turnsPerStage(stage)
        val isBossTurn = if (mode == "endless") {
            tIn == Cfg.ENDLESS_BLOCK
        } else {
            bossTurns(stage).contains(tIn)
        }
        // ترن‌های تمدیدی (دژخیم نهایی هنوز زنده است پس مرحله ادامه دارد)
        val extending = mode != "endless" && tIn >= cap &&
                enemies.any { it.def.boss && !it.dead }
        // هر ترن ۳ تا ۸ خانه اضافه می‌شود (ترن پایانی = فقط دژخیم؛ مگر در تمدید)
        val count = if (tIn >= cap && !extending) 0
        else Cfg.SPAWN_MIN + Random.nextInt(Cfg.SPAWN_MAX - Cfg.SPAWN_MIN + 1)
        val s = stage
        // استخر وزن‌دار + ورود تدریجی داخل مرحله
        val pool = Cfg.ENEMIES.values.filter { e ->
            if (e.weight <= 0) return@filter false   // ذرّه/دژخیم/سیاهچاله از قاعده وزن خارج‌اند
            if (s < e.minStage) return@filter false   // گیت مرحله
            val minTurn = Cfg.HOUSE_MIN_TURN[e.key]
            if (minTurn != null && tIn < minTurn) return@filter false // گیت ترن (ورود تدریجی)
            true
        }
        // ۳ ترن اول: فقط خانه آجری
        val finalPool = if (tIn <= Cfg.INTRO_TURNS) pool.filter { it.key == "normal" } else pool
        val totalW = finalPool.sumOf { it.weight }
        val pick: () -> Cfg.EnemyDef = {
            var r = Random.nextDouble() * totalW
            var chosen = finalPool.last()
            for (e in finalPool) {
                r -= e.weight
                if (r <= 0) {
                    chosen = e
                    break
                }
            }
            chosen
        }
        // سیاهچاله: از مرحله ۲۰ در هر لاینی که وارد می‌شود ۵٪ شانس دارد
        val holeChance: () -> Cfg.EnemyDef? = {
            if (s >= Cfg.HOLE_MIN_STAGE && Random.nextDouble() < Cfg.HOLE_CHANCE)
                Cfg.ENEMIES["hole"]
            else null
        }
        // HP پایه: ۱۰ + ۷×(ترن داخل مرحله - ۱)؛ سختی هر مرحله ۸٪ مرکب بیشتر
        val hpBase = (Cfg.HP_BASE + (tIn - 1) * Cfg.HP_PER_TURN) * Cfg.STAGE_HP_RAMP.pow(s - 1)
        val taken = HashSet<Int>(enemies.filter { it.row == 0 }.map { it.col })
        var spawned = 0
        // دژخیم‌ها: هر ۱۰ ترن یک‌بار وسط ردیف بالا (بلوک ۲×۲)
        if (isBossTurn && enemies.none { it.def.boss }) {
            val col = Cfg.GRID_COLS / 2 - 1
            val bossHp = Math.round(hpBase * (Cfg.BOSS_HP_RAMP + 1).pow(s - 1)).toDouble()
            val boss = Enemy("boss", col, 0, bossHp)
            boss.skin = "bos" + (1 + Random.nextInt(4)) // پوسته رندوم از bos1 تا bos4
            boss.yOff = if (first) 0.0 else -1.0
            enemies.add(boss)
            taken.add(col)
            taken.add(col + 1) // جای دژخیم ۲ ستون است
            addEffect(Fx().apply {
                kind = "bosswarn"; text = "⚠️ دژخیم می‌آید!"; t = 0.0; dur = 2.0
            })
            sfx("bosshorn")
        }
        // ستون‌های آزاد ردیف اول را یک‌بار می‌سازیم و بُر می‌زنیم
        val freeCols = (0 until Cfg.GRID_COLS).filter { !taken.contains(it) }.toMutableList()
        freeCols.shuffle()
        var i = 0
        while (i < count && spawned < count && spawned < freeCols.size) {
            val col = freeCols[i]
            taken.add(col)
            val def = holeChance() ?: pick()
            val ne = Enemy(def.key, col, 0, hpBase)
            enemies.add(ne)
            queueFirstSeenGuide(ne)
            spawned++
            i++
        }
        // موج دوم: ۳ تا ۸ خانه در نیمه بالایی (ردیف‌های ۱ تا ۵)
        if (tIn < cap || extending) {
            val halfCount = Cfg.SPAWN_MIN + Random.nextInt(Cfg.SPAWN_MAX - Cfg.SPAWN_MIN + 1)
            val occupied = HashSet<String>(enemies.map { it.col.toString() + "," + it.row })
            // خانه‌های اشغال‌شده باس (بلوک ۲×۲)
            for (e in enemies) {
                if (!e.def.boss) continue
                for (dc in 0 until e.size) {
                    for (dr in 0 until e.size) {
                        occupied.add((e.col + dc).toString() + "," + (e.row + dr))
                    }
                }
            }
            val freeCells = mutableListOf<Pair<Int, Int>>()
            for (r in 1..5) { // نیمه بالایی = ردیف‌های ۱ تا ۵
                for (c in 0 until Cfg.GRID_COLS) {
                    if (!occupied.contains(c.toString() + "," + r)) freeCells.add(Pair(c, r))
                }
            }
            freeCells.shuffle()
            var placed = 0
            i = 0
            while (i < halfCount && placed < freeCells.size) {
                val cell = freeCells[i]
                occupied.add(cell.first.toString() + "," + cell.second)
                val def = holeChance() ?: pick()
                val e = Enemy(def.key, cell.first, cell.second, hpBase)
                e.yOff = if (first) 0.0 else -1.0 // از بالا وارد شود
                enemies.add(e)
                queueFirstSeenGuide(e)
                placed++
                i++
            }
        }
        if (first) for (e in enemies) e.yOff = 0.0
        simDirty = true
    }

    /** اولین ظاهر هر خانه خاص: پنجره راهنما — فقط یک بار */
    fun queueFirstSeenGuide(e: Enemy) {
        if (Prefs.houseSeen(e.type)) return
        val guide = Cfg.HOUSES_GUIDE[e.type] ?: return
        Prefs.markHouseSeen(e.type)
        pendingGuides.add(guide)
    }

    /** شفای درمانگرها: دشمنان کناری (هم‌ردیف) مقداری HP برمی‌گردانند */
    private fun applyHeals() {
        val healers = enemies.filter { !it.dead && it.def.heal > 0 }
        if (healers.isEmpty()) return
        for (h in healers) {
            for (e in enemies) {
                if (e === h || e.dead || e.hp >= e.maxHp) continue
                if (e.row == h.row && abs(e.col - h.col) == 1) {
                    e.hp = min(e.maxHp, e.hp + ceil(e.maxHp * h.def.heal).toInt())
                    val c = enemyRect(e)
                    addEffect(Fx().apply {
                        kind = "heal"; x = c.x + c.w / 2; y = c.y
                        t = 0.0; dur = 0.7
                    })
                }
            }
        }
        sfx("heal")
    }

    /** انعکاس توپ از سطح یک مستطیل */
    fun bounceOffRect(b: Ball, c: RectD) {
        val cxm = c.x + c.w / 2
        val cym = c.y + c.h / 2
        val px = b.x - cxm
        val py = b.y - cym
        val oX = (c.w / 2 + b.radius) - abs(px)
        val oY = (c.h / 2 + b.radius) - abs(py)
        if (oX < oY) {
            val s = if (px >= 0) 1.0 else -1.0
            b.vx = s * abs(b.vx)
            b.x += s * oX
        } else {
            val s = if (py >= 0) 1.0 else -1.0
            b.vy = s * abs(b.vy)
            b.y += s * oY
        }
    }

    /** خانه‌های اشغال‌شده‌ی یک دشمن (دژخیم ۲×۲ چند خانه دارد) */
    private fun cellsOf(e: Enemy): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (dc in 0 until e.size) {
            for (dr in 0 until e.size) {
                cells.add(Pair(e.col + dc, e.row + dr))
            }
        }
        return cells
    }

    /** چسبیدگی دو خانه (مجاورت ۴جهته) */
    private fun areAdjacent(a: Enemy, b: Enemy): Boolean {
        for (ca in cellsOf(a)) {
            for (cb in cellsOf(b)) {
                if (abs(ca.first - cb.first) + abs(ca.second - cb.second) == 1) return true
            }
        }
        return false
    }

    /** مؤلفه‌ی همبند خانه‌ها از یک نقطه */
    fun connectedComponent(start: Enemy): List<Enemy> {
        val visited = LinkedHashSet<Enemy>()
        visited.add(start)
        val queue = ArrayDeque<Enemy>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (e in enemies) {
                if (e.dead || visited.contains(e)) continue
                if (!areAdjacent(cur, e)) continue
                visited.add(e)
                queue.add(e)
            }
        }
        return visited.toList()
    }

    /**
     * زنجیره برق: پرش لایه‌به‌لایه بین خانه‌های مجاور — فاصله پرش‌ها ۰.۱ ثانیه و
     * هر پرش ۵۰٪ قدرتِ پایه روی پرش قبلی اضافه می‌شود (جمعی: ۲۷ ← ۴۰ ← ۵۴...)
     */
    fun queueChain(start: Enemy, dmg: Double) {
        val nCfg = Cfg.BALLS["electric"]!!
        val gap = if (nCfg.jumpGap > 0) nCfg.jumpGap else 0.1
        val gain = nCfg.jumpGain
        val seen = HashSet<Enemy>()
        seen.add(start)
        val ordered = mutableListOf<Enemy>()
        var frontier = mutableListOf(start)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Enemy>()
            for (cur in frontier) {
                for (e in enemies) {
                    if (e.dead || seen.contains(e)) continue
                    if (!areAdjacent(cur, e)) continue
                    seen.add(e)
                    next.add(e)
                    ordered.add(e)
                }
            }
            frontier = next
        }
        var depth = 0
        for (e in ordered) {
            depth++
            // پرش اول = آسیب پایه؛ هر پرش بعدی +۵۰٪ قدرت پایه (جمعی)
            val jumpDmg = kotlin.math.round(dmg + (depth - 1) * gain * dmg).roundToInt()
            chainQueue.add(ChainJump(e, start, jumpDmg, depth * gap))
        }
    }

    /** برخورد مستقیم توپ برق با خانه آهنی: برق قرمزِ روشن‌تر به همه‌ی خانه‌های صفحه */
    private fun fullBoardZap(iron: Enemy, dmg: Double) {
        val color = "#ff1744"
        val c = enemyRect(iron)
        val x1 = c.x + c.w / 2
        val y1 = c.y + c.h / 2
        // کپی لحظه‌ای: damageEnemy حین زدن همه‌ی خانه‌ها ممکن است ذرّه‌ی شکافتی اضافه کند (CME)
        for (e in enemies.toList()) {
            if (e.dead) continue
            val cr = enemyRect(e)
            if (e !== iron) {
                addEffect(Fx().apply {
                    kind = "zap"
                    this.x1 = x1; this.y1 = y1
                    x2 = cr.x + cr.w / 2; y2 = cr.y + cr.h / 2
                    this.color = color
                    thick = 2.4; t = 0.0; dur = 0.45
                    jag = doubleArrayOf(
                        Random.nextDouble() - 0.5,
                        Random.nextDouble() - 0.5,
                        Random.nextDouble() - 0.5
                    )
                })
            }
            damageEnemy(e, dmg, cr.x + cr.w / 2, cr.y, "electric")
        }
        addEffect(Fx().apply {
            kind = "boom"; x = x1; y = y1; t = 0.0; dur = 0.4; this.color = color
        })
        shake = max(shake, 0.5)
        sfx("thunder")
    }

    // ===== دژخیم‌کش =====
    private fun tryExecutioner() {
        val cfg = Cfg.BALLS["explosive"]!!
        val chance = cfg.executionerChance
        if (chance <= 0 || Random.nextDouble() >= chance) return
        var target: Enemy? = null
        for (e in enemies) {
            if (e.dead || e.def.soft) continue // ژله‌ای مصون از بمب
            if (target == null || e.hp > target.hp) target = e
        }
        val tg = target ?: return
        val c = enemyRect(tg)
        // سپر دژخیم ضربه‌ی دژخیم‌کش را هم دفع می‌کند
        if (tg.def.boss && tg.shield) {
            addEffect(Fx().apply {
                kind = "shield"; x = c.x + c.w / 2; y = c.y + c.h / 2
                t = 0.0; dur = 0.35
            })
            sfx("shield")
            return
        }
        val dmg = max(1, kotlin.math.round(tg.maxHp * cfg.executionerDmg).roundToInt())
        tg.hp -= dmg
        tg.hitFlash = 1.0
        addEffect(Fx().apply {
            kind = "boom"; x = c.x + c.w / 2; y = c.y + c.h / 2
            t = 0.0; dur = 0.35; color = "#ff5252"
        })
        addEffect(Fx().apply {
            kind = "dmg"; x = c.x + c.w / 2; y = c.y
            text = "دژخیم‌کش " + faNum(dmg); t = 0.0; dur = 1.0; color = "#ff5252"
        })
        sfx("breach")
        // ترک خوردن سنگی هم مثل ضربه‌های معمولی چک شود
        if (tg.def.crackAt > 0 && tg.crackStage == 0 && tg.hp > 0 && tg.hp <= tg.maxHp * tg.def.crackAt) {
            tg.crackStage = 1
        }
        if (tg.hp <= 0) killEnemy(tg, "explosive")
    }

    private fun hitEnemy(b: Ball, e: Enemy, c: RectD) {
        val st = b.stats
        val dmgTxtX = c.x + c.w / 2
        val dmgTxtY = c.y
        lastDamageTime = time

        if (b.type == "normal") {
            // سیاهچاله: توپ معمولی را می‌بلعد
            if (e.def.hole) {
                b.dead = true
                addEffect(Fx().apply {
                    kind = "vortex"; x = c.x + c.w / 2; y = c.y + c.h / 2
                    t = 0.0; dur = 0.45
                })
                sfx("vanish")
                return
            }
            // src='normal' حتماً پاس می‌شود تا ضربه به خانه نفرین‌شده ۱۰ برابر محاسبه شود
            damageEnemy(e, st.damage, dmgTxtX, dmgTxtY, "normal")
            // شمارنده برخورد هر گلوله مجزاست؛ هر ۵ برخورد گلوله سبز جوانه می‌زند
            consumeNormalHit(b)
            // نامیرا: فقط از سطح برخورد جدا می‌شود
            bounceOffRect(b, c)

        } else if (b.type == "explosive") {
            // توپ بمب روی خانه‌های ژله‌ای هیچ اثری ندارد
            val jellyImmune: (Enemy) -> Boolean = { it.type == "jelly" }
            // توپ بمب به خانه‌های سنگی ۲ برابر آسیب می‌زند
            val stoneMul = Cfg.BALLS["explosive"]!!.stoneMul
            val dmgTo: (Enemy) -> Double = { en ->
                if (en.type == "stone") kotlin.math.round(st.damage * stoneMul) else st.damage
            }
            if (jellyImmune(e)) {
                addEffect(Fx().apply {
                    kind = "dmg"; x = dmgTxtX; y = dmgTxtY
                    text = "مصون!"; t = 0.0; dur = 0.6; color = "#ce93d8"
                })
            } else {
                damageEnemy(e, dmgTo(e), dmgTxtX, dmgTxtY, null)
            }
            val R = st.radius * ts
            // کپی لحظه‌ای: آسیب آبشاری ممکن است دشمن شکافتی را بکشد و ذرّه اضافه کند (CME)
            for (e2 in enemies.toList()) {
                if (e2 === e || e2.dead) continue
                if (jellyImmune(e2)) continue // ژله‌ای در برابر بمب مصون است
                val c2 = enemyRect(e2)
                val ex = c2.x + c2.w / 2
                val ey = c2.y + c2.h / 2
                if (hypot(ex - b.x, ey - b.y) <= R + c2.w / 2) {
                    damageEnemy(e2, ceil(dmgTo(e2) * st.splash), c2.x + c2.w / 2, c2.y, null)
                }
            }
            // دژخیم‌کش: هر ضربه‌ی بمب یک قُل مستقل ۲۰٪
            tryExecutioner()
            addEffect(Fx().apply {
                kind = "explosion"; x = b.x; y = b.y; r = R
                t = 0.0; dur = 0.35
            })
            shake = max(shake, 0.25)
            sfx("boom")
            b.dead = true

        } else if (b.type == "ghost") {
            // گلوله روحی با برخورد به خانه آهنی محو می‌شود
            if (e.type == "iron") {
                b.dead = true
                addEffect(Fx().apply {
                    kind = "boom"; x = b.x; y = b.y
                    t = 0.0; dur = 0.3; color = "#b39ddb"
                })
                sfx("vanish")
                return
            }
            // عبور می‌کند؛ هر دشمن فقط یک بار در هر عبور از هر گلوله آسیب می‌بیند
            if (!b.hitSet.contains(e)) {
                b.hitSet.add(e)
                damageEnemy(e, st.damage, dmgTxtX, dmgTxtY, "ghost")
                sfx("hit")
                // اعمال نفرین (بعد از آسیب عبور)
                if (!e.dead && !e.def.soft && !e.cursed &&
                    Random.nextDouble() < Cfg.BALLS["ghost"]!!.curseChance
                ) {
                    e.cursed = true
                    addEffect(Fx().apply {
                        kind = "dmg"; x = dmgTxtX; y = dmgTxtY - ts * 0.45
                        text = "نفرین! 👻"; t = 0.0; dur = 1.0; color = "#b39ddb"
                    })
                    sfx("shield")
                }
            }

        } else if (b.type == "electric") {
            // ===== توپ برق: پرتو در اولین خانه تمام می‌شود اما زنجیره اثر می‌کند =====
            if (b.laserPath.isEmpty()) {
                b.laserPath.add(
                    floatArrayOf(
                        (b.x - b.vx * 0.02).toFloat(),
                        (b.y - b.vy * 0.02).toFloat()
                    )
                )
            }

            // برخورد مستقیم با خانه آهنی → برق قرمزِ فوری به همه خانه‌های صفحه
            if (e.type == "iron") {
                fullBoardZap(e, st.damage)
                b.laserPath.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                killBeam(b)
                b.dead = true
                return
            }

            damageEnemy(e, st.damage, dmgTxtX, dmgTxtY, "electric")
            addEffect(Fx().apply {
                kind = "laserhit"; x = b.x; y = b.y; color = "#40c4ff"
                t = 0.0; dur = 0.25
            })
            sfx("zap")
            // پرتو همان‌جا تمام می‌شود
            b.laserPath.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
            killBeam(b)
            b.dead = true
            // زنجیره: پرش به خانه‌های چسبیده
            queueChain(e, st.damage)
        }
    }

    // ---------- پیش‌نمایش مسیر (خط‌چین) ----------
    fun ensureSim() {
        if (!simDirty && simCache != null) return
        simCache = HashMap()
        for (key in Cfg.BALL_KEYS) {
            simCache!![key] = simulatePath(key)
        }
        simDirty = false
    }

    fun simFor(key: String): SimPath? = simCache?.get(key)

    /** شبیه‌سازی مسیر یک توپ با همان منطق برخورد بازی (بدون آسیب زدن) */
    private fun simulatePath(key: String): SimPath {
        val t = ts
        val st = statsFor(key)
        val idx = Cfg.BALL_KEYS.indexOf(key)
        val b = Ball(key, cannonX(idx), cannonY, aims[key]!!, st, t)
        val pts = mutableListOf<FloatArray>()
        val hits = mutableListOf<FloatArray>()
        var explosion: FloatArray? = null

        val dt = 1.0 / 120
        val dur = if (b.duration.isInfinite()) 8.0 else b.duration
        val maxSteps = min(1500, ceil((dur + 0.1) / dt).toInt())
        val left = ox + b.radius
        val right = ox + Cfg.GRID_COLS * t - b.radius
        val top = oy + b.radius
        val bottom = defenseY - b.radius
        val simChain = LinkedHashSet<Enemy>() // خانه‌هایی که زنجیره برق به آن‌ها می‌رسد
        var simNormalHits = 0                 // پیش‌نمایش توپ معمولی فقط تا ۲ برخورد

        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
        for (step in 0 until maxSteps) {
            b.time += dt
            b.x += b.vx * dt
            b.y += b.vy * dt

            // دیوارها — توپ برق انعکاس ندارد
            if (b.type == "electric") {
                if (b.x <= left || b.x >= right || b.y <= top) {
                    pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    break
                }
            } else {
                if (b.x < left) {
                    b.x = left; b.vx = abs(b.vx)
                } else if (b.x > right) {
                    b.x = right; b.vx = -abs(b.vx)
                }
                if (b.y < top) {
                    b.y = top; b.vy = abs(b.vy)
                }
            }
            // دیوار پایین: مسیر همان‌جا تمام می‌شود
            if (b.y > bottom && b.vy > 0) {
                pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                break
            }

            // برخورد با دشمنان (فقط هندسه، بدون آسیب)
            var hitE: Enemy? = null
            var hitC: RectD? = null
            for (e in enemies) {
                if (e.dead || e === b.ignore) continue
                val c = enemyRect(e)
                val nx = clamp(b.x, c.x, c.x + c.w)
                val ny = clamp(b.y, c.y, c.y + c.h)
                val dx = b.x - nx
                val dy = b.y - ny
                if (dx * dx + dy * dy <= b.radius * b.radius) {
                    hitE = e; hitC = c
                    break
                }
            }

            if (hitE != null && hitC != null) {
                if (b.type == "normal") {
                    // سیاهچاله: توپ معمولی محو می‌شود
                    if (hitE.def.hole) {
                        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                        break
                    }
                    hits.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    b.bounces++
                    bounceOffRect(b, hitC)
                    simNormalHits++
                    if (simNormalHits >= 2) {
                        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                        break
                    }
                } else if (b.type == "explosive") {
                    explosion = floatArrayOf(b.x.toFloat(), b.y.toFloat(), (st.radius * t).toFloat())
                    pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    break
                } else if (b.type == "ghost") {
                    if (hitE.type == "iron") {
                        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                        break
                    }
                    if (!b.hitSet.contains(hitE)) {
                        b.hitSet.add(hitE)
                        hits.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    }
                    // عبور می‌کند؛ مسیر ادامه دارد
                } else if (b.type == "electric") {
                    hits.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    for (e in connectedComponent(hitE)) {
                        if (e !== hitE) simChain.add(e)
                    }
                    pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
                    break
                }
            }

            if (step % 4 == 0) pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
        }
        pts.add(floatArrayOf(b.x.toFloat(), b.y.toFloat()))
        val chains = simChain.map { e ->
            val cr = enemyRect(e)
            floatArrayOf((cr.x + cr.w / 2).toFloat(), (cr.y + cr.h / 2).toFloat())
        }
        return SimPath(key, pts, hits, explosion, chains)
    }
}
