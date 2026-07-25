import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.click.browser"
    compileSdk = 34

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val props = Properties()
                keystorePropsFile.inputStream().use { props.load(it) }
                val sf = props.getProperty("storeFile") ?: "release-key.jks"
                // Resolve path relative to project root or absolute
                storeFile = if (File(sf).isAbsolute) file(sf) else rootProject.file(sf)
                storePassword = props.getProperty("storePassword") ?: "click123"
                keyAlias = props.getProperty("keyAlias") ?: "click"
                keyPassword = props.getProperty("keyPassword") ?: "click123"
            } else {
                storeFile = file("release-key.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "click123"
                keyAlias = System.getenv("KEY_ALIAS") ?: "click"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "click123"
            }
        }
    }

    defaultConfig {
        applicationId = "com.click.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

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
            signingConfig = signingConfigs["release"]
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
