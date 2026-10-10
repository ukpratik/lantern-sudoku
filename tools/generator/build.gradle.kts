// SPDX-License-Identifier: GPL-3.0-or-later
// Bank generator and validator CLI (ADR-004). Commands are stubs until M1.
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

// CI gate (LLD §8): validates every record of the committed bank. Until M1 there is no bank, so it reports and passes.
val validateBank = tasks.register<JavaExec>("validateBank") {
    group = "verification"
    description = "Validates the committed puzzle bank (bank/)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("validate", rootProject.layout.projectDirectory.dir("bank").asFile.path)
}

tasks.named("check") { dependsOn(validateBank) }
