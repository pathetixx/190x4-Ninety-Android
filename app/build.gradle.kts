plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pw.x4.ninety"
    compileSdk = 35

    defaultConfig {
        applicationId = "pw.x4.ninety"
        // minSdk 26: adaptive-иконка чистым вектором (без бинарных PNG-фолбэков),
        // VpnService+libbox полностью поддержаны. Потеря API24/25 (<2%) несущественна.
        minSdk = 26
        targetSdk = 35

        // Версионирование: монотонный versionCode = major*10000 + minor*100 + patch.
        // 0.1.0 -> 100. Свежий проект, без офсет-ловушки (нет прошлых установок).
        versionCode = 100
        versionName = "0.1.0"

        // Только мобильные ABI — режет нативные .so ядра (libbox.aar несёт 4 ABI).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Per-ABI APK вместо universal: иначе libbox тащит обе ABI в один APK (148 МБ).
    // Один versionCode на оба — раздача через GitHub Releases, не Play.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            // Реквизиты из env (CI декодирует keystore из GH Secret). Локально пусто →
            // release неподписан (релиз собираем только в CI).
            System.getenv("NINETY_KEYSTORE")?.let { ks ->
                storeFile = file(ks)
                storePassword = System.getenv("NINETY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NINETY_KEY_ALIAS")
                keyPassword = System.getenv("NINETY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            // minify/shrink отложены: proguard для libbox/gomobile нужно отлаживать
            // отдельно, не блокируем им первый релиз. Размер режут ABI-splits.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            // applicationIdSuffix нет → чистый pw.x4.ninety
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        // не валим release-сборку на lint-предупреждениях (lintVitalRelease)
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Ядро VPN — libbox.aar (gomobile bind hiddify-sing-box), кладётся CI в app/libs/.
    // fileTree пустой локально (.gitignore) — milestone 1 код его не импортирует.
    implementation(fileTree("libs") { include("*.aar") })

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
