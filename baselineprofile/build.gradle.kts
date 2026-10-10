// SPDX-License-Identifier: GPL-3.0-or-later
// Generates the app's Baseline Profile and measures cold start (NFR-001, LLD §8). Needs a device or emulator
// (API 33+); never part of `scripts/ci.sh`.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.lanternsudoku.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The store flavours run identical code (ADR-008); profile the Play one.
        missingDimensionStrategy("store", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
