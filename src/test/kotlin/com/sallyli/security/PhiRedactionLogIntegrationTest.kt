package com.sallyli.security

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end test of PHI redaction through the real Logback pipeline.
 * Unlike PhiRedactingThrowableConverterTest (which calls converter.convert() directly),
 * these tests wire up an actual encoder with the %phi_msg/%phi_ex/%nopex pattern
 * and assert on the final formatted string that would appear in a log file.
 */
class PhiRedactionLogIntegrationTest {

    private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    private val baos = ByteArrayOutputStream()
    private lateinit var appender: OutputStreamAppender<ILoggingEvent>
    private lateinit var testLogger: Logger

    @BeforeTest
    fun setup() {
        val encoder = PatternLayoutEncoder()
        encoder.context = loggerContext
        encoder.pattern = "%phi_msg%n%phi_ex%nopex"
        encoder.start()

        appender = OutputStreamAppender()
        appender.context = loggerContext
        appender.encoder = encoder
        appender.outputStream = baos
        appender.start()

        testLogger = loggerContext.getLogger("phi.integration.test")
        testLogger.level = Level.ERROR
        testLogger.isAdditive = false
        testLogger.addAppender(appender)
    }

    @AfterTest
    fun teardown() {
        testLogger.detachAppender(appender)
        appender.stop()
    }

    private fun capturedOutput(): String = baos.toString(Charsets.UTF_8)

    // ── %phi_msg covers the log message string ────────────────────────────────

    @Test
    fun testPhiInLogMessageIsRedacted() {
        testLogger.error("""Processing failed for patientId="P-SECRET-123"""")

        val output = capturedOutput()
        assertFalse(output.contains("P-SECRET-123"), "patientId value must not appear in log output")
        assertTrue(output.contains("[REDACTED]"), "expected [REDACTED] in: $output")
    }

    // ── %phi_ex covers the exception block ────────────────────────────────────

    @Test
    fun testPhiInExceptionMessageIsRedacted() {
        testLogger.error(
            "Unexpected error",
            RuntimeException("""patientId="P-SECRET-123" triggered an illegal state""")
        )

        val output = capturedOutput()
        assertFalse(output.contains("P-SECRET-123"), "patientId value must not appear in stack trace output")
        assertTrue(output.contains("[REDACTED]"), "expected [REDACTED] in: $output")
        assertTrue(output.contains("RuntimeException"))
    }

    @Test
    fun testPhiInCauseChainIsRedacted() {
        val cause = IllegalArgumentException("""patientId="P-NESTED-456" is invalid""")
        testLogger.error("Unexpected error", RuntimeException("wrapper", cause))

        val output = capturedOutput()
        assertFalse(output.contains("P-NESTED-456"), "PHI in cause chain must not appear in log output")
        assertTrue(output.contains("[REDACTED]"), "expected [REDACTED] in: $output")
    }

    // ── %nopex prevents the default unredacted exception block ───────────────

    @Test
    fun testExceptionAppearsOnlyOnce() {
        testLogger.error(
            "Unexpected error",
            RuntimeException("""patientId="P-SECRET-123" caused error""")
        )

        val output = capturedOutput()
        // Without %nopex, Logback would append the exception a second time unredacted.
        // Verify the raw value never appears — even once.
        assertFalse(output.contains("P-SECRET-123"), "PHI must not appear even from default exception appending")
        assertTrue(output.indexOf("[REDACTED]") == output.lastIndexOf("[REDACTED]"),
            "exception block should appear exactly once (check %nopex is working)")
    }
}
