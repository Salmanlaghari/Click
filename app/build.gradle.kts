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
        applicationId = "com.click.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Built-in Groq key for AI chat, injected at build time from the
        // GROQ_API_KEY env var (GitHub Actions secret in CI). Empty when not
        // provided — the build still passes and the app falls back to asking
        // the user for their own key. NEVER hardcode a key here.
        // At runtime the user's own Settings key always takes precedence.
        buildConfigField("String", "DEFAULT_GROQ_API_KEY", "\"${System.getenv("GROQ_API_KEY") ?: ""}\"")

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
            // Keep full premium features and libraries intact to match Debug APK size (~15MB+)
            isMinifyEnabled = false
            isShrinkResources = false
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
