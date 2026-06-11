package dev.sharingan.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharingan.Sharingan
import kotlinx.coroutines.launch

/**
 * Tiny host app: generates demo traffic and opens the Sharingan browser.
 * [openSharingan] is platform-wired (Activity launch / VC presentation).
 */
@Composable
fun App(openSharingan: () -> Unit) {
    val scope = rememberCoroutineScope()
    val events by Sharingan.events.collectAsState()
    var rounds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        DemoTraffic.runRound()
        rounds++
    }

    MaterialTheme {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sharingan sample", style = MaterialTheme.typography.headlineSmall)
                Text("${events.size} events captured · $rounds rounds")
                Button(onClick = {
                    scope.launch {
                        DemoTraffic.runRound()
                        rounds++
                    }
                }) {
                    Text("Simulate IoT traffic")
                }
                OutlinedButton(onClick = openSharingan) {
                    Text("Open Sharingan")
                }
                Text(
                    "Android: tap the capture notification, too.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
