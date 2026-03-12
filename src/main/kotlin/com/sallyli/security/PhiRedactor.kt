package com.sallyli.security

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class PhiRedactingConverter : ClassicConverter() {
    private val phiPatterns = listOf(
        Regex("""("patientId"\s*:\s*)"[^"]*"""") to """$1"[REDACTED]"""",
        Regex("""(patientId=)[^\s,}&]+""") to """$1[REDACTED]"""
    )

    fun redact(message: String): String {
        var msg = message
        phiPatterns.forEach { (regex, replacement) ->
            msg = regex.replace(msg, replacement)
        }
        return msg
    }

    override fun convert(event: ILoggingEvent): String = redact(event.formattedMessage)
}
