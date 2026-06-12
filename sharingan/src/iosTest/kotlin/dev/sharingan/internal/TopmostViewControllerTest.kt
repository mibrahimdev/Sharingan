package dev.sharingan.internal

import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertTrue

class TopmostViewControllerTest {

    @Test
    fun `When root has no presentations Then root is the topmost`() {
        val root = UIViewController(nibName = null, bundle = null)

        // K/N wraps each ObjC return in a fresh Kotlin peer; use ObjC isEqual
        // (pointer equality for NSObject) instead of Kotlin reference identity.
        assertTrue(root.isEqual(topmostViewController(root)))
    }

    /**
     * Simulates a two-level presentation chain using a stub subclass that
     * overrides the ObjC [presentedViewController] selector without requiring
     * a live UIApplication (which the K/N test binary does not initialise).
     */
    @Test
    fun `Given a two-deep presented chain When resolving Then the deepest controller wins`() {
        val second = UIViewController(nibName = null, bundle = null)
        val first = StubPresentingViewController(second)
        val root = StubPresentingViewController(first)

        assertTrue(second.isEqual(topmostViewController(root)))
    }

    /**
     * UIViewController stub whose [presentedViewController] returns a fixed
     * child, exercising the traversal loop in [topmostViewController] without
     * a live UIApplication.
     */
    private class StubPresentingViewController(
        private val presented: UIViewController,
    ) : UIViewController(nibName = null, bundle = null) {
        override fun presentedViewController(): UIViewController? = presented
    }
}
