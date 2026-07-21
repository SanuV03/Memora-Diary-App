plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    // ADD THIS LINE BELOW
    id("kotlin-kapt")
}

android {
    namespace = "com.chronicle.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chronicle.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }


    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)
    implementation(libs.coil.compose)
    implementation(libs.datastore.prefs)
    implementation(libs.mpandroidchart)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    implementation("com.google.firebase:firebase-auth-ktx")
    // Image Loading (Required for Requirement 2)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // Coroutines for Firebase tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}