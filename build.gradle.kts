plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.personalassistant"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.example.personalassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-local"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake { cppFlags += listOf("-std=c++17", "-O2") }
        }
    }

    buildFeatures { compose = true }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.19.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
