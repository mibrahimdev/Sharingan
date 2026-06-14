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

// ---------------------------------------------------------------------------
// Cross-module API-parity gate (issue #12).
//
// The "swap :sharingan-noop in release" safety story depends on the two modules
// exposing a SIGNATURE-FOR-SIGNATURE identical public CONTRACT. They are compiled
// independently, so the only thing keeping them in lockstep is human discipline:
// a drifted default or a forgotten method would surface only as a *consumer's
// release-build compile error* — the worst place to find it.
//
// BCV (#11) already dumps each module's full public surface to api/*.api (JVM) and
// api/*.klib.api (native/iOS). This task compares those committed dumps and FAILS
// the build on any divergence. It is pure text processing over the four files —
// no compilation, no iOS link — so it runs in seconds and needs no Android/iOS SDK.
//
// It is NOT a whole-file diff: :sharingan legitimately carries debug-only symbols
// the noop must not (and cannot) expose. Those are filtered out before comparing —
// see the local helpers and docs/api-parity.md for the rationale behind each
// exclusion. (The helpers are local to `doLast` on purpose: Gradle Kotlin DSL drops
// top-level statements placed after top-level `fun` declarations, so the comparison
// logic lives inside the task action rather than as script-level functions.)
// ---------------------------------------------------------------------------

