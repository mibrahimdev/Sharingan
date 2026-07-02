package dev.sharingan.sample

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sharingan.SharinganActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Regression for issue #38: opening the Sharingan logger from a host app that
 * uses per-app locales (Arabic/RTL) must not corrupt the host. The logger is a
 * locale-neutral surface — English + LTR while open — and must leave every
 * process-global locale knob exactly as the host set it.
 *
 * ```
 * ./gradlew :sample:composeApp:connectedDebugAndroidTest
 * ```
 *
 * We stand in for the AppCompat host by driving the framework per-app locale
 * (`LocaleManager`, what `AppCompatDelegate.setApplicationLocales` calls under
 * the hood on API 33+) directly in the process, since a test-APK host Activity
 * can't be launched into the app-under-test's process and the leak we assert is
 * process-global anyway. Requires API 33+; CI's `android` job (build.yml) runs
 * the connected suites on an API 34 emulator, so this regression gates PRs.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33) // LocaleManager is API 33+; skip (don't crash) below.
class LoggerLocaleLeakTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val localeManager get() = context.getSystemService(LocaleManager::class.java)

    private fun setAppLocale(tags: String) = instrumentation.runOnMainSync {
        localeManager.applicationLocales = LocaleList.forLanguageTags(tags)
    }

    @Before
    fun pinHostToArabic() {
        setAppLocale("ar")
        // LocaleManager applies asynchronously; wait until the host is Arabic.
        val deadline = System.currentTimeMillis() + 5_000
        while (localeManager.applicationLocales.toLanguageTags() != "ar" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
        assertEquals(
            "precondition: host must be pinned to Arabic",
            "ar",
            localeManager.applicationLocales.toLanguageTags(),
        )
        // The above only confirms system-server state. The process-local JVM
        // default is what SharinganActivity snapshots/restores, and it updates
        // separately — wait for it too, so we don't snapshot a stale value.
        val jvmDeadline = System.currentTimeMillis() + 2_000
        while (LocaleList.getDefault()[0].language != "ar" &&
            System.currentTimeMillis() < jvmDeadline
        ) {
            Thread.sleep(50)
        }
        assertEquals(
            "precondition: process default locale must be Arabic before launch",
            "ar",
            LocaleList.getDefault()[0].language,
        )
    }

    @After
    fun clearAppLocale() = setAppLocale("")

    @Test
    fun `Given_Arabic_host_When_logger_opens_and_closes_Then_stays_LTR_and_host_locale_untouched`() {
        // Open the logger, assert it renders LTR, then close it.
        ActivityScenario.launch(SharinganActivity::class.java).use { logger ->
            logger.onActivity { activity ->
                assertEquals(
                    "logger frame must be LTR while open",
                    View.LAYOUT_DIRECTION_LTR,
                    activity.resources.configuration.layoutDirection,
                )
            }
        }

        // Sanity: system-server per-app locale is untouched. This lives in
        // system_server, not our process, so the process-local fix can't corrupt
        // it — it passes regardless of the bug and just guards the test setup.
        assertEquals(
            "system per-app locale changed unexpectedly (test setup sanity)",
            "ar",
            localeManager.applicationLocales.toLanguageTags(),
        )
        // The REAL leak assertions: the process-global JVM defaults the buggy
        // activity flipped to English must be back to the host's Arabic.
        assertEquals("Locale.getDefault() leaked", "ar", Locale.getDefault().language)
        assertEquals("LocaleList.getDefault() leaked", "ar", LocaleList.getDefault()[0].language)
    }
}
