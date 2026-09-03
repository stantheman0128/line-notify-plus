import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing config is read from keystore.properties (git-ignored).
// See keystore.properties.template for the expected keys.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.stanslab.linenotify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stanslab.linenotify"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "1.6.2"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // 實機驗證版可與 Play 版並存，避免 debug 簽章無法覆蓋 Play App Signing。
            applicationIdSuffix = ".singlepoptest"
            versionNameSuffix = "-single-pop-test"
        }
        release {
            isMinifyEnabled = false
            // 打包原生 debug symbols（androidx 依賴帶進來的 .so），方便 Play Console 分析原生層 crash/ANR。
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)

    debugImplementation(libs.androidx.ui.tooling)

    // JVM 單元測試（純邏輯，不依賴 Android framework）。用字串座標避免動 libs.versions.toml。
    testImplementation("junit:junit:4.13.2")
}
