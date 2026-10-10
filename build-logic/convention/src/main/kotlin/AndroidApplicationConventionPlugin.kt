// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("minSdk").toInt()
                targetSdk = libs.version("targetSdk").toInt()
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            // LLD §8: Android Lint is the static-analysis gate. Dependabot owns upgrades, so version nags are off.
            lint {
                warningsAsErrors = true
                abortOnError = true
                checkDependencies = true
                disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
                // targetSdk is a recorded decision (ADR-011, D-023); raising it is a deliberate change, not a lint nag.
                disable += "OldTargetApi"
            }
        }
    }
}
