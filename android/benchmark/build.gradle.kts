// Macrobenchmark host module — see README.md for the on-device workflow.
//
// This module holds no product code: it is the instrumentation APK that measures the
// `:app` target. The test build type must be named exactly like the app's measurement
// build type (`benchmark`) so the two variants pair up, and it must stay debuggable
// because Macrobenchmark requires a debuggable *test* APK — the *target* app is the
// one that must be non-debuggable and profileable.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xu.wake.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Measure the sibling `:app` module, not a copy of it.
    targetProjectPath = ":app"
    // Self-instrumenting: the benchmark runs inside the instrumented process of this
    // module's own APK, which is what lets it reset / profile the target app.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    implementation("androidx.test:core:1.7.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("junit:junit:4.13.2")
}
