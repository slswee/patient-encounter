package com.sallyli.security

import com.auth0.jwt.JWT
import com.sallyli.model.AuditLog
import com.sallyli.repository.AuditRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.time.Instant
import java.util.UUID

fun parseApiKeys(raw: String): Map<String, String> =
    raw.split(",")
        .filter { it.contains(":") }
        .associate { entry ->
            val (key, identity) = entry.trim().split(":", limit = 2)
            key to identity
        }

// Decode (without verifying) to recover whatever identity is claimed in a bad token
private fun tryDecodeSubject(authHeader: String?): String {
    val token = authHeader?.removePrefix("Bearer ")?.trim() ?: return "unknown"
    return try {
        JWT.decode(token).subject ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

fun Application.configureSecurity(jwtConfig: JwtConfig, denylist: TokenDenylist, auditRepo: AuditRepository) {
    install(Authentication) {
        jwt("api-key") {
            verifier(jwtConfig.verifier)
            validate { credential ->
                val jti = credential.payload.id
                val subject = credential.payload.subject
                if (!subject.isNullOrBlank() && !jti.isNullOrBlank() && !denylist.isRevoked(jti)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                val authHeader = call.request.headers["Authorization"]
                val reason = if (authHeader == null) "NO_CREDENTIALS" else "INVALID_TOKEN"
                val identity = if (authHeader == null) "unknown" else tryDecodeSubject(authHeader)

                auditRepo.save(
                    AuditLog(
                        auditId = UUID.randomUUID().toString(),
                        action = "AUTH_FAILURE",
                        encounterId = null,
                        accessedBy = identity,
                        accessedAt = Instant.now().toString(),
                        ipAddress = call.request.local.remoteAddress,
                        reason = reason
                    )
                )
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
            }
        }
    }
}
