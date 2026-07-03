package dev.sharingan

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.sharingan.ui.SharinganScreen
import java.util.Locale

/**
 * Hosts the Sharingan log browser. Launched by tapping the capture
 * notification or by calling [show]; apps never need to declare it —
 * it ships in the library manifest.
 *
 * The logger is a locale-neutral surface: always English + LTR regardless of
 * the host's per-app locale, and it never leaks its locale back into the host
 * (issue #38). [attachBaseContext] pins an English/LTR configuration for this
 * activity via the non-mutating [Context.createConfigurationContext]; that call
 * has the side effect of resetting the process-global [LocaleList] default to
 * English, so we snapshot the host's default and restore it — both right after,
 * and again in [onDestroy] — leaving the host's locale untouched.
 */
public class SharinganActivity : ComponentActivity() {

    private var hostLocales: LocaleList? = null

    override fun attachBaseContext(newBase: Context) {
        hostLocales = LocaleList.getDefault()
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }
        // Non-mutating: createConfigurationContext, never resources.updateConfiguration.
        super.attachBaseContext(newBase.createConfigurationContext(config))
        hostLocales?.let(LocaleList::setDefault)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SharinganScreen()
        }
    }

    override fun onDestroy() {
        hostLocales?.let(LocaleList::setDefault)
        super.onDestroy()
    }
}
