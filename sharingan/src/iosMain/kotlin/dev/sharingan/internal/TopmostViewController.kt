package dev.sharingan.internal

import platform.UIKit.UIViewController

/**
 * The controller that can legally host a new modal presentation — UIKit
 * rejects presenting from any controller that is already presenting.
 */
internal fun topmostViewController(root: UIViewController): UIViewController {
    var top = root
    while (true) {
        top = top.presentedViewController ?: break
    }
    return top
}
