package com.sallyli.service

import com.sallyli.model.CallerContext
import com.sallyli.model.CreateEncounterRequest
import com.sallyli.model.EncounterType
import com.sallyli.repository.InMemoryAuditRepository
import com.sallyli.repository.InMemoryEncounterRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EncounterServiceTest {

    private val encounterRepo = InMemoryEncounterRepository()
    private val auditRepo = InMemoryAuditRepository()
    private val service = EncounterService(encounterRepo, auditRepo)

    private val provider1 = CallerContext("provider-001", "provider")
    private val provider2 = CallerContext("provider-002", "provider")
    private val admin = CallerContext("admin-001", "admin")

    private val clinicalData = buildJsonObject { put("notes", "Test visit") }

    private fun requestFor(providerId: String) = CreateEncounterRequest(
        patientId = "P123",
        providerId = providerId,
        encounterDate = "2026-03-10T10:00:00Z",
        encounterType = EncounterType.INITIAL_ASSESSMENT,
        clinicalData = clinicalData
    )

    // ── createEncounter ───────────────────────────────────────────────────────

    @Test
    fun testProviderCanCreateOwnEncounter() {
        val encounter = service.createEncounter(requestFor("provider-001"), provider1, null)
        assertEquals("provider-001", encounter.providerId)
        assertEquals("provider-001", encounter.metadata.createdBy)
    }

    @Test
    fun testProviderCannotCreateForAnotherProvider() {
        assertFailsWith<ForbiddenException> {
            service.createEncounter(requestFor("provider-002"), provider1, null)
        }
    }

    @Test
    fun testAdminCanCreateForAnyProvider() {
        val encounter = service.createEncounter(requestFor("provider-001"), admin, null)
        assertNotNull(encounter.encounterId)
    }

    @Test
    fun testCreateWritesAuditLog() {
        service.createEncounter(requestFor("provider-001"), provider1, "10.0.0.1")
        val logs = auditRepo.findAll()
        assertEquals(1, logs.size)
        assertEquals("CREATE", logs[0].action)
        assertEquals("provider-001", logs[0].accessedBy)
        assertEquals("10.0.0.1", logs[0].ipAddress)
    }

    // ── getEncounter ──────────────────────────────────────────────────────────

    @Test
    fun testProviderCanGetOwnEncounter() {
        val created = service.createEncounter(requestFor("provider-001"), provider1, null)
        val found = service.getEncounter(created.encounterId, provider1, null)
        assertEquals(created.encounterId, found.encounterId)
    }

    @Test
    fun testProviderCannotGetAnotherProvidersEncounter() {
        val created = service.createEncounter(requestFor("provider-001"), provider1, null)
        assertFailsWith<ForbiddenException> {
            service.getEncounter(created.encounterId, provider2, null)
        }
    }

    @Test
    fun testAdminCanGetAnyEncounter() {
        val created = service.createEncounter(requestFor("provider-001"), provider1, null)
        val found = service.getEncounter(created.encounterId, admin, null)
        assertEquals(created.encounterId, found.encounterId)
    }

    @Test
    fun testGetNonExistentEncounterThrowsNotFound() {
        assertFailsWith<NotFoundException> {
            service.getEncounter("does-not-exist", provider1, null)
        }
    }

    @Test
    fun testGetWritesAuditLog() {
        val created = service.createEncounter(requestFor("provider-001"), provider1, null)
        auditRepo.findAll().let { assertEquals(1, it.size) }  // only the CREATE entry so far

        service.getEncounter(created.encounterId, provider1, null)
        val logs = auditRepo.findAll()
        assertEquals(2, logs.size)
        assertEquals("READ", logs.first { it.action == "READ" }.action)
    }

    // ── listEncounters ────────────────────────────────────────────────────────

    @Test
    fun testProviderOnlySeesOwnEncounters() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        val list = service.listEncounters(null, null, null, null, provider1, null)
        assertEquals(1, list.size)
        assertEquals("provider-001", list[0].providerId)
    }

    @Test
    fun testProviderCannotOverrideProviderIdFilter() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        // provider-001 tries to pass provider-002's id as filter — should still only see own
        val list = service.listEncounters(null, null, "provider-002", null, provider1, null)
        assertEquals(1, list.size)
        assertEquals("provider-001", list[0].providerId)
    }

    @Test
    fun testAdminSeesAllEncounters() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        assertEquals(2, service.listEncounters(null, null, null, null, admin, null).size)
    }

    @Test
    fun testAdminCanFilterByProvider() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        val list = service.listEncounters(null, null, "provider-001", null, admin, null)
        assertEquals(1, list.size)
        assertEquals("provider-001", list[0].providerId)
    }

    @Test
    fun testListWritesOneAuditEntryPerEncounter() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-001"), provider1, null)
        auditRepo.findAll().let { assertEquals(2, it.size) } // only CREATE entries so far

        service.listEncounters(null, null, null, null, provider1, "10.0.0.1")

        val logs = auditRepo.findAll()
        val listEntries = logs.filter { it.action == "LIST" }
        assertEquals(2, listEntries.size)
        listEntries.forEach { entry ->
            assertEquals("provider-001", entry.accessedBy)
            assertEquals("10.0.0.1", entry.ipAddress)
        }
    }

    @Test
    fun testListWritesNoAuditEntryWhenResultIsEmpty() {
        // No encounters exist — nothing was disclosed, nothing to audit
        service.listEncounters(null, null, null, null, provider1, null)

        assertEquals(0, auditRepo.findAll().filter { it.action == "LIST" }.size)
    }

    @Test
    fun testListAuditEntriesLinkToCorrectEncounterIds() {
        val e1 = service.createEncounter(requestFor("provider-001"), provider1, null)
        val e2 = service.createEncounter(requestFor("provider-001"), provider1, null)

        service.listEncounters(null, null, null, null, provider1, null)

        val listEntries = auditRepo.findAll().filter { it.action == "LIST" }
        val auditedIds = listEntries.map { it.encounterId }.toSet()
        assertEquals(setOf(e1.encounterId, e2.encounterId), auditedIds)
    }

    // ── getAuditLogs ──────────────────────────────────────────────────────────

    @Test
    fun testProviderGetsOnlyOwnAuditLogs() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        val logs = service.getAuditLogs(null, null, provider1)
        assertEquals(1, logs.size)
        assertEquals("provider-001", logs[0].accessedBy)
    }

    @Test
    fun testProviderCannotSeeOtherProvidersAuditLogs() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        val logs = service.getAuditLogs(null, null, provider2)
        assertEquals(1, logs.size)
        assertEquals("provider-002", logs[0].accessedBy)
    }

    @Test
    fun testAdminGetsAllAuditLogs() {
        service.createEncounter(requestFor("provider-001"), provider1, null)
        service.createEncounter(requestFor("provider-002"), provider2, null)

        val logs = service.getAuditLogs(null, null, admin)
        assertEquals(2, logs.size)
    }
}
