package dev.sharingan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.sharingan.ui.SharinganScreen

/**
 * Hosts the Sharingan log browser. Launched by tapping the capture
 * notification or by calling [show]; apps never need to declare it —
 * it ships in the library manifest.
 */
public class SharinganActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SharinganScreen()
        }
    }
}
