import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0+ ships Kotlin support built in; applying
    // org.jetbrains.kotlin.android now fails the build.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.srpass.kykfz01"
    // Android 17 (API 37) - requires the android-37.2 platform and
    // AGP >= 9.1.0, since AGP 9.0.1 caps out at compileSdk 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.srpass.kykfz01"
        // Android 11 (API 30). AndroidX/Compose 目前的底线是 23，再往下要整包
        // 降级依赖；30 以上无需代码级版本分支（权限差异见 MediaPermissionCompat）。
        minSdk = 30
        // Targeting 37 opts into Android 17 behaviour changes. Orientation and
        // aspect ratio must NOT be locked (enforced on sw >= 600dp screens),
        // which is why android:screenOrientation is absent from the manifest.
        targetSdk = 37
        versionCode = 32
        versionName = "0.2.32-Beta"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        create("release") {
            val localProps = rootProject.file("local.properties")
                .readLines()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
                .toMap()
            storeFile = file(localProps["kykfz01.storeFile"] ?: "app/kykfz01-release.keystore")
            storePassword = localProps["kykfz01.storePassword"]
            keyAlias = localProps["kykfz01.keyAlias"] ?: "kykfz01"
            keyPassword = localProps["kykfz01.keyPassword"]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            // Native ABIs: arm64 covers every real device at minSdk 30+;
            // armeabi-v7a is kept as a safety net for the rare 32-bit device
            // (adds ~0.5 MB). No x86 ABIs: emulators can use debug builds.
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotpass)
    // kotpass keeps okio as `implementation`, so it is not visible on our
    // compile classpath. We touch okio.ByteString directly for KDBX binaries.
    implementation("com.squareup.okio:okio-jvm:3.15.0")
    implementation(libs.bouncycastle.bcprov)

    // Authenticator (TOTP): CameraX for preview + frames, ZXing to decode them.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    // Reads the EXIF orientation of an imported photo, so a portrait shot does
    // not end up lying sideways after being re-encoded.
    implementation(libs.androidx.exifinterface)
    // LocalLifecycleOwner moved here from androidx.compose.ui.platform.
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
    // Test classpath only: the packaged APK keeps using the platform's org.json.
    testImplementation(libs.org.json)
}
