package com.projectzerodays.quantumcli.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** Sidebar destination pages. */
enum class Page(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    IMPLANTS("Implants", Icons.Outlined.Devices),
    CAMERAS("Cameras", Icons.Outlined.Videocam),
    NETWORK("Network", Icons.Outlined.Wifi),
    EXPLOITS("Exploits", Icons.Outlined.BugReport),
    LOOT("Loot", Icons.Outlined.Inventory2),
    CHAT("Chat", Icons.Outlined.Chat),
    AGENTS("Agents", Icons.Outlined.SmartToy),
    SKILLS("Skills", Icons.Outlined.Extension),
    TASKS("Tasks", Icons.Outlined.Checklist),
    ML("Machine Learning", Icons.Outlined.AutoAwesome),
    DEVICES("Devices", Icons.Outlined.Adb),
    HONEYPOT("Honeypot", Icons.Outlined.Sensors),
    RAT("Remote Access", Icons.Outlined.SettingsRemote),
    DORK("Google Dorking", Icons.Outlined.Search),
    OSINT("OSINT", Icons.Outlined.Search),
    WDEFENSE("Wi-Fi Defense", Icons.Outlined.Wifi),
    BLE("BLE Recon", Icons.Outlined.Sensors),
    BOTNET("Botnet", Icons.Outlined.Hub),
    PHISH("Phishing", Icons.Outlined.Lock),
    BRUTE("Brute Force", Icons.Outlined.Password),
    DDOS("DDoS / Stress", Icons.Outlined.Wifi),
    TERMINAL("Terminal", Icons.Outlined.Code),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
