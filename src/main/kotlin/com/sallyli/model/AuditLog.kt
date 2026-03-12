package com.sallyli.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditLog(
    val auditId: String,
    val action: String,
    val encounterId: String?,
    val accessedBy: String,
    val accessedAt: String,
    val ipAddress: String?
)
