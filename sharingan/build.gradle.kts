import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.ktlint)
}

// Maven coordinate only — Central auto-verifies io.github.mibrahimdev against the GitHub repo (design decision 1/1a).
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

    // Same baseName as :sharingan-noop so pure-Swift consumers swap debug/noop and `import Sharingan` resolves identically.
    val sharinganXCFramework = XCFramework("Sharingan")
    iosSimulatorArm64 {
        // The default KGP simulator id may not exist in newer Xcodes; pin to a device present here (xcrun simctl list devices).
        testRuns.configureEach { deviceId = "iPhone 17 Pro" }
    }
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
            // StateFlow types appear in the public API surface.
            api(libs.kotlinx.coroutines.core)
            // Ships in the core artifact (Chucker model) so the release no-op swap stays a single dependency substitution.
            implementation(libs.ktor.client.core)
            // Flight-recorder persistence (issue #27 / #49): DTO JSON codec.
            implementation(libs.kotlinx.serialization.json)
            // On-device SQLDelight database; implementation dependency so its
            // generated public types are not exported to consumers.
            implementation(project(":sharingan-db"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
        }
        androidUnitTest.dependencies {
        }
        iosTest.dependencies {
        }
        // On-device UI tests (CMP test API): ./gradlew :sharingan:connectedDebugAndroidTest
        androidInstrumentedTest.dependencies {
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.junit)
            implementation(libs.androidx.test.runner)
        }
    }
}

android {
    namespace = "dev.sharingan"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

mavenPublishing {
    publishToMavenCentral()
    // signAllPublications() is the default; -PlocalPublishNoSign disables it for offline POM verification (no GPG key).
    if (!providers.gradleProperty("localPublishNoSign").isPresent) {
        signAllPublications()
    }
    coordinates(group.toString(), "sharingan", version.toString())

    pom {
        name.set("Sharingan")
        description.set(
            "On-device debug logger and HTTP/MQTT/BLE inspector for Android & Kotlin Multiplatform.",
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
