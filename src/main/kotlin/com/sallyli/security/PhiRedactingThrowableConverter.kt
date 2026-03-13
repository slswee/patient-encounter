package com.sallyli.security

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class PhiRedactingThrowableConverter : ExtendedThrowableProxyConverter() {
    private val redactor = PhiRedactingConverter()

    override fun convert(event: ILoggingEvent): String = redactor.redact(super.convert(event))
}
