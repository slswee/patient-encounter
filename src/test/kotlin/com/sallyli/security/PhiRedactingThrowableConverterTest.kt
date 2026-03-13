package com.sallyli.security

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.LoggingEvent
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhiRedactingThrowableConverterTest {

    private val converter = PhiRedactingThrowableConverter().also { it.start() }
    private val logger = LoggerFactory.getLogger(PhiRedactingThrowableConverterTest::class.java) as Logger

    private fun eventWithThrowable(throwable: Throwable): LoggingEvent {
        return LoggingEvent(
            PhiRedactingThrowableConverterTest::class.java.name,
            logger,
            Level.ERROR,
            "error occurred",
            throwable,
            null
        )
    }

    @Test
    fun testRedactsPHIFromExceptionMessage() {
        val ex = RuntimeException("""patientId="P-SECRET-123" caused an error""")
        val output = converter.convert(eventWithThrowable(ex))
        assertFalse(output.contains("P-SECRET-123"), "PHI must not appear in stack trace output")
        assertTrue(output.contains("[REDACTED]"))
    }

    @Test
    fun testRedactsPHIInCauseChain() {
        val cause = IllegalArgumentException(""""patientId": "P-NESTED-456"""")
        val ex = RuntimeException("wrapper exception", cause)
        val output = converter.convert(eventWithThrowable(ex))
        assertFalse(output.contains("P-NESTED-456"), "PHI in cause chain must be redacted")
        assertTrue(output.contains("[REDACTED]"))
    }

    @Test
    fun testNonPhiStackTraceIsUnchanged() {
        val ex = RuntimeException("Encounter not found: encounterId=abc-123")
        val output = converter.convert(eventWithThrowable(ex))
        assertTrue(output.contains("Encounter not found"))
        assertTrue(output.contains("encounterId=abc-123"))
        assertFalse(output.contains("[REDACTED]"))
    }

    @Test
    fun testEmptyOutputWhenNoException() {
        val event = LoggingEvent(
            PhiRedactingThrowableConverterTest::class.java.name,
            logger,
            Level.ERROR,
            "no throwable here",
            null,
            null
        )
        val output = converter.convert(event)
        assertTrue(output.isEmpty() || output.isBlank())
    }
}
