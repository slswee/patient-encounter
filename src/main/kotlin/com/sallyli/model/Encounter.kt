package com.sallyli.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class EncounterType { INITIAL_ASSESSMENT, FOLLOW_UP, TREATMENT_SESSION }

@Serializable
data class EncounterMetadata(
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String
)

@Serializable
data class Encounter(
    val encounterId: String,
    val patientId: String,
    val providerId: String,
    val encounterDate: String,
    val encounterType: EncounterType,
    val clinicalData: JsonObject,
    val metadata: EncounterMetadata
)

@Serializable
data class CreateEncounterRequest(
    val patientId: String,
    val providerId: String,
    val encounterDate: String,
    val encounterType: EncounterType,
    val clinicalData: JsonObject
)

@Serializable
data class ErrorResponse(
    val message: String,
    val details: List<String> = emptyList()
)
