plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astraya.wallpapers"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astraya.wallpapers"
        // Android 14: a still set on the lock screen alone leaves a live home
        // wallpaper running, which the one-tap setup relies on.
        minSdk = 34
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("BUILD_NUMBER") ?: "0"}"
    }

    // The Astraya key (shared with the Glyph Toy), so each new build installs
    // over the last. CI decodes it from a repository secret; without it the
    // build falls back to the debug key.
    val keystorePath = System.getenv("ASTRAYA_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("astraya") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ASTRAYA_KEYSTORE_PASSWORD")
                keyAlias = "astraya"
                keyPassword = System.getenv("ASTRAYA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("astraya") ?: signingConfigs.getByName("debug")
        }
    }

    // The loops are streamed straight out of the APK by MediaPlayer, which can
    // only open an asset that is stored, not deflated.
    androidResources {
        noCompress += listOf("mp4", "jpg", "png")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
