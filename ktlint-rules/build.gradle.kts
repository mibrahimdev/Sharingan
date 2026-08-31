import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No version: the Kotlin plugins are already on the root classpath
    // (root applies kotlinMultiplatform `apply false` for the whole build).
    kotlin("jvm")
}

// Internal tooling — the custom ktlint ruleset enforcing the AGENTS.md
// comments policy. Not published, not API-guarded, and deliberately not
// ktlint-checked itself (it would depend on itself via ktlintRuleset).
dependencies {
    // Must match the ktlint version resolved by org.jlleitschuh.gradle.ktlint
    // (14.2.0 defaults to 1.5.0). compileOnly: the ktlint runtime provides
    // these classes when the ruleset jar is loaded.
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core:1.5.0")
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core:1.5.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
