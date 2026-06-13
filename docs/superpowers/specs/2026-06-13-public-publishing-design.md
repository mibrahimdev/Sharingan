# Public Publishing Design — Sharingan to Maven Central

**Status:** Approved (grilled 2026-06-13)
**Goal:** Make Sharingan publicly consumable for Android and Kotlin Multiplatform
(incl. KMP-iOS) by publishing both artifacts to **Maven Central** via the
Central Portal, with a reproducible tag-triggered release and a verified
from-Central clean-consumer acceptance gate. Pure-Swift SPM distribution is
explicitly deferred.

---

## 1. Scope

**In scope**
- Publish `:sharingan` and `:sharingan-noop` to Maven Central (Central Portal).
- Namespace verification, signing, POM metadata, javadoc stub — all via the
  vanniktech plugin.
- A `LICENSE` file (Apache-2.0) at repo root.
- Local first publish (interactive debug), then a tag-triggered GitHub Actions
  release workflow.
- Documentation switch from `mavenLocal()` to Central coordinates across
  README / site / AGENTS.md / llms.txt.
- A from-Central clean-consumer acceptance gate on Android **and** KMP-iOS,
  field-tested and visually verified.

**Out of scope (recorded in [docs/ROADMAP.md](../../ROADMAP.md))**
- **SPM / GitHub-Release-hosted XCFramework** for pure-Swift consumers. SPM
  `.binaryTarget` cannot express a build-configuration-conditional binary, so
  the debug↔noop swap needs its own designed Xcode recipe + field test. Pure-
  Swift consumers keep the already-verified **manual XCFramework** path
  (`assembleSharinganReleaseXCFramework` + drag-and-drop) documented as-is.
- CocoaPods podspec.
- SNAPSHOT / nightly channel.
- Migration to a `dev.sharingan` group (would require owning `sharingan.dev`).

---

## 2. Decisions (the grilled decision tree)

| # | Decision | Resolution | Rationale |
|---|----------|------------|-----------|
| 1 | Maven namespace | **`io.github.mibrahimdev`** | Central auto-verifies against the GitHub repo; no domain purchase. `dev.sharingan` would require owning `sharingan.dev`. |
| 1a | Source package paths | **Unchanged — keep `dev.sharingan.*`** and Android `namespace = "dev.sharingan"`. Change only Gradle `group`. | Maven group ID is independent of `import` paths; avoids a churny source rename. Consumers type `io.github.mibrahimdev:sharingan` in Gradle, `import dev.sharingan.*` in code. |
| 2 | Publishing tooling | **`com.vanniktech.maven.publish`** with `SonatypeHost.CENTRAL_PORTAL` | Purpose-built for KMP+Android multi-variant; auto-generates the required javadoc stub jar, signs everything, single POM block, targets the *current* Central Portal (OSSRH is sunset). |
| 3 | Release execution | **Local first run, then GitHub Actions tag-triggered** | First publish locally to debug Central's first-acceptance handshake; thereafter push-button + reproducible, and pairs with the GitHub Release the iOS roadmap will need. |
| 4a | Version source of truth | **`gradle/libs.versions.toml` (`sharingan`)**; tag `v<version>` must match; CI asserts `tag == project.version`. | Least magic; version bump is a reviewable commit; avoids detached-HEAD tag-derivation foot-guns. |
| 4b | SNAPSHOT channel | **None** | YAGNI — zero public users today. |
| 4c | Version line | **Stay pre-1.0 (`0.1.0`)** | API still reshaping (roadmap: Swift `log()` overload, runtime bridge). `0.x` signals instability. |
| 5 | noop artifact | **Both `:sharingan` and `:sharingan-noop` published at same version** | Release builds substitute to the noop (Chucker model); it must resolve from Central. |
| 6 | iOS distribution | **KMP via Central; pure-Swift stays manual XCFramework; SPM deferred** | SPM can't express the debug↔noop config swap; rushing it ships a misleading or under-documented Swift story. Central is the larger audience and fully ready to design. |
| 7 | License | **Apache-2.0**, `LICENSE` file at root, copyright "2026 Mohamed Ibrahim" | Permissive + patent grant; README already claims it. |
| 8 | POM `url` / `scm` | **`url` → GitHub Pages site; `scm` → the git repo** | Pages site is the curated front door; scm always the repo. |
| 9 | "Ready" gate | **From-Central clean-consumer resolution on Android + KMP-iOS, field-tested + visually verified, waiting on CDN propagation** | Exit-0 on publish ≠ consumable; mirrors how iOS-readiness was validated. |

---

## 3. Published coordinates

```
io.github.mibrahimdev:sharingan:0.1.0          // debug — real capture + viewer
io.github.mibrahimdev:sharingan-noop:0.1.0     // release — inert API mirror
```

KMP publishes a multi-artifact set per module (root + `-android` +
`-iosarm64` + `-iossimulatorarm64` + `*-metadata` + the
`kotlinMultiplatform` root). The vanniktech plugin coordinates all of them; we
do not hand-list artifacts.

Transitive surface stays as-is: only `kotlinx-coroutines-core` is `api`
(exposed). Ktor and Compose remain `implementation` and do **not** leak onto
consumer classpaths.

---

## 4. Build configuration

### 4.1 Plugin & version catalog
- Add `com.vanniktech.maven.publish` to `gradle/libs.versions.toml` (plugins)
  and apply it in **both** `sharingan/build.gradle.kts` and
  `sharingan-noop/build.gradle.kts`, replacing the bare `` `maven-publish` ``.
