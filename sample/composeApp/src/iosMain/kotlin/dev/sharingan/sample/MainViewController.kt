package dev.sharingan.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.sharingan.SharinganViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Entry point for the iOS sample's `UIViewControllerRepresentable`. */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(openSharingan = { presentSharingan() })
}

private fun presentSharingan() {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.keyWindow }
    var top = window?.rootViewController ?: return
    while (true) {
        top = top.presentedViewController ?: break
    }
    top.presentViewController(SharinganViewController(), animated = true, completion = null)
}
