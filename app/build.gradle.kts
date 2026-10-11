// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("lantern.android.application")
    id("lantern.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.lanternsudoku"

    defaultConfig {
        applicationId = "io.github.ukpratik.lanternsudoku" // permanent after first publication (ADR-010)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (ADR-012). 1.0.0's candidates sat just below it
        // (10000 − 100 + N = 9901..9903, D-035). A patch has no free code below it (1.0.0 = 10000, 1.0.1 = 10001), so
        // 1.0.1's candidates use 1.0.1's own code: they upgrade 1.0.0, are never published, and the final 1.0.1 is
        // built with the same code (D-044).
        versionCode = 10001
        versionName = "1.0.1-rc1"
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

    // ADR-008: identical apps apart from the review prompter (US-025: play = In-App Review, fdroid = no-op). The release build type has no
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

    // Robolectric tests (Room, Compose smoke tests) need the merged resources and assets (LLD §8).
    testOptions.unitTests.isIncludeAndroidResources = true
}

// LLD §2.1: the Room schema JSON is exported and committed; every shipped version stays frozen in app/schemas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// LLD §4.6: the committed bank (bank/v1/*.txt) is copied into the APK's assets at build time, never duplicated in git.
abstract class CopyBankAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bankFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.resolve("bank/v1")
        out.deleteRecursively()
        out.mkdirs()
        bankFiles.files.forEach { it.copyTo(out.resolve(it.name)) }
    }
}

val copyBankAssets = tasks.register<CopyBankAssets>("copyBankAssets") {
    bankFiles.from(rootProject.fileTree("bank/v1") { include("*.txt") })
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyBankAssets, CopyBankAssets::outputDir)
    }
}

// LLD §9 / ADR-012: one Baseline Profile for both store flavours (identical code), merged into main.
baselineProfile {
    mergeIntoMain = true
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    // ADR-008: proprietary code only in the play flavour (US-025 review prompt); fdroid gets a no-op.
    "playImplementation"(libs.play.review.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
