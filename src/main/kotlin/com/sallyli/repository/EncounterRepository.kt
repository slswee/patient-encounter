package com.sallyli.repository

import com.sallyli.model.Encounter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

interface EncounterRepository {
    fun save(encounter: Encounter): Encounter
    fun findById(id: String): Encounter?
    fun findAll(
        fromDate: String? = null,
        toDate: String? = null,
        providerId: String? = null,
        patientId: String? = null
    ): List<Encounter>
}

class InMemoryEncounterRepository : EncounterRepository {
    private val store = ConcurrentHashMap<String, Encounter>()

    override fun save(encounter: Encounter): Encounter {
        store[encounter.encounterId] = encounter
        return encounter
    }

    override fun findById(id: String): Encounter? = store[id]

    override fun findAll(
        fromDate: String?,
        toDate: String?,
        providerId: String?,
        patientId: String?
    ): List<Encounter> {
        val from = fromDate?.toInstantFloor()
        val to = toDate?.toInstantCeiling()
        return store.values.filter { encounter ->
            val date = encounter.encounterDate.toInstantFloor()
            (from == null || date >= from) &&
            (to == null || date <= to) &&
            (providerId == null || encounter.providerId == providerId) &&
            (patientId == null || encounter.patientId == patientId)
        }.sortedByDescending { it.encounterDate }
    }
}

// date-only "2026-03-13" → 2026-03-13T00:00:00Z; full timestamp parsed as-is
private fun String.toInstantFloor(): Instant =
    try { Instant.parse(this) }
    catch (_: Exception) { LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant() }

// date-only "2026-03-13" → 2026-03-13T23:59:59.999999999Z; full timestamp parsed as-is
private fun String.toInstantCeiling(): Instant =
    try { Instant.parse(this) }
    catch (_: Exception) { LocalDate.parse(this).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1) }
