plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.compass.design"
    compileSdk = 35

    defaultConfig { minSdk = 21 }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    api(composeBom)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    debugApi(libs.compose.ui.tooling)
    api(libs.compose.ui.tooling.preview)
}
