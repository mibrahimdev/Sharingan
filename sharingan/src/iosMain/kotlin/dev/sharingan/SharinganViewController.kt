package dev.sharingan

import androidx.compose.ui.window.ComposeUIViewController
import dev.sharingan.internal.topmostViewController
import dev.sharingan.ui.SharinganScreen
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The Sharingan log browser as a `UIViewController` — embed or present it
 * however your app likes (sheet, push, debug menu). For the common case,
 * call [presentSharingan] instead.
 *
 * A host app whose Info.plist lacks `CADisableMinimumFrameDurationOnPhone`
 * must never crash (Compose Multiplatform 1.11 aborts on a strict plist
 * check otherwise); the key remains a host-side opt-in for ProMotion/120Hz.
 */
@ObjCName("SharinganViewController", swiftName = "SharinganViewController")
@OptIn(ExperimentalObjCName::class)
public fun SharinganViewController(): UIViewController = ComposeUIViewController(configure = {
    enforceStrictPlistSanityCheck = false
}) {
    SharinganScreen()
}

/**
 * Presents the log browser over the topmost view controller of the key
 * window. Safe to call from any thread.
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
    dispatch_async(dispatch_get_main_queue()) {
        val root = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.keyWindow }
            ?.rootViewController ?: return@dispatch_async
        val top = topmostViewController(root)
        // Guard: UIKit silently swallows a present() while another
        // presentation/dismissal is already in flight; make the no-op explicit.
        if (top.presentedViewController != null ||
            top.isBeingDismissed() ||
            top.isBeingPresented()
        ) return@dispatch_async
        top.presentViewController(SharinganViewController(), animated = animated, completion = null)
    }
}
