# Consumer R8/ProGuard rules — applied automatically to any app that minifies
# while depending on the Sharingan debug artifact.
#
# Only Sharingan's own reflectively-reached surface is kept here. Compose, Ktor
# and AndroidX ship their own consumer rules inside their artifacts, so those
# are intentionally NOT repeated.

# --- Reflectively instantiated entry points -------------------------------
# These three components are declared by NAME in the library manifest and are
# created reflectively by the Android framework (no compile-time call graph
# reaches them). R8 must keep each class and its no-arg framework constructor;
# the overridden lifecycle methods survive because they override always-present
# framework classes once the type itself is kept.

# Zero-setup initializer (captures the app context at process start).
-keep class dev.sharingan.internal.SharinganInitProvider { <init>(); }

# Log-browser host, launched from the capture notification / Sharingan.show().
-keep class dev.sharingan.SharinganActivity { <init>(); }

# Capture-notification Pause/Resume action target.
-keep class dev.sharingan.internal.SharinganNotificationReceiver { <init>(); }

# --- Public facade --------------------------------------------------------
# The documented entry point apps integrate against. Direct calls keep what a
# consumer uses, but pin the facade explicitly so an aggressive consumer R8
# config can't rename or strip the supported public surface.
-keep class dev.sharingan.Sharingan { public *; }
