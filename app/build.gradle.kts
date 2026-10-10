// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("lantern.android.application")
    id("lantern.android.compose")
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "io.github.ukpratik.lanternsudoku"

    defaultConfig {
        applicationId = "io.github.ukpratik.lanternsudoku" // permanent after first publication (ADR-010)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (ADR-012). Pre-release 0.1.0 until 1.0.0.
        versionCode = 100
        versionName = "0.1.0"
    }

    // ADR-012: keys live in ~/.android-keys/, passwords in ~/.gradle/gradle.properties, never in the repo.
    // A config exists only when its properties do; otherwise release builds stay unsigned (CI, F-Droid).
    signingConfigs {
        for ((name, prefix) in listOf("upload" to "lantern.upload", "release" to "lantern.release")) {
            val storeFilePath = providers.gradleProperty("$prefix.storeFile").orNull ?: continue
            create(name) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("$prefix.storePassword").get()
                keyAlias = providers.gradleProperty("$prefix.keyAlias").get()
                keyPassword = providers.gradleProperty("$prefix.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // ADR-008: identical apps apart from the review prompter (added in M6). The release build type has no
    // signing config, so each flavour's config applies to release only; debug keeps the debug key.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
        create("fdroid") {
            dimension = "store"
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // ADR-008: reproducible builds for F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// ADR-008 licence gate: generated at build time, fully offline. Strict mode fails the release build on any
// licence outside the GPL-3.0-compatible allowlist.
aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
        filterVariants.addAll("playRelease", "fdroidRelease")
    }
    export {
        excludeFields.addAll("generated", "funding", "developers")
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "OFL-1.1")
        // Play build only (review prompt, M6). Allowed for these Google groups and nothing else.
        allowedLicensesMap = mapOf(
            "PCSDKToS" to listOf("com.google.android.play"),
            "ASDKL" to listOf("com.google.android.gms"),
        )
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
