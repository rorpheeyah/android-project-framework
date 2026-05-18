plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bizplay.aoscore"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // :aos-core knows only HTTP / crypto / storage primitives — it must NOT depend on
    // :core or any product layer. No `Receipt`, `Expense`, `Variant`, etc. types.
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.android)
    api("javax.inject:javax.inject:1")
}
