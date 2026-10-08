package com.projectzerodays.quantumcli.ops

import org.json.JSONObject

/** PhishEngine parity — deterministic staging (QBrain body, no AI needed). */
object Phish {

    fun launch(targetEmail: String, subject: String = "Payroll"): JSONObject {
        val body = QBrain.phishBody(targetEmail, subject)
        val staged = com.projectzerodays.quantumcli.c2.C2State.let {
            val f = java.io.File(it.qcliDir, "phish_${Net.randHex(3)}.txt")
            f.writeText("to: $targetEmail\nsubject: $subject\n\n$body")
            f.absolutePath
        }
        com.projectzerodays.quantumcli.c2.C2State.audit("ATK", "PHISH staged -> $targetEmail")
        return JSONObject()
            .put("email", targetEmail)
            .put("subject", subject)
            .put("body", body)
            .put("staged", staged)
    }

    fun social(params: JSONObject): JSONObject =
        JSONObject().put("pretext", QBrain.summary(params))
}
