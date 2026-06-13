plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    // Applied to the root project: BCV injects apiDump/apiCheck into every
    // subproject and guards the committed public-API dumps (issue #11).
    alias(libs.plugins.binaryCompatibilityValidator)
}

// Public-API stability gate. The "swap sharingan-noop in release" safety story
// depends on the public API staying stable across versions; apiCheck (run in CI)
// fails the build when the surface drifts from the committed api/*.api dumps.
apiValidation {
    // The sample app is not a published library — nothing to protect.
    ignoredProjects += "composeApp"

    // KMP: also dump/verify the Kotlin/Native (iOS) ABI, not just the JVM/Android
    // surface, so iosMain-only declarations (SharinganViewController,
    // presentSharingan) are covered too.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
