package dev.sharingan

import androidx.compose.ui.window.ComposeUIViewController
import dev.sharingan.ui.SharinganScreen
import platform.UIKit.UIViewController

/**
 * The Sharingan log browser as a `UIViewController` — present it however
 * your app likes (sheet, push, debug menu, shake gesture):
 *
 * ```swift
 * // SwiftUI
 * .sheet(isPresented: $showLogs) {
 *     SharinganViewControllerKt.SharinganViewController()
 *         .toSwiftUI() // via UIViewControllerRepresentable
 * }
 *
 * // UIKit
 * present(SharinganViewControllerKt.SharinganViewController(), animated: true)
 * ```
 *
 * iOS has no Android-style sticky notification; this view controller is the
 * platform-conventional entry point. The capture API and the screen behave
 * identically on both platforms.
 */
public fun SharinganViewController(): UIViewController = ComposeUIViewController {
    SharinganScreen()
}
