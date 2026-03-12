package com.sallyli.service

import com.sallyli.model.AuditLog
import com.sallyli.model.CreateEncounterRequest
import com.sallyli.model.Encounter
import com.sallyli.model.EncounterMetadata
import com.sallyli.repository.AuditRepository
import com.sallyli.repository.EncounterRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class NotFoundException(message: String) : Exception(message)

class EncounterService(
    private val encounters: EncounterRepository,
    private val audit: AuditRepository
) {
    private val logger = LoggerFactory.getLogger(EncounterService::class.java)

    fun createEncounter(request: CreateEncounterRequest, createdBy: String, ip: String?): Encounter {
        val now = Instant.now().toString()
        val encounter = Encounter(
            encounterId = UUID.randomUUID().toString(),
            patientId = request.patientId,
            providerId = request.providerId,
            encounterDate = request.encounterDate,
            encounterType = request.encounterType,
            clinicalData = request.clinicalData,
            metadata = EncounterMetadata(
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            )
        )
        val saved = encounters.save(encounter)
        logger.info("Encounter created: encounterId={}, providerId={}", saved.encounterId, saved.providerId)

        audit.save(
            AuditLog(
                auditId = UUID.randomUUID().toString(),
                action = "CREATE",
                encounterId = saved.encounterId,
                accessedBy = createdBy,
                accessedAt = now,
                ipAddress = ip
            )
        )
        return saved
    }

    fun getEncounter(id: String, accessedBy: String, ip: String?): Encounter {
        val encounter = encounters.findById(id) ?: throw NotFoundException("Encounter not found: $id")
        logger.info("Encounter retrieved: encounterId={}, accessedBy={}", id, accessedBy)

        audit.save(
            AuditLog(
                auditId = UUID.randomUUID().toString(),
                action = "READ",
                encounterId = id,
                accessedBy = accessedBy,
                accessedAt = Instant.now().toString(),
                ipAddress = ip
            )
        )
        return encounter
    }

    fun listEncounters(
        fromDate: String?,
        toDate: String?,
        providerId: String?,
        patientId: String?
    ): List<Encounter> {
        return encounters.findAll(fromDate, toDate, providerId, patientId)
    }

    fun getAuditLogs(fromDate: String?, toDate: String?): List<AuditLog> {
        return audit.findAll(fromDate, toDate)
    }
}
