plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties

fun String.asBuildConfigString(): String = buildString {
    append('"')
    this@asBuildConfigString.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(char)
        }
    }
    append('"')
}

fun readLocalManifestUrl(): String {
    val localPropsFile = rootProject.file("local.properties")
    if (!localPropsFile.exists()) return ""

    val properties = Properties()
    localPropsFile.inputStream().use(properties::load)
    return properties.getProperty("BGT_REMOTE_MANIFEST_URL", "").trim()
}

val remoteManifestUrl = providers.gradleProperty("BGT_REMOTE_MANIFEST_URL")
    .orElse(readLocalManifestUrl())
    .get()

android {
    namespace = "com.bgtactician.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bgtactician.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2026.04.01-alpha"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "DEFAULT_MANIFEST_URL", remoteManifestUrl.asBuildConfigString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.material)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
