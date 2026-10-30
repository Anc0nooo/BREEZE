import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 版本号 + 自动更新地址放在 version.properties，改版本只需要动这一个文件。
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionCode = (versionProps.getProperty("versionCode") ?: "1").trim().toInt()
val appVersionName = (versionProps.getProperty("versionName") ?: "1.0.0").trim()
val updateBase = (versionProps.getProperty("updateBase") ?: "https://example.com/breeze/").trim()

// 正式签名：没有 keystore.properties 时退回 debug 签名，保证别人 clone 下来也能编。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.pangguai.breeze"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pangguai.breeze"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_BASE", "\"$updateBase\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // 不开 R8：支付宝/微信 SDK 都有反射与 JNI，混淆规则没实测过，
            // 自用 App 体积多 1~2MB 完全能接受，稳比小重要。
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 扫码：CameraX 预览 + ZXing 解码（不用 zxing-android-embedded 的横屏 CaptureActivity）
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 支付宝 App 支付（订单串来自服务端 pay/unify/prePay）
    implementation("com.alipay.sdk:alipaysdk-android:15.8.42")
    // 微信 App 支付（prepayParam 是 JSON，需要 WXAPIFactory 组装 PayReq）
    implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.40")
}
