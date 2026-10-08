package com.projectzerodays.quantumcli.selfhealing

/** Health verdict rendered by every widget on its badge. */
sealed class WidgetStatus {
    object GREEN : WidgetStatus()
    data class AMBER(val healHint: String) : WidgetStatus()
    data class RED(val reason: String) : WidgetStatus()
    data class GREY(val reason: String) : WidgetStatus()
}
