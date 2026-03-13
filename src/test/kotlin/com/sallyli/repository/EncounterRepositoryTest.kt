package com.sallyli.repository

import com.sallyli.model.Encounter
import com.sallyli.model.EncounterMetadata
import com.sallyli.model.EncounterType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EncounterRepositoryTest {

    private val repo = InMemoryEncounterRepository()

    private fun encounter(
        providerId: String = "provider-001",
        patientId: String = "P123",
        encounterDate: String = "2026-03-10T10:00:00Z"
    ) = Encounter(
        encounterId = UUID.randomUUID().toString(),
        patientId = patientId,
        providerId = providerId,
        encounterDate = encounterDate,
        encounterType = EncounterType.INITIAL_ASSESSMENT,
        clinicalData = buildJsonObject { put("notes", "test") },
        metadata = EncounterMetadata("2026-03-10T10:00:00Z", "2026-03-10T10:00:00Z", providerId)
    )

    @Test
    fun testSaveAndFindById() {
        val saved = repo.save(encounter())
        assertNotNull(repo.findById(saved.encounterId))
    }

    @Test
    fun testFindByIdReturnsNullForUnknown() {
        assertNull(repo.findById("does-not-exist"))
    }

    @Test
    fun testFilterByProviderId() {
        repo.save(encounter(providerId = "provider-001"))
        repo.save(encounter(providerId = "provider-002"))

        val results = repo.findAll(providerId = "provider-001")
        assertEquals(1, results.size)
        assertEquals("provider-001", results[0].providerId)
    }

    @Test
    fun testFilterByPatientId() {
        repo.save(encounter(patientId = "P111"))
        repo.save(encounter(patientId = "P222"))

        val results = repo.findAll(patientId = "P111")
        assertEquals(1, results.size)
        assertEquals("P111", results[0].patientId)
    }

    @Test
    fun testFilterByFromDate() {
        repo.save(encounter(encounterDate = "2026-01-01T00:00:00Z"))
        repo.save(encounter(encounterDate = "2026-06-01T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-01T00:00:00Z")
        assertEquals(1, results.size)
        assertEquals("2026-06-01T00:00:00Z", results[0].encounterDate)
    }

    @Test
    fun testFilterByToDate() {
        repo.save(encounter(encounterDate = "2026-01-01T00:00:00Z"))
        repo.save(encounter(encounterDate = "2026-06-01T00:00:00Z"))

        val results = repo.findAll(toDate = "2026-03-01T00:00:00Z")
        assertEquals(1, results.size)
        assertEquals("2026-01-01T00:00:00Z", results[0].encounterDate)
    }

    // ── Date-only filter params (the previously broken case) ──────────────────

    @Test
    fun testDateOnlyToDateIncludesEncountersFromThatDay() {
        // "2026-03-13" as toDate must include a timestamp stored encounter on that day
        repo.save(encounter(encounterDate = "2026-03-13T10:00:00Z"))
        repo.save(encounter(encounterDate = "2026-03-14T08:00:00Z"))

        val results = repo.findAll(toDate = "2026-03-13")
        assertEquals(1, results.size)
        assertEquals("2026-03-13T10:00:00Z", results[0].encounterDate)
    }

    @Test
    fun testDateOnlyFromDateIncludesEncountersFromThatDay() {
        repo.save(encounter(encounterDate = "2026-03-12T23:59:59Z"))
        repo.save(encounter(encounterDate = "2026-03-13T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-13")
        assertEquals(1, results.size)
        assertEquals("2026-03-13T00:00:00Z", results[0].encounterDate)
    }

    @Test
    fun testDateOnlyRangeIncludesFullDay() {
        repo.save(encounter(encounterDate = "2026-03-12T23:59:59Z"))
        repo.save(encounter(encounterDate = "2026-03-13T00:00:00Z"))
        repo.save(encounter(encounterDate = "2026-03-13T12:00:00Z"))
        repo.save(encounter(encounterDate = "2026-03-13T23:59:59Z"))
        repo.save(encounter(encounterDate = "2026-03-14T00:00:00Z"))

        val results = repo.findAll(fromDate = "2026-03-13", toDate = "2026-03-13")
        assertEquals(3, results.size)
        results.forEach { assertEquals("2026-03-13", it.encounterDate.take(10)) }
    }

    @Test
    fun testFindAllWithNoFiltersReturnsAll() {
        repo.save(encounter())
        repo.save(encounter())
        assertEquals(2, repo.findAll().size)
    }
}
