plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phantom.tube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phantom.tube"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "2.10.29"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations += listOf("en", "id", "in", "es", "ja", "ru")
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
        freeCompilerArgs += listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:nonSkippingGroupOptimization=true",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
                "META-INF/version-control-info.textproto",
                "META-INF/com/android/build/gradle/**",
                "**/*.version",
                "DebugProbesKt.bin",
                "kotlin/**",
                "kotlin-tooling-metadata.json"
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

configurations.all {
    exclude(group = "androidx.emoji2", module = "emoji2")
    exclude(group = "androidx.emoji2", module = "emoji2-views-helper")
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
