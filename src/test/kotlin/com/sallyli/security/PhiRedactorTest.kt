package com.sallyli.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhiRedactorTest {

    private val converter = PhiRedactingConverter()

    @Test
    fun testRedactsJsonPatientId() {
        val result = converter.redact(""""patientId": "P-SECRET-123"""")
        assertFalse(result.contains("P-SECRET-123"))
        assertTrue(result.contains("[REDACTED]"))
        assertTrue(result.contains("patientId"))   // key is preserved
    }

    @Test
    fun testRedactsQueryParamPatientId() {
        val result = converter.redact("patientId=P-SECRET-123&providerId=doc-001")
        assertFalse(result.contains("P-SECRET-123"))
        assertTrue(result.contains("[REDACTED]"))
        assertTrue(result.contains("patientId"))   // key is preserved
    }

    @Test
    fun testDoesNotRedactOtherFields() {
        val msg = """"providerId": "doc-001""""
        assertEquals(msg, converter.redact(msg))
    }

    @Test
    fun testRedactsMultipleOccurrences() {
        val msg = """"patientId": "P1", "patientId": "P2""""
        val result = converter.redact(msg)
        assertFalse(result.contains("P1"))
        assertFalse(result.contains("P2"))
    }

    @Test
    fun testMessageWithNoPhiIsUnchanged() {
        val msg = "Encounter created: encounterId=abc-123, providerId=doc-001"
        assertEquals(msg, converter.redact(msg))
    }
}
