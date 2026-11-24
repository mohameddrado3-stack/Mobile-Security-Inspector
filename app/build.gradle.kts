plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.msi.inspector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.msi.inspector"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-alpha01"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Real VT/xterm terminal view and PTY implementation (Termux terminal library; license notices retained).
dependencies {
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
}
