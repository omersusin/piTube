import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.room)
}

android {
    namespace = "com.omersusin.pitube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.omersusin.pitube"
        minSdk = 26
        targetSdk = 36
        versionCode = 97

        versionName = "3.4.8"
        testInstrumentationRunner = "com.omersusin.pitube.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // NOTE: no ndk.abiFilters here — it hard-conflicts with splits.abi
        // below ("Conflicting configuration" AGP error). The splits block is
        // the single source of truth for emitted ABIs.

        buildConfigField("Boolean", "UPDATER_ENABLED", "true")

        // Optional build-time credentials for recognition providers (Audile
        // style: read from local.properties, never exposed in-app):
        //   AUDD_TOKEN=...
        //   ACR_CLOUD_HOST=identify-eu-west-1.acrcloud.com
        //   ACR_CLOUD_ACCESS_KEY=...
        //   ACR_CLOUD_ACCESS_SECRET=...
        // Shazam needs no credentials, and builds without a local.properties
        // simply get empty values (provider falls back gracefully).
        val localProps = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }
        fun String.buildConfigLiteral(): String =
            "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
        buildConfigField("String", "AUDD_TOKEN", localProps.getProperty("AUDD_TOKEN", "").buildConfigLiteral())
        buildConfigField("String", "ACR_CLOUD_HOST", localProps.getProperty("ACR_CLOUD_HOST", "").buildConfigLiteral())
        buildConfigField("String", "ACR_CLOUD_ACCESS_KEY", localProps.getProperty("ACR_CLOUD_ACCESS_KEY", "").buildConfigLiteral())
        buildConfigField("String", "ACR_CLOUD_ACCESS_SECRET", localProps.getProperty("ACR_CLOUD_ACCESS_SECRET", "").buildConfigLiteral())
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // Universal APK duplicates the full dex+package work (~1-2 min in CI).
            // Routine builds emit ABI splits only; pass -PuniversalApk for a
            // universal artifact (CI does this on manual dispatch / tag builds).
            isUniversalApk = project.hasProperty("universalApk")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        // Nightly: release-level performance + debug signing so it's easy to
        // sideload. Fixes the laggy-nightly issue reported in #66.
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use release signing if configured, otherwise fallback to debug
            val releaseKeystore =
                try {
                    signingConfigs.getByName("release").storeFile
                } catch (e: Exception) {
                    null
                }
            if (releaseKeystore?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
                println("Using RELEASE signing config with keystore: ${releaseKeystore.absolutePath}")
            } else {
                signingConfig = null // Let Gradle build an unsigned APK for IzzyOnDroid/F-Droid
                println("WARNING: Release keystore not found. Building UNSIGNED release APK.")
            }
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // Enable desugaring
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

composeCompiler {
    if (project.findProperty("composeCompilerReports") == "true") {
        val reportsDir = layout.buildDirectory.dir("compose_compiler")
        reportsDestination = reportsDir
        metricsDestination = reportsDir
    }
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // --- Compose (Using BOM is best practice) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Lifecycle & Architecture ---
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Image Loading ---
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)

    // --- Dependency Injection ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // --- Data & Network ---
    implementation(libs.newpipe.extractor)

    // Networking
    implementation(libs.okhttp)

    // Ktor (Managed in libs.versions.toml)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.encoding)

    // --- Device Sync (FLOW-SYNC/1) ---
    implementation(libs.ktor.server.core) {
        exclude(group = "org.fusesource.jansi", module = "jansi")
    }
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Serialization & JSON
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)

    // conscrypt for OkHttp TLS support on older Android versions
    implementation(libs.conscrypt.android)

    // --- Media Playback ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media)

    // --- Database & Storage ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // --- Home-screen widgets (Jetpack Glance) ---
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.graphics.shapes)

    // --- Async & Utils ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.apkupdater)

    implementation(libs.brotli)

    // --- Baseline profiles ---
    implementation(libs.androidx.profileinstaller)

    // Desugaring for older Android versions
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Allow references to generated code
ksp {
    arg("dagger.fastInit", "enabled")
}

hilt {
    enableAggregatingTask = true
}
