# ADR 0001: Stage to Maven Central, then release manually

- Status: Accepted
- Date: 2026-06-14
- Decision owner: maintainer (@mibrahimdev)
- Issue: [#14 — De-risk Maven Central publish](https://github.com/mibrahimdev/Sharingan/issues/14)

## Context

Sharingan publishes two artifacts to Maven Central under
`io.github.mibrahimdev` (`:sharingan` and `:sharingan-noop`) via the
vanniktech `gradle-maven-publish-plugin` (0.36.0), driven by a tag-triggered
GitHub Actions workflow (`.github/workflows/publish.yml`).

Until this decision, that workflow ran the Gradle task
`publishAndReleaseToMavenCentral`, which uploads **and** auto-releases the
deployment to Central in a single step on every `v*` tag push.

**Maven Central releases are permanent.** Once a coordinate+version is released
it can never be unpublished or overwritten. An auto-release flow means any
defect that slips past CI — a wrong coordinate, a missing/broken POM, an
unsigned or mis-signed artifact, only one of the two modules uploaded — becomes
a forever-public mistake, only fixable by burning the version number and
shipping a new one.

The plugin exposes two relevant Gradle tasks:

- `publishToMavenCentral` — uploads to a **new deployment** on the Central
  Portal and **stops**. The deployment sits in the portal awaiting a human, who
  verifies it and clicks **Publish** to release.
- `publishAndReleaseToMavenCentral` — uploads **and** releases automatically,
  end to end, with no human gate.

## Decision

The publish workflow runs **`publishToMavenCentral`** (stage-only). Releasing
the staged deployment is a deliberate manual step performed by a maintainer in
the Central Portal UI, per the checklist in [`docs/RELEASING.md`](../RELEASING.md).

We deliberately chose **staging + manual release** over the alternative of
**automated release behind an in-CI verify gate**. A CI gate can only assert
what we thought to script; the Central Portal already shows the
fully-assembled, validated, signed deployment exactly as consumers would
receive it, and a human eyeballing that — coordinates, both modules, POM,
signatures — is the strongest cheap check against an irreversible mistake. The
marginal cost is a single click per release.

## Consequences

- A tag push no longer publishes to Central. It stages, then waits. Releases
  require a human to click **Publish** in the portal — the GitHub Release is
  cut by the maintainer at the same time, not auto-created by CI.
- The workflow also gained a `workflow_dispatch` trigger so the staged flow can
  be exercised manually (dry-run) without cutting a tag. The tag trigger is
  unchanged.
- Anyone tempted to "speed things up" by switching back to
  `publishAndReleaseToMavenCentral` is re-introducing an irreversible
  auto-release; the workflow and this ADR both flag that explicitly.

## Alternatives considered

- **Automated release with an in-CI verify gate** (smoke-resolve the staged
  artifact, then auto-release if green). Rejected: still auto-releases something
  permanent, and the gate only checks what we remembered to script. The portal's
  human review is cheaper and catches the long tail.
- **Status quo (`publishAndReleaseToMavenCentral`).** Rejected: one irreversible
  shot per tag, no human in the loop, the original motivation for issue #14.
