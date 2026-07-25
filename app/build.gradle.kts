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
            var keystoreFile: File? = null
            var storePass = "click123"
            var aliasName = "click"
            var keyPass = "click123"

            if (keystorePropsFile.exists()) {
                val props = Properties()
                keystorePropsFile.inputStream().use { props.load(it) }
                val sf = props.getProperty("storeFile")
                if (sf != null) {
                    val f = File(sf)
                    keystoreFile = if (f.isAbsolute) f else {
                        val f1 = rootProject.file(sf)
                        if (f1.exists()) f1 else file(sf)
                    }
                }
                storePass = props.getProperty("storePassword") ?: "click123"
                aliasName = props.getProperty("keyAlias") ?: "click"
                keyPass = props.getProperty("keyPassword") ?: "click123"
            }

            if (keystoreFile == null || !keystoreFile.exists()) {
                val f1 = file("release-key.jks")
                val f2 = rootProject.file("release-key.jks")
                val f3 = rootProject.file("app/release-key.jks")
                keystoreFile = when {
                    f1.exists() -> f1
                    f2.exists() -> f2
                    f3.exists() -> f3
                    else -> f1
                }
                storePass = System.getenv("KEYSTORE_PASSWORD") ?: storePass
                aliasName = System.getenv("KEY_ALIAS") ?: aliasName
                keyPass = System.getenv("KEY_PASSWORD") ?: keyPass
            }

            storeFile = keystoreFile
            storePassword = storePass
            keyAlias = aliasName
            keyPassword = keyPass
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
