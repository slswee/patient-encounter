package com.sallyli.service

import com.sallyli.model.AuditLog
import com.sallyli.model.CallerContext
import com.sallyli.model.CreateEncounterRequest
import com.sallyli.model.Encounter
import com.sallyli.model.EncounterMetadata
import com.sallyli.repository.AuditRepository
import com.sallyli.repository.EncounterRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class NotFoundException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)

class EncounterService(
    private val encounters: EncounterRepository,
    private val audit: AuditRepository
) {
    private val logger = LoggerFactory.getLogger(EncounterService::class.java)

    fun createEncounter(request: CreateEncounterRequest, caller: CallerContext, ip: String?): Encounter {
        if (!caller.isAdmin && request.providerId != caller.identity) {
            throw ForbiddenException("Providers may only create encounters for themselves")
        }

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
                createdBy = caller.identity
            )
        )
        val saved = encounters.save(encounter)
        logger.info("Encounter created: encounterId={}, providerId={}", saved.encounterId, saved.providerId)

        audit.save(
            AuditLog(
                auditId = UUID.randomUUID().toString(),
                action = "CREATE",
                encounterId = saved.encounterId,
                accessedBy = caller.identity,
                accessedAt = now,
                ipAddress = ip
            )
        )
        return saved
    }

    fun getEncounter(id: String, caller: CallerContext, ip: String?): Encounter {
        val encounter = encounters.findById(id) ?: throw NotFoundException("Encounter not found: $id")

        if (!caller.isAdmin && encounter.providerId != caller.identity) {
            throw ForbiddenException("Access denied to encounter $id")
        }

        logger.info("Encounter retrieved: encounterId={}, accessedBy={}", id, caller.identity)

        audit.save(
            AuditLog(
                auditId = UUID.randomUUID().toString(),
                action = "READ",
                encounterId = id,
                accessedBy = caller.identity,
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
        patientId: String?,
        caller: CallerContext,
        ip: String?
    ): List<Encounter> {
        // Non-admin callers are silently restricted to their own encounters
        // regardless of any providerId filter they supply
        val effectiveProviderId = if (caller.isAdmin) providerId else caller.identity
        val results = encounters.findAll(fromDate, toDate, effectiveProviderId, patientId)

        // Write one audit entry per disclosed encounter so compliance queries can
        // answer "who accessed patient X's records?" at the individual record level
        val now = Instant.now().toString()
        results.forEach { encounter ->
            audit.save(
                AuditLog(
                    auditId = UUID.randomUUID().toString(),
                    action = "LIST",
                    encounterId = encounter.encounterId,
                    accessedBy = caller.identity,
                    accessedAt = now,
                    ipAddress = ip
                )
            )
        }
        return results
    }

    fun getAuditLogs(fromDate: String?, toDate: String?, caller: CallerContext): List<AuditLog> {
        val effectiveAccessedBy = if (caller.isAdmin) null else caller.identity
        return audit.findAll(fromDate, toDate, effectiveAccessedBy)
    }
}
