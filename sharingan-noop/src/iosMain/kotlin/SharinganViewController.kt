package dev.sharingan

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

/**
 * No-op twin: returns an empty view controller so Swift call sites compile
 * and run against the release framework without UI or Compose payload.
 */
@ObjCName("SharinganViewController", swiftName = "SharinganViewController")
@OptIn(ExperimentalObjCName::class)
public fun SharinganViewController(): UIViewController = UIViewController(nibName = null, bundle = null)

/**
 * No-op twin: release builds have no browser to present. Safe to call from
 * any thread; no-ops unconditionally.
 *
 * No-ops when:
 * - no key window is attached (app not yet foregrounded, or running in an
 *   extension context without a scene);
 * - a presentation or dismissal is already in flight on the resolved
 *   topmost controller (prevents UIKit's "attempt to present … already
 *   presenting" warning on rapid double-calls).
 *
 * ```swift
 * // Swift (global function exported from your shared framework)
 * presentSharingan(animated: true)
 * ```
 */
@ObjCName("presentSharingan", swiftName = "presentSharingan")
@OptIn(ExperimentalObjCName::class)
public fun presentSharingan(animated: Boolean = true) {
}