- `group = "io.github.mibrahimdev"` in both build files (was `dev.sharingan`).
- Version continues to read `libs.versions.sharingan.get()`.

### 4.2 `mavenPublishing { }` block (identical shape in both modules)
- `publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)` — **no** `automaticRelease`
  in the DSL; whether a deployment auto-releases is controlled by the Gradle
  *task* chosen (upload-only vs upload-and-release), so the interactive first
  run can inspect the staged deployment before releasing.
- `signAllPublications()`
- `coordinates(group, "<sharingan|sharingan-noop>", version)`
- POM:
  - `name` — "Sharingan" / "Sharingan (no-op)"
  - `description` — core: "On-device debug logger and HTTP/MQTT/BLE inspector
    for Android & Kotlin Multiplatform."; noop: "Inert release replacement for
    Sharingan — same API, zero runtime cost."
  - `url` — the GitHub Pages site
  - `licenses` — Apache-2.0 (`https://www.apache.org/licenses/LICENSE-2.0.txt`)
  - `developers` — id `mibrahimdev`, name "Mohamed Ibrahim", email
    `mibrahim.dev@gmail.com`
  - `scm` — `connection`/`developerConnection`/`url` →
    `github.com/mibrahimdev/Sharingan`

### 4.3 Secrets (never committed)
Gradle properties consumed by the plugin:
`mavenCentralUsername`, `mavenCentralPassword` (Central Portal token pair),
`signingInMemoryKey`, `signingInMemoryKeyPassword`.
- **Local run:** in `~/.gradle/gradle.properties` (outside the repo).
- **CI:** GitHub repo Secrets, injected as `ORG_GRADLE_PROJECT_*` env vars
  (GPG private key base64-encoded into a single secret).

### 4.4 GPG signing key
Generate an RSA/EdDSA key, publish the public key to a keyserver
(`keyserver.ubuntu.com`), export the private key for in-memory signing
(local + base64 for CI). Document the steps in a contributor doc, not committed
key material.

---

## 5. Release workflow

### 5.1 First publish — local, interactive
1. Set up Central Portal account, verify the `io.github.mibrahimdev` namespace
   (create the verification repo Central dictates).
2. Generate + register the GPG key.
3. Put secrets in `~/.gradle/gradle.properties`.
4. Tag `v0.1.0`, run `./gradlew publishToMavenCentral --no-configuration-cache`
   (upload-only — stages the deployment without releasing) for both modules.
5. Inspect the staged deployment and **release it manually** in the Central
   Portal UI. Once the flow is trusted, CI switches to
   `publishAndReleaseToMavenCentral` (upload-and-release in one step).

### 5.2 Subsequent releases — GitHub Actions, tag-triggered
- Workflow file `.github/workflows/publish.yml`, trigger `on: push: tags: ['v*']`.
- Steps: checkout → JDK 17 → assert `git tag == project.version` (fail on drift)
  → `publishAndReleaseToMavenCentral` for both modules → create the GitHub
  Release.
- Secrets injected as `ORG_GRADLE_PROJECT_*` env vars.

---

## 6. Documentation updates

The library already documents a `mavenLocal()` install. Once on Central:
- **README "Get the artifacts" section:** lead with Central coordinates +
  `mavenCentral()` in `repositories {}`; demote mavenLocal to a
  "building from source / contributing" note. Update the version matrix and the
  release-substitution recipe to the new `io.github.mibrahimdev` coordinates.
- **`site/docs/getting-started.html`, `site/docs/integrations.html`,
  `site/index.html`:** same coordinate switch.
- **`AGENTS.md` + `llms.txt`:** update dependency coordinates.
- **KMP-iOS recipe:** the `iosMain api(...)` + `export(...)` block now references
  `io.github.mibrahimdev:sharingan`.
- Add a `LICENSE` file (full Apache-2.0 text) at repo root.

---

## 7. Acceptance gate — "ready to publish publicly"

Declared ready only when a **clean consumer resolves from Central** (not
mavenLocal):

1. **Android:** throwaway app, `implementation("io.github.mibrahimdev:sharingan:0.1.0")`
   resolving from `mavenCentral()`, debug compiles + shows the viewer; release
   substitutes `sharingan-noop` and compiles. Delegated to the OpenCode Android
   field agent; build logs + screenshots inspected by the main loop.
2. **KMP-iOS:** shared-module project resolving `-iosarm64` /
   `-iossimulatorarm64` from Central, framework builds, viewer renders on the
   iPhone 17 Pro simulator. Delegated to the OpenCode iOS field agent;
   screenshots inspected by the main loop (agents can't see images).
3. **Propagation:** the verification waits for `repo1.maven.org` CDN
   availability (~15–30 min post-release), not just Portal "published" status.

Field-test methodology and visual-verification discipline mirror the
iOS-readiness pass. See [[sharingan-ios-consumer-integration]].

---

## 8. Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| Central first-acceptance rejects (missing POM field, unsigned, bad javadoc) | First run is local + interactive; vanniktech fills the common gaps; debug before wiring CI. |
| Namespace verification delay | Do it as step 1; it gates everything. |
| Tag/version drift in CI | Hard assertion `tag == project.version`, fail the build. |
| CDN propagation lag mistaken for failure | Acceptance gate explicitly polls `repo1.maven.org`. |
| Consumer pulls noop transitive Compose/Ktor | Already mitigated — those are `implementation`, not `api`. |
| GPG key leak | Key never committed; local in `~/.gradle`, CI as base64 secret. |
