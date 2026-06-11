import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.ktor.client.core)
            // Demo traffic is served by MockEngine so the sample works offline
            // and mirrors the design's deterministic IoT data.
            implementation(libs.ktor.client.mock)

            // KMP no-op swap pattern: `-Psharingan.noop` builds the sample
            // against the no-op artifact, proving API parity and zero UI payload.
            if (providers.gradleProperty("sharingan.noop").isPresent) {
                implementation(project(":sharingan-noop"))
            } else {
                implementation(project(":sharingan"))
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        // Capture-notification E2E (needs the real app + zero-setup init):
        // ./gradlew :sample:composeApp:connectedDebugAndroidTest
        androidInstrumentedTest.dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

android {
    namespace = "dev.sharingan.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.sharingan.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // AGP 8.13's bundled lint can't read Kotlin 2.4 metadata yet.
        checkReleaseBuilds = false
    }
}
