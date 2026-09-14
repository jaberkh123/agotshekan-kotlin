plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
