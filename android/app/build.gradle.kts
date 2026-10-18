plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.xu.wake"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.xu.wake"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "2.5.0"
        /* arm64 for phones; x86_64 so emulators run natively instead of under ARM
           translation, which breaks WebView percentage-height layout. */
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file("D:/dev/xwake-signing/release.jks")
            storePassword = file("D:/dev/xwake-signing/password.txt").readText().trim()
            keyAlias = "xwake"
            keyPassword = storePassword
        }
    }
    buildTypes {
        /* debug 也使用 release 密钥。
         *
         * 默认的 debug 用自动生成的 ~/.android/debug.keystore，与 release 是两把不同的证书，
         * Android 因此拒绝互相覆盖安装，每次换包都要先卸载，本地数据与登录态一并丢失。
         * 让两者共用同一把密钥后，debug 与 release 可直接互相覆盖。 */
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Macrobenchmark 的测量目标：与 release 同构（混淆 + release 签名），但必须 debuggable=false，
        // 否则 androidx.benchmark 会直接报错而不是给出错误数据。
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
}
