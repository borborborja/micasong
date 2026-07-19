plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.micasong.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micasong.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.0.14"
        vectorDrawables { useSupportLibrary = true }
    }

    // A fixed signing key so every build (local + CI) is signed identically, which lets you
    // upgrade the installed app without uninstalling first. The keystore path/credentials can
    // come from env vars (CI secret) or default to a local `micasong.keystore`. If no keystore
    // is present the build falls back to the default per-machine debug key (so a fresh clone or
    // a CI without the secret still builds).
    val keystoreFile = file(System.getenv("SIGNING_KEYSTORE_FILE") ?: "micasong.keystore")
    val hasSigningKey = keystoreFile.exists()
    signingConfigs {
        create("shared") {
            if (hasSigningKey) {
                storeFile = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "micasong"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "micasong"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "micasong"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningKey) signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            applicationIdSuffix = ".debug"
            if (hasSigningKey) signingConfig = signingConfigs.getByName("shared")
        }
    }

    // Two distributions (spec §45 F-Droid): a fully free/open-source build with no proprietary
    // dependencies, and a "full" build that may bundle proprietary components (e.g. Google Cast).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "IS_FOSS", "true")
            resValue("string", "app_name", "MiCaSong FOSS")
        }
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FOSS", "false")
            resValue("string", "app_name", "MiCaSong")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            // Emit real JVM default methods so Room recognises @Transaction default methods
            // declared in DAO interfaces (replaceLocalLibrary / setPlaylistTracks).
            "-Xjvm-default=all",
            // Media3 exposes most session/player APIs behind @UnstableApi. Opt in module-wide
            // rather than annotating every call site.
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }
    lint {
        // The Media3 UnstableApi surface is opted into module-wide above; lint's separate
        // check would otherwise flag every usage.
        disable += "UnsafeOptInUsageError"
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Proprietary components live only in the "full" flavor; the "foss" flavor stays F-Droid clean.
    "fullImplementation"("com.google.android.gms:play-services-cast-framework:21.5.0")
    // Media3's CastPlayer + the Cast route button (MediaRouteButton). Both pull in Google Cast, so
    // they must stay out of the FOSS build.
    "fullImplementation"(libs.androidx.media3.cast)
    "fullImplementation"(libs.androidx.mediarouter)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json for unit tests (the android.jar version is a throwing stub).
    testImplementation("org.json:json:20240303")
    // Robolectric runs real Android components (Room, ContentResolver/MediaStore) on the JVM,
    // giving runtime verification of the data layer without an emulator.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(libs.androidx.room.ktx)
    // Local fake HTTP server for end-to-end provider sync tests.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
}
