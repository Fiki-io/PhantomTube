plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.phantom.tube"
    compileSdk = 34

    val envNdk = System.getenv("ANDROID_NDK_ROOT") ?: System.getenv("ANDROID_NDK_HOME")
    if (!envNdk.isNullOrBlank() && file(envNdk).exists()) {
        val ndkDir = file(envNdk)
        ndkPath = ndkDir.absolutePath
        ndkVersion = ndkDir.name
    }

    defaultConfig {
        applicationId = "com.phantom.tube"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "2.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        resourceConfigurations += listOf("en", "id", "in", "es", "ja", "ru")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        getByName("debug") {
            val debugFile = file("debug.keystore")
            if (debugFile.exists()) {
                storeFile = debugFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            storeFile = file("release.jks")
            storePassword = "phantom2026"
            keyAlias = "phantom"
            keyPassword = "phantom2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.version",
                "META-INF/INDEX.LIST",
                "META-INF/*.txt",
                "**/*.version",
                "DebugProbesKt.bin",
                "kotlin/**"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        ignoreWarnings = true
        checkDependencies = false
        disable += listOf("InvalidFragmentVersionForActivityResult")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil for live YouTube thumbnails
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Networking (Live InnerTube & SponsorBlock API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // AndroidX WebKit with WebViewAssetLoader for secure HTTPS origin/referer (Solves Error 150/152/153)
    implementation("androidx.webkit:webkit:1.11.0")

    // Room Database for Local History & Bookmarks
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Media & MediaSession
    implementation("androidx.media:media:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
