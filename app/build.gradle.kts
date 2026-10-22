import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version lives in version.properties, which ./release.sh bumps.
val versions = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

// Release signing from keystore.properties (git-ignored) when there is one; otherwise this PC's debug key, like the
// other VOID apps in Void Store. Every Void Wars APK must carry the same key, or Android won't take it as an update.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.m4s4t0.voidwars"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.m4s4t0.voidwars"
        // Android 13+ (haptic primitives, low-latency audio). Built and tuned for the Solana Seeker on Android 16.
        minSdk = 33
        targetSdk = 36
        versionCode = versions.getProperty("versionCode").toInt()
        versionName = versions.getProperty("versionName")
        // In-app updates come from this repository's GitHub releases (see Updater.kt and release.sh).
        buildConfigField("String", "UPDATE_REPO", "\"M4S4T0-V01D/void-wars\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":void-ui"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
