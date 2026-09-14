# آجرشکن — Brick Defense (نسخه بومی کاتلین) 🎮

بازنویسی **کاملاً بومی** بازی «آجرشکن — Brick Defense» به زبان **Kotlin**.

این ریپو جایگزین نسخه WebView است: در نسخه قبلی، بازی با HTML5/JavaScript داخل WebView اجرا می‌شد و فقط Wrapper با Java نوشته شده بود. اینجا **هیچ** جاوااسکریپت، جاوا، HTML و CSS وجود ندارد — کل بازی (منطق، رندر، UI، منوها، صدا) با **کاتلین خالص** روی Canvas بومی اندروید بازنویسی شده است.

> نام پکیج دقیقاً مثل نسخه اصلی است: `com.jaber.brickdefense` — یعنی APK جدید به‌عنوان **آپدیت** روی نسخه قبلی نصب می‌شود و همه‌چیز (نام اپ، آیکون، تبلیغات ادیوری) سر جای خودش است.

## ویژگی‌ها

- ✅ **۱۰۰٪ کاتلین** — بدون Java / JavaScript / HTML / CSS
- ✅ همان گیم‌پلی نسخه اصلی: ۴ توپ (معمولی ⚪، انفجاری 💥، روحی 👻، برقی ⚡)، کمپین ۱۰۰ مرحله‌ای، مود بی‌نهایت، دژخیم، سیاهچاله، نفرین روحی، جوانه سبز، دژخیم‌کش و...
- ✅ همان اسپرایت‌ها و ظاهر (خط‌چین مسیر، افکت‌ها، آلارم‌های قرمز...)
- ✅ صداهای رویه‌ای (WebAudio نسخه اصلی → AudioTrack با سنتز PCM در کاتلین)
- ✅ منوها، انتخاب مرحله با فصل‌بندی، تنظیمات، راهنمای دشمنان، پنجره ارتقا — همه با Canvas بومی رندر می‌شوند (بدون XML لایوت)
- ✅ تبلیغات ادیوری (همسان + میان‌صفحه‌ای + App Open) با همان شناسه‌ها — SDK به‌صورت AAR
- ✅ ذخیره پیشرفت/تنظیمات/رکورد با SharedPreferences

## ساختار پروژه

```
├── app/
│   ├── libs/                        ← SDK ادیوری + وابستگی‌هایش (باینری)
│   ├── build.gradle.kts             ← Gradle Kotlin DSL
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/img/              ← اسپرایت‌های بازی (همان تصاویر نسخه اصلی)
│       ├── res/mipmap-*/            ← آیکون لانچر (همان نسخه اصلی)
│       ├── res/layout/              ← فقط لایوت تبلیغ همسان ادیوری
│       └── java/com/jaber/brickdefense/
│           ├── MainActivity.kt      ← میزبان GameView + تبلیغات + دکمه بازگشت
│           ├── App.kt               ← App Open Ad یک‌درمیان
│           ├── Ads.kt               ← شناسه‌های ادیوری
│           ├── game/
│           │   ├── Config.kt        ← پورت config.js (ثابت‌ها + ذخیره‌سازی)
│           │   ├── Entities.kt      ← پورت entities.js (Enemy/Ball/Fx)
│           │   ├── GameEngine.kt    ← پورت game.js (کل منطق بازی + شبیه‌ساز مسیر)
│           │   ├── GameRenderer.kt  ← پورت متدهای draw (رندر Canvas)
│           │   ├── Sfx.kt           ← پورت sfx.js (سنتز صدا)
│           │   └── Assets.kt        ← پورت assets.js (لود تصاویر)
│           └── ui/
│               ├── GameView.kt      ← حلقه بازی (Choreographer) + لمس
│               ├── UiController.kt  ← پورت ui.js (وضعیت UI، پنجره‌ها، آموزش)
│               ├── UiDraw.kt        ← رندر منوها/HUD/کارت‌ها/مودال‌ها
│               └── UiKit.kt         ← ابزار رسم (دکمه، پنل، متن فارسی RTL)
```

## بیلد

با Android Studio (Hedgehog یا جدیدتر) باز کنید، یا با خط فرمان:

```bash
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

- minSdk 24 | targetSdk 34
- versionCode 12 / versionName 2.1 (یک واحد بالاتر از آخرین نسخه WebView تا آپدیت نصب شود)

## تفاوت‌های فنی با نسخه قبلی

| نسخه قبلی (WebView) | این نسخه (بومی) |
|---|---|
| بازی HTML5/JS داخل WebView | منطق و رندر کاتلین روی Canvas بومی |
| Wrapper Java | تمام کدهای پروژه کاتلین است |
| صدا با WebAudio | سنتز PCM با AudioTrack |
| UI با DOM/CSS | UI با Canvas بومی + متن فارسی RTL |
| ذخیره با localStorage | SharedPreferences با همان منطق |
