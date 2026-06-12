package dev.sharingan.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.sharingan.presentSharingan
import platform.UIKit.UIViewController

/** Entry point for the iOS sample's `UIViewControllerRepresentable`. */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(openSharingan = { presentSharingan() })
}
