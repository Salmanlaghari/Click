import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.click.browser"
    compileSdk = 36

    // Release signing for Play closed testing.
    // Reads GitHub Secrets-provided env vars (CLICK_KEYSTORE_FILE must point at a .p12 file).
    // NEVER commit a keystore, password, or keystore.properties with real values.
    signingConfigs {
        create("release") {
            val ksFile = System.getenv("CLICK_KEYSTORE_FILE")
            val ksPass = System.getenv("CLICK_KEYSTORE_PASSWORD")
            val aliasName = System.getenv("CLICK_KEY_ALIAS")
            val keyPass = System.getenv("CLICK_KEY_PASSWORD")
            if (!ksFile.isNullOrBlank() && !ksPass.isNullOrBlank()
                && !aliasName.isNullOrBlank() && !keyPass.isNullOrBlank()
                && File(ksFile).exists()
            ) {
                storeFile = File(ksFile)
                storePassword = ksPass
                keyAlias = aliasName
                keyPassword = keyPass
                storeType = "pkcs12"
            }
        }
    }

    defaultConfig {
        // Play Store package: com.teampkai.clickbrowser
        // (namespace stays com.click.browser so BuildConfig/R keep resolving;
        //  namespace != applicationId is fully supported by AGP)
        applicationId = "com.teampkai.clickbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        // Built-in Groq key for AI chat, injected at build time from the
        // GROQ_API_KEY env var (GitHub Actions secret in CI). The key is
        // XOR-obfuscated with a pad and hex-encoded so the RAW key never
        // appears in BuildConfig / the APK as a plain string.
        // Honest note: this defeats casual `strings` extraction, NOT a
        // determined reverser. Empty when the env var is missing — the build
        // still passes and the app falls back to asking for the user's key.
        // At runtime the user's own Settings key always takes precedence.
        // See KeyObfuscator.kt for the runtime decode. The pad itself is also
        // passed via BuildConfig (single source of truth — Kilo review).
        // (Hex — not base64 — because the Gradle script classpath reliably
        // supports String.format; no extra imports needed.)
        val groqObfPad = "ClickBrowserObfPad2026"
        val groqKeyRaw = System.getenv("GROQ_API_KEY") ?: ""
        val groqKeyObf = if (groqKeyRaw.isBlank()) "" else {
            val padBytes = groqObfPad.toByteArray(Charsets.UTF_8)
            val rawBytes = groqKeyRaw.toByteArray(Charsets.UTF_8)
            val xored = ByteArray(rawBytes.size) { i ->
                (rawBytes[i].toInt() xor padBytes[i % padBytes.size].toInt()).toByte()
            }
            xored.joinToString("") { "%02x".format(it) }
        }
        buildConfigField("String", "GROQ_API_KEY_OBF", "\"$groqKeyObf\"")
        buildConfigField("String", "GROQ_OBF_PAD", "\"$groqObfPad\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }

        release {
            // Use the release signing config only when all CLICK_* env vars are present
            // (set by CI from GitHub Secrets). Otherwise fall back to debug signing so
            // local/CI builds stay green before Prince adds the secrets.
            val ksFile = System.getenv("CLICK_KEYSTORE_FILE")
            val hasReleaseKeys = !ksFile.isNullOrBlank()
                && !System.getenv("CLICK_KEYSTORE_PASSWORD").isNullOrBlank()
                && !System.getenv("CLICK_KEY_ALIAS").isNullOrBlank()
                && !System.getenv("CLICK_KEY_PASSWORD").isNullOrBlank()
                && File(ksFile).exists()
            if (hasReleaseKeys) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
                println("WARNING: Release signing secrets missing — building debug-signed APK/AAB. " +
                    "Set CLICK_KEYSTORE_FILE/CLICK_KEYSTORE_PASSWORD/CLICK_KEY_ALIAS/CLICK_KEY_PASSWORD for Play-ready signing.")
            }
            // R8 full obfuscation + resource shrinking for release builds
            // (decompile guard). Keep rules live in app/proguard-rules.pro —
            // notably the WebView @JavascriptInterface bridges, which JS calls
            // by method name. CI must stay green: fix the rules if R8 breaks
            // the release build, don't disable minify.
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    // On-device translation (ML Kit). Language MODELS are downloaded on
    // demand at runtime — nothing is bundled, so the APK stays lean.
    implementation(libs.mlkit.translate)
    // Google AdMob for news-feed monetization (banner + native ads).
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
