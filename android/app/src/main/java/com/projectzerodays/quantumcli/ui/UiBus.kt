package com.projectzerodays.quantumcli.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight event bus between chat intents and the pages they drive. */
object UiBus {
    /** CIDR to scan when the Cameras page next resumes (null = local /24). */
    val cameraScanRequest = MutableStateFlow<String?>(null)

    /** Bump (increment) to request the floating chat overlay to open. */
    val chatOpenRequest = MutableStateFlow(0)
}
