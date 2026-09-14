import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// امضای ریلیز — دقیقاً همان کلید نسخه‌ی اصلی «آجرشکن» (release.keystore / alias=brickdefense)
// مقادیر از keystore.properties در ریشه‌ی پروژه خوانده می‌شود
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jaber.brickdefense"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jaber.brickdefense"   // نام پکیج دقیقاً مثل نسخه اصلی
        minSdk = 24
        targetSdk = 34
        versionCode = 15        // +۱: وسط‌چین شدن توپ‌ها + دکمه شروع ترن کدر + کمرنگ شدن توپ‌های خارج از نوبت
        versionName = "2.4"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("KEYSTORE", "release.keystore"))
            storePassword = keystoreProps.getProperty("STORE_PASS", "")
            keyAlias = keystoreProps.getProperty("ALIAS", "brickdefense")
            keyPassword = keystoreProps.getProperty("KEY_PASS", "")
            enableV3Signing = true // مثل بیلد اصلی (apksigner پیش‌فرض v2+v3 می‌زند)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // lintVitalRelease به دانلود lint-gradle نیاز دارد (در محیط آفلاین ممکن نیست)؛
    // اسکریپت بیلد دستی نسخه‌ی اصلی هم بدون لینت بود
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // SDK ادیوری و وابستگی‌هایش (کتابخانه باینری — کد پروژه ۱۰۰٪ کاتلین است)
    implementation(files("libs/adivery-sdk.aar"))
    implementation(files("libs/okhttp.jar"))
    implementation(files("libs/okio-jvm.jar"))
    implementation(files("libs/coroutines-core.jar"))
    implementation(files("libs/coroutines-android.jar"))
    implementation(files("libs/sentry.jar"))
}
