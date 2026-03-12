package com.sallyli

import com.sallyli.model.CreateEncounterRequest
import com.sallyli.plugins.configureStatusPages
import com.sallyli.repository.InMemoryAuditRepository
import com.sallyli.repository.InMemoryEncounterRepository
import com.sallyli.security.configureSecurity
import com.sallyli.service.EncounterService
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val encounterRepo = InMemoryEncounterRepository()
    val auditRepo = InMemoryAuditRepository()
    val service = EncounterService(encounterRepo, auditRepo)

    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureValidation()
    configureRouting(service)
}

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<CreateEncounterRequest> { req ->
            val errors = buildList {
                if (req.patientId.isBlank()) add("patientId is required")
                if (req.providerId.isBlank()) add("providerId is required")
                if (!isValidIso8601(req.encounterDate)) add("encounterDate must be ISO-8601")
                if (req.clinicalData.isEmpty()) add("clinicalData must not be empty")
            }
            if (errors.isEmpty()) ValidationResult.Valid
            else ValidationResult.Invalid(errors.joinToString("; "))
        }
    }
}

private fun isValidIso8601(date: String): Boolean = try {
    java.time.Instant.parse(date)
    true
} catch (e: Exception) {
    try {
        java.time.LocalDate.parse(date)
        true
    } catch (e2: Exception) {
        false
    }
}
