import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.mibrahimdev"
version = libs.versions.sharingan.get()

sqldelight {
    databases {
        create("SharinganDatabase") { packageName.set("dev.sharingan.db") }
    }
}

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    iosArm64()
    iosSimulatorArm64 {
        // Same pin as :sharingan — the default KGP simulator id may not exist.
        testRuns.configureEach { deviceId = "iPhone 17 Pro" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies    { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies        { implementation(libs.sqldelight.native.driver) }
        androidUnitTest.dependencies{ implementation(libs.sqldelight.sqlite.driver) }
        iosTest.dependencies        { implementation(libs.sqldelight.native.driver) }
    }
}

android {
    namespace = "dev.sharingan.db"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (!providers.gradleProperty("localPublishNoSign").isPresent) { signAllPublications() }
    coordinates(group.toString(), "sharingan-db", version.toString())
    pom {
        name.set("Sharingan DB")
        description.set("On-device flight-recorder database for Sharingan. Implementation detail of the `sharingan` artifact.")
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
