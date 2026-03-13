package com.sallyli.repository

import com.sallyli.model.AuditLog
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditRepositoryTest {

    private val repo = InMemoryAuditRepository()

    private fun log(
        accessedAt: String = "2026-03-13T10:00:00Z",
        accessedBy: String = "provider-001",
        action: String = "READ"
    ) = AuditLog(
        auditId = UUID.randomUUID().toString(),
        action = action,
        encounterId = UUID.randomUUID().toString(),
        accessedBy = accessedBy,
        accessedAt = accessedAt,
        ipAddress = null
    )

    // ── Timestamp filter params ───────────────────────────────────────────────

    @Test
    fun testFilterByFromDateTimestamp() {
        repo.save(log(accessedAt = "2026-01-01T00:00:00Z"))
        repo.save(log(accessedAt = "2026-06-01T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-01T00:00:00Z")
        assertEquals(1, results.size)
        assertEquals("2026-06-01T00:00:00Z", results[0].accessedAt)
    }

    @Test
    fun testFilterByToDateTimestamp() {
        repo.save(log(accessedAt = "2026-01-01T00:00:00Z"))
        repo.save(log(accessedAt = "2026-06-01T00:00:00Z"))

        val results = repo.findAll(toDate = "2026-03-01T00:00:00Z")
        assertEquals(1, results.size)
        assertEquals("2026-01-01T00:00:00Z", results[0].accessedAt)
    }

    // ── Date-only filter params (the previously broken case) ──────────────────

    @Test
    fun testDateOnlyToDateIncludesEntriesFromThatDay() {
        // "2026-03-13" as toDate must include timestamp entries from that day
        repo.save(log(accessedAt = "2026-03-13T10:00:00Z"))
        repo.save(log(accessedAt = "2026-03-14T08:00:00Z"))

        val results = repo.findAll(toDate = "2026-03-13")
        assertEquals(1, results.size)
        assertEquals("2026-03-13T10:00:00Z", results[0].accessedAt)
    }

    @Test
    fun testDateOnlyFromDateIncludesEntriesFromThatDay() {
        repo.save(log(accessedAt = "2026-03-12T23:59:59Z"))
        repo.save(log(accessedAt = "2026-03-13T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-13")
        assertEquals(1, results.size)
        assertEquals("2026-03-13T00:00:00Z", results[0].accessedAt)
    }

    @Test
    fun testDateOnlyRangeIncludesFullDay() {
        repo.save(log(accessedAt = "2026-03-12T23:59:59Z"))
        repo.save(log(accessedAt = "2026-03-13T00:00:00Z"))
        repo.save(log(accessedAt = "2026-03-13T12:00:00Z"))
        repo.save(log(accessedAt = "2026-03-13T23:59:59Z"))
        repo.save(log(accessedAt = "2026-03-14T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-13", toDate = "2026-03-13")
        assertEquals(3, results.size)
        results.forEach { assertEquals("2026-03-13", it.accessedAt.take(10)) }
    }

    // ── accessedBy filter ─────────────────────────────────────────────────────

    @Test
    fun testFilterByAccessedBy() {
        repo.save(log(accessedBy = "provider-001"))
        repo.save(log(accessedBy = "provider-002"))

        val results = repo.findAll(accessedBy = "provider-001")
        assertEquals(1, results.size)
        assertEquals("provider-001", results[0].accessedBy)
    }

    @Test
    fun testNoFiltersReturnsAll() {
        repo.save(log())
        repo.save(log())
        assertEquals(2, repo.findAll().size)
    }
}
