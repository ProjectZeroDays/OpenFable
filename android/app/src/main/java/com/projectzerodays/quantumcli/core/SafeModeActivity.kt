package com.projectzerodays.quantumcli.core

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.MainActivity
import com.projectzerodays.quantumcli.ui.ClassificationFrame
import com.projectzerodays.quantumcli.ui.theme.QuantumTheme

/**
 * Safe mode: shown by QuantumApp when [CrashLoopGuard.shouldSafeBoot] trips.
 * Core diagnostics only — subsystem boot phases, healing hooks, and the C2
 * server are deliberately NOT started here. Clearing the journal is an
 * explicit operator action.
 */
class SafeModeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(0x8B, 0x1A, 0x1A)
        window.navigationBarColor = android.graphics.Color.rgb(0x8B, 0x1A, 0x1A)
        setContent {
            QuantumTheme {
                ClassificationFrame {
                    Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("SAFE MODE", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "The app failed to boot cleanly ${safeBoots()} times in a row. " +
                                "Subsystems are disabled. Diagnose below, then restart normally.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Boot journal (newest 20 events):",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            CrashLoopGuard.summary(),
                            style = MaterialTheme.typography.bodySmall
                                .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                CrashLoopGuard.resetJournal()
                                startActivity(Intent(this@SafeModeActivity, MainActivity::class.java))
                                finish()
                            }) { Text("Restart normally") }
                            OutlinedButton(onClick = { CrashLoopGuard.resetJournal() }) {
                                Text("Clear journal only")
                            }
                        }
                    }
                }
                }
            }
        }
    }

    private fun safeBoots(): Int {
        val summary = CrashLoopGuard.summary()
        return summary.split('\n').count { it.contains("CRASH") || it.contains("BOOTFAIL") }
    }
}
