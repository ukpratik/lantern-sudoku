// SPDX-License-Identifier: GPL-3.0-or-later
// Pure Kotlin game engine: no Android, no coroutines, nothing else in the repo (ADR-002, LLD §1).
// kotlinx-serialization is the one library: the save-state codec (LLD §2.3).
plugins {
    id("lantern.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

// -Plantern.slow=true runs the property tests with 100,000 seeds; -Plantern.writeFixtures=true rewrites the blob fixtures.
tasks.test {
    for (key in listOf("lantern.slow", "lantern.writeFixtures")) {
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
