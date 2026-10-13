plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.omalauncher.ttfx"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        ndk { abiFilters += "arm64-v8a" }
    }
}

dependencies {
    testImplementation(libs.junit)
}
