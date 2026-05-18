plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bizplay.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bizplay.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Per framework rule #8: MgGate URL is the only network configuration
            // baked into the binary. Each buildType points at its own MgGate.
            buildConfigField("String", "MG_URL", "\"https://mg-dev.bizplay.co.kr/\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "MG_URL", "\"https://mg.bizplay.co.kr/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // :app is the only module allowed to depend on every sibling.
    implementation(project(":aos-core"))
    implementation(project(":core"))
    implementation(project(":design-system"))
    implementation(project(":data"))
    implementation(project(":features"))
    implementation(project(":variants-kr"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
