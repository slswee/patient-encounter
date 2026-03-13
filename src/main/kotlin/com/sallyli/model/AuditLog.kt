package com.sallyli.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditLog(
    val auditId: String,
    val action: String,          // "CREATE" | "READ" | "AUTH_FAILURE"
    val encounterId: String?,
    val accessedBy: String,
    val accessedAt: String,
    val ipAddress: String?,
    val reason: String? = null   // "NO_CREDENTIALS" | "INVALID_TOKEN" | "INVALID_CLIENT"
)
