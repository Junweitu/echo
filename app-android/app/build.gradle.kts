plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val echoKeystorePath = System.getenv("ECHO_KEYSTORE_PATH")
val echoKeystorePassword = System.getenv("ECHO_KEYSTORE_PASSWORD")

android {
    namespace = "tech.echo.app"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "tech.echo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.6.0-zipformer-deepseek-fix-zhTW"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            if (!echoKeystorePath.isNullOrBlank() && !echoKeystorePassword.isNullOrBlank()) {
                storeFile = file(echoKeystorePath)
                storePassword = echoKeystorePassword
                keyAlias = "echo"
                keyPassword = echoKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Zipformer CTC ONNX 模型很大，保持原始資產格式，讓 sherpa-onnx 可由 AssetManager 穩定讀取。
    androidResources {
        noCompress += listOf("onnx")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Room（本地片段儲存）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt（依賴注入）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // 本機離線 ASR 與 VAD：sherpa-onnx + Zipformer CTC 中文模型 + Silero VAD。
    // sherpa-onnx AAR 已內含其所需的 ONNX Runtime native libraries，不能再額外加入 onnxruntime-android。
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))

    // 網路只用於 DeepSeek LLM；語音辨識本身完全離線。
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // 配置加密儲存
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // WorkManager（語音轉寫 / 每日整理排程）
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
