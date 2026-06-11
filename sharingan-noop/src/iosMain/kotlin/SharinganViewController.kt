package dev.sharingan

import platform.UIKit.UIViewController

/**
 * No-op twin: returns an empty view controller so Swift call sites compile
 * and run against the release framework without UI or Compose payload.
 */
public fun SharinganViewController(): UIViewController = UIViewController(nibName = null, bundle = null)
