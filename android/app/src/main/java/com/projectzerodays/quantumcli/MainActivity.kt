package com.projectzerodays.quantumcli

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import com.projectzerodays.quantumcli.ui.ClassificationFrame
import com.projectzerodays.quantumcli.ui.ClassificationRed
import com.projectzerodays.quantumcli.ui.QuantumUi
import com.projectzerodays.quantumcli.ui.theme.QuantumTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System bars match the classification banner — the marking runs edge to edge.
        window.statusBarColor = android.graphics.Color.rgb(0x8B, 0x1A, 0x1A)
        window.navigationBarColor = android.graphics.Color.rgb(0x8B, 0x1A, 0x1A)
        requestNotificationPermissionIfNeeded()
        setContent {
            QuantumTheme {
                ClassificationFrame { QuantumUi() }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}