tasks.register("checkApiParity") {
    group = "verification"
    description =
        "Asserts :sharingan and :sharingan-noop expose an identical public API contract (issue #12)."

    val realApi = layout.projectDirectory.file("sharingan/api/sharingan.api").asFile
    val noopApi = layout.projectDirectory.file("sharingan-noop/api/sharingan-noop.api").asFile
    val realKlib = layout.projectDirectory.file("sharingan/api/sharingan.klib.api").asFile
    val noopKlib = layout.projectDirectory.file("sharingan-noop/api/sharingan-noop.klib.api").asFile

    // Re-run only when one of the committed dumps changes.
    inputs.files(realApi, noopApi, realKlib, noopKlib)

    doLast {
        // Debug-only JVM classes the real module ships but a consumer never references
        // directly, so :sharingan-noop correctly omits them. Excluded from parity:
        //  - dev/sharingan/ui/**             : the in-app Compose viewer screens
        //  - dev/sharingan/internal/**       : init ContentProvider + notification receiver
        //  - dev/sharingan/SharinganActivity : the Android viewer Activity
        //  - any ComposableSingletons$*      : Compose-compiler lambda synthetics
        fun isExcludedJvmClass(name: String): Boolean =
            name.startsWith("dev/sharingan/ui/") ||
                name.startsWith("dev/sharingan/internal/") ||
                name == "dev/sharingan/SharinganActivity" ||
                name.contains("ComposableSingletons\$")

        // Reduce a JVM `.api` dump to the consumer-facing contract as a set of
        // qualified signatures. Drops the excluded classes wholesale and strips the
        // Compose-compiler `public static final field $stable I` line that only the
        // real module carries (Compose ships only in :sharingan). Each surviving
        // member is keyed by its enclosing class so an identical signature in a
        // different class cannot mask a divergence.
        fun jvmContractEntries(apiFile: File): Set<String> {
            val lines = apiFile.readLines()
            val entries = linkedSetOf<String>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                // Class/interface headers sit at column 0; members are tab-indented
                // and the block closes with a lone "}". Skip non-header lines.
                if (line.isBlank() || line.first().isWhitespace() || line == "}") {
                    i++
                    continue
                }
                val className =
                    if (" class " in line) line.substringAfter(" class ").substringBefore(" ") else ""
                val block = mutableListOf(line)
                var j = i + 1
                while (j < lines.size && lines[j] != "}") {
                    block.add(lines[j])
                    j++
                }
                i = j + 1
                if (className.isEmpty() || isExcludedJvmClass(className)) continue
                // Keep the header verbatim (sans trailing " {") so a changed supertype is caught.
                entries.add("CLASS " + line.removeSuffix(" {").trim())
                for (member in block.drop(1)) {
                    val m = member.trim()
                    if (m.isEmpty() || "\$stable" in m) continue
                    entries.add("$className :: $m")
                }
            }
            return entries
        }

        // Reduce a native `.klib.api` dump to the consumer-facing contract. Each BCV
        // declaration carries a globally-unique signature-id trailing comment, so the
        // trimmed line is unique and used as-is. Dropped:
        //  - the `//` header block, incl. `// Library unique name: <…:sharingan[-noop]>`
        //    which differs by module name and would always false-fail a raw diff;
        //  - `$stableprop` / `$stableprop_getter`: the native equivalent of the JVM
        //    `$stable` field, present only in the Compose-carrying real module;
        //  - `dev.sharingan.ui/SharinganScreen`: the top-level UI composable (ui/**).
        fun nativeContractEntries(klibFile: File): Set<String> {
            val entries = linkedSetOf<String>()
            for (raw in klibFile.readLines()) {
                val t = raw.trim()
                if (t.isEmpty() || t == "}" || t.startsWith("//")) continue
                if ("\$stableprop" in raw || "dev.sharingan.ui/" in raw) continue
                entries.add(t)
            }
            return entries
        }

        // Human-readable mismatch report for one surface, or null when the two
        // contracts are identical. Compared both directions so a symbol the noop is
        // MISSING and a symbol it EXPOSES EXTRA are both reported.
        fun diffSurface(
            surface: String,
            realPath: String,
            noopPath: String,
            realEntries: Set<String>,
            noopEntries: Set<String>,
        ): String? {
            val missingFromNoop = (realEntries - noopEntries).sorted()
            val extraInNoop = (noopEntries - realEntries).sorted()
            if (missingFromNoop.isEmpty() && extraInNoop.isEmpty()) return null
            return buildString {
                appendLine("API PARITY MISMATCH on the $surface surface")
                appendLine("  :sharingan      dump: $realPath")
                appendLine("  :sharingan-noop dump: $noopPath")
                if (missingFromNoop.isNotEmpty()) {
                    appendLine("  In :sharingan but MISSING from :sharingan-noop")
                    appendLine("  (consumer code compiled against debug would FAIL to compile after a release swap):")
                    missingFromNoop.forEach { appendLine("    - $it") }
                }
                if (extraInNoop.isNotEmpty()) {
                    appendLine("  In :sharingan-noop but NOT in :sharingan")
                    appendLine("  (the noop over-exposes surface the real module lacks — equally a contract break):")
                    extraInNoop.forEach { appendLine("    + $it") }
                }
            }
        }

        val problems = listOfNotNull(
            diffSurface(
                "Android/JVM (.api)",
                "sharingan/api/sharingan.api",
                "sharingan-noop/api/sharingan-noop.api",
                jvmContractEntries(realApi),
                jvmContractEntries(noopApi),
            ),
            diffSurface(
                "native/iOS (.klib.api)",
                "sharingan/api/sharingan.klib.api",
                "sharingan-noop/api/sharingan-noop.klib.api",
                nativeContractEntries(realKlib),
                nativeContractEntries(noopKlib),
            ),
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.joinToString("\n\n") + "\n\n" +
                    "The release-swap safety story requires :sharingan and :sharingan-noop to\n" +
                    "expose a signature-for-signature identical public contract. Reconcile the\n" +
                    "lagging module (add/remove the symbol), run `./gradlew apiDump`, and commit\n" +
                    "the regenerated dumps. See docs/api-parity.md for the contract and rationale.",
            )
        }
        logger.lifecycle(
            "API parity OK: :sharingan and :sharingan-noop expose an identical public contract (JVM + native).",
        )
    }
}

// Make the standard `check` lifecycle run the parity gate locally when present.
tasks.matching { it.name == "check" }.configureEach { dependsOn("checkApiParity") }
