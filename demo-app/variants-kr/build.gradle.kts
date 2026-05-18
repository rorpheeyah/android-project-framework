plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bizplay.variants.kr"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // :variants-kr must NOT depend on :data, :design-system, :features, or any
    // other :variants-* module. Pure policy + DI.
    implementation(project(":core"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
