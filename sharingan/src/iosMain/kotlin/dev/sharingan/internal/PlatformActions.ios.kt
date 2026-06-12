package dev.sharingan.internal

import platform.Foundation.NSOperationQueue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

internal actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

internal actual fun shareText(text: String) {
    NSOperationQueue.mainQueue.addOperationWithBlock {
        val root = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.keyWindow }
            ?.rootViewController ?: return@addOperationWithBlock
        val presenter = topmostViewController(root)
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        // iPad requires a popover anchor; anchor to the presenter's view.
        controller.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}
