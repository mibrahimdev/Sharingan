import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

// Maven coordinate only — Kotlin packages and the Android namespace stay
// `dev.sharingan(.noop)`. See :sharingan for the rationale (design decision 1a).
group = "io.github.mibrahimdev"
version = libs.versions.sharingan.get()

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val sharinganXCFramework = XCFramework("Sharingan")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Sharingan"
            isStatic = false
            sharinganXCFramework.add(this)
        }
    }

    sourceSets {
        all { languageSettings.optIn("kotlin.experimental.ExperimentalObjCName") }
        commonMain.dependencies {
            // Mirrors the real artifact's API surface (StateFlow, Ktor plugin
            // types) with no Compose/UI payload — release builds carry ~nothing.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.sharingan.noop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    publishToMavenCentral()
    // signAllPublications() is the default for real publishes (local + CI). The
    // `-PlocalPublishNoSign` flag disables it for offline POM verification where
    // no GPG key is available (real signing is provisioned in a separate issue).
    if (!providers.gradleProperty("localPublishNoSign").isPresent) {
        signAllPublications()
    }
    coordinates(group.toString(), "sharingan-noop", version.toString())

    pom {
        name.set("Sharingan (no-op)")
        description.set(
            "Inert release replacement for Sharingan — same API, zero runtime cost."
        )
        url.set("https://mibrahimdev.github.io/Sharingan/")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("mibrahimdev")
                name.set("Mohamed Ibrahim")
                email.set("mibrahim.dev@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/mibrahimdev/Sharingan.git")
            developerConnection.set("scm:git:ssh://git@github.com/mibrahimdev/Sharingan.git")
            url.set("https://github.com/mibrahimdev/Sharingan")
        }
    }
}
