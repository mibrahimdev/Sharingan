# Releasing Sharingan

Sharingan ships two artifacts to Maven Central under `io.github.mibrahimdev`:

| Coordinate                            | Module            |
| ------------------------------------- | ----------------- |
| `io.github.mibrahimdev:sharingan`     | `:sharingan`      |
| `io.github.mibrahimdev:sharingan-noop`| `:sharingan-noop` |

Releases are **two steps by deliberate design**: CI *stages* a deployment to
the Central Portal, then a human *verifies and releases* it. We do **not**
auto-release, because **Maven Central releases are permanent and can never be
unpublished** (see [ADR 0001](adr/0001-stage-then-manual-release.md)).

```
push tag v<version>  ──▶  CI stages to Central Portal  ──▶  STOP (awaiting human)
                                                              │
                          maintainer verifies + clicks  ◀─────┘
                          "Publish" in the portal, then
                          cuts the GitHub Release
```

## Step 1 — cut the release tag (triggers CI staging)

1. Make sure `gradle/libs.versions.toml` → `sharingan` holds the version you
   intend to release (e.g. `0.1.0`), and it is merged to `main`.
2. Tag and push:

   ```bash
   git checkout main && git pull
   git tag v0.1.0          # must equal the catalog version, with a leading "v"
   git push origin v0.1.0
   ```

   The tag **must** equal the catalog version or the
   `Assert tag matches project version` step fails before anything is published
   (`scripts/check-version-tag.sh`).

3. The `Publish to Maven Central` workflow runs `./gradlew publishToMavenCentral`.
   This **uploads both modules to a new deployment on the Central Portal and
   stops** — nothing is public yet. The job's `Staged - next steps` summary
   links straight back to this checklist.

## Step 2 — verify the staged deployment in the Central Portal

1. Sign in at <https://central.sonatype.com> and open **Deployments**:
   <https://central.sonatype.com/publishing/deployments>
2. Find the new deployment from the CI run. Confirm **all** of:
   - **Status** is `VALIDATED` (not `FAILED`). If it failed, expand it to read
     why — do **not** publish a failed deployment; fix and re-stage instead.
   - **Both** components are present under group `io.github.mibrahimdev`:
     - `io.github.mibrahimdev:sharingan:<version>`
     - `io.github.mibrahimdev:sharingan-noop:<version>`

     If only one module is there, something is wrong — drop the deployment and
     re-stage. Never release a half-uploaded version.
   - The **version** matches the tag (`<version>`, no stray `-SNAPSHOT`).
   - Each component carries its **`.pom`**, the main artifact, **`-sources`**
     and **`-javadoc`** jars, and a matching **`.asc` signature** for every
     file (the portal flags missing/invalid signatures during validation).
   - POM metadata looks right: `name`, `description`, project `url`, Apache-2.0
     license, developer `mibrahimdev`, and SCM pointing at the repo.

## Step 3 — release (the irreversible click)

1. Once you are satisfied, click **Publish** on the deployment in the Central
   Portal. This releases it to Maven Central. **This cannot be undone.**
2. If you are *not* satisfied, click **Drop** instead. The staged deployment is
   discarded and nothing reaches Central; fix the problem and re-stage from a
   new CI run (re-run the workflow or re-push the tag).
3. Propagation to `repo1.maven.org` / `search.maven.org` typically takes
   minutes to a couple of hours.

## Step 4 — cut the GitHub Release

CI no longer auto-creates the GitHub Release (it would otherwise announce a
"release" while the artifact is still only staged). Once the Central
deployment is **Published**, cut the matching GitHub Release yourself:

```bash
gh release create v0.1.0 --title v0.1.0 --generate-notes
```

## Dry-run / re-staging without cutting a tag

The workflow also has a **`workflow_dispatch`** trigger so you can exercise the
staged flow — and confirm it reaches the portal *without releasing* — without
cutting a real tag:

- GitHub → **Actions** → **Publish to Maven Central** → **Run workflow**.

A `workflow_dispatch` run has no tag, so the version-tag assertion is skipped;
it stages the *current* catalog version. Use this to dry-run the pipeline or to
re-stage a version whose previous deployment you dropped. Because the task is
`publishToMavenCentral` (stage-only), even a dispatch run will **never**
auto-release — it always stops at the portal awaiting your **Publish** click.

## Why two steps (and not auto-release)

Recorded in [ADR 0001 — Stage to Maven Central, then release manually](adr/0001-stage-then-manual-release.md).
Short version: Central releases are irreversible, so a human verifies the
fully-assembled, signed deployment in the portal before the one click that
makes it permanent. Do **not** switch the workflow back to
`publishAndReleaseToMavenCentral`.
