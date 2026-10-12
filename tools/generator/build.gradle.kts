// SPDX-License-Identifier: GPL-3.0-or-later
// Bank generator and validator CLI (ADR-004, LLD §4.6): generate | validate | report | freeze.
plugins {
    id("lantern.jvm.library")
    application
}

application {
    mainClass.set("io.github.ukpratik.lanternsudoku.tools.MainKt")
    applicationName = "lantern-generator"
}

dependencies {
    implementation(project(":engine"))
}

val bankDir = rootProject.layout.projectDirectory.dir("bank/v1")

// CI gate (LLD §8, NFR-006): validates every record of the committed bank.
val validateBank = tasks.register<JavaExec>("validateBank") {
    group = "verification"
    description = "Validates the committed puzzle bank (bank/v1)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("validate", bankDir.asFile.path)
}

tasks.named("check") { dependsOn(validateBank) }

// The bank tests (hint benchmark, regeneration, validator negatives) read the committed bank.
tasks.named<Test>("test") {
    inputs.dir(bankDir).withPropertyName("bank")
    systemProperty("lantern.bank", bankDir.asFile.path)
}

// `./gradlew :tools:generator:run --args="report"` runs from the repo root so default paths (bank/v1, build/) resolve.
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
