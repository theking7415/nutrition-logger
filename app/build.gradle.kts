plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nutritionlogger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nutritionlogger"
        minSdk = 26          // Health Connect requires API 26+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Health Connect. NOTE: pinned to alpha07 deliberately — see README, the
    // Metadata constructor became private in later builds.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Home screen widget: lets a tap call straight into Health Connect with no
    // activity launch, via ActionCallback. Verify the current stable version
    // in Android Studio's dependency suggestions if this has moved on.
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // On-device OCR (bundled model, no Play Services download, works offline).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Receives water-log relays from the watch tile (Wearable Data Layer API).
    // See relay/PhoneRelayListenerService.kt and CLAUDE.md, "The wear/watch
    // integration".
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // ScreenshotParser.extract() is pure Kotlin, so the parser is testable on
    // the JVM: ./gradlew testDebugUnitTest
    testImplementation("junit:junit:4.13.2")
}
