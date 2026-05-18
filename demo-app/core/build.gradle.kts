plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bizplay.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.kotlinx.coroutines.android)
    api("javax.inject:javax.inject:1")
    // Dagger annotations only (@MapKey, @Scope, @Qualifier) — the Hilt runtime
    // and the kapt plugin are only applied by consumers that actually wire DI.
    api("com.google.dagger:dagger:2.52")
}
