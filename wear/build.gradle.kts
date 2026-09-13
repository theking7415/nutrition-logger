plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nutritionlogger.wear"
    // Reverted from 36 back to 35: that bump existed only to satisfy
    // connect-client 1.1.0's AAR metadata, and the module no longer depends
    // on Health Connect at all — see "The wear/watch integration" in
    // CLAUDE.md for why direct on-watch writes were abandoned in favor of
    // relaying to the phone. AGP/Gradle stay at the versions the Upgrade
    // Assistant already picked (8.11.2 / 8.13) — no reason to unwind those.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nutritionlogger"
        minSdk = 30          // Wear OS 3+ practical floor; Pixel Watch 5 is far above this
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Wear Compose — used for the tile's trampoline confirmation and the
    // manual test screen. Pull in whatever Android Studio suggests as
    // current stable if this has moved.
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")

    // Tiles + ProtoLayout for the actual watch tile.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    // tiles' own .module metadata lists this only on its runtime variant, not
    // the api/compile variant -- it's bundled but not exposed transitively,
    // so CallbackToFutureAdapter (used in WaterTileService.kt) needs it
    // declared explicitly here. Found by reading tiles-1.4.1.module directly,
    // after "Unresolved reference 'concurrent'" despite the class existing in
    // the Gradle cache.
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    // Pinned explicitly: ListenableFuture itself later became unresolvable
    // ("Cannot access class ... Check your module classpath") after adding
    // concurrent-futures above. Likely Gradle's built-in Guava/listenablefuture
    // conflict resolution substituting the empty "avoid-conflict" stub for
    // this module without a real Guava present to provide the class instead.
    // Declaring the real stub directly removes the ambiguity. If this doesn't
    // fix it, run `:wear:dependencies --configuration debugCompileClasspath`
    // and look at the actual resolved `com.google.guava` line rather than
    // guessing further.
    implementation("com.google.guava:listenablefuture:1.0")

    // Wearable Data Layer API — this is the whole write path now. No Health
    // Connect dependency on this module at all: the phone does the actual
    // write, so the watch needs no Health Connect permissions whatsoever.
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
