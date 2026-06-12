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

/** No-op twin: release builds have no browser to present. */
@ObjCName("presentSharingan", swiftName = "presentSharingan")
@OptIn(ExperimentalObjCName::class)
public fun presentSharingan(animated: Boolean = true) {
}
