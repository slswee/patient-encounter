package com.sallyli.repository

import com.sallyli.model.Encounter
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
        return store.values.filter { encounter ->
            (fromDate == null || encounter.encounterDate >= fromDate) &&
            (toDate == null || encounter.encounterDate <= toDate) &&
            (providerId == null || encounter.providerId == providerId) &&
            (patientId == null || encounter.patientId == patientId)
        }.sortedByDescending { it.encounterDate }
    }
}
