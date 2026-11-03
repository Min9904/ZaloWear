plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.min.zalowear"
    compileSdk = 35
    defaultConfig {
        // Phone and watch apps MUST share applicationId + signing key for the Data Layer to connect.
        applicationId = "com.min.zalowear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        // Optional: a keystore in the project root keeps the same signature across machines.
        // Without it, the default debug key is used (fine as long as both apps are built on the same machine).
        val ks = rootProject.file("shared-debug.keystore")
        if (ks.exists()) getByName("debug") { storeFile = ks }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
