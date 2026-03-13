package com.sallyli.routes

import com.sallyli.model.AuditLog
import com.sallyli.repository.AuditRepository
import com.sallyli.security.JwtConfig
import com.sallyli.security.TokenDenylist
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long
)

fun Route.authRoutes(
    validKeys: Map<String, String>,
    roles: Map<String, String>,
    jwtConfig: JwtConfig,
    denylist: TokenDenylist,
    auditRepo: AuditRepository
) {
    post("/oauth/token") {
        val params = call.receiveParameters()
        val clientId = params["client_id"]
        val clientSecret = params["client_secret"]
        val grantType = params["grant_type"]

        if (grantType != "client_credentials") {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unsupported_grant_type"))
            return@post
        }

        if (clientSecret == null || clientId == null || validKeys[clientSecret] != clientId) {
            auditRepo.save(
                AuditLog(
                    auditId = UUID.randomUUID().toString(),
                    action = "AUTH_FAILURE",
                    encounterId = null,
                    accessedBy = clientId ?: "unknown",
                    accessedAt = Instant.now().toString(),
                    ipAddress = call.request.local.remoteAddress,
                    reason = "INVALID_CLIENT"
                )
            )
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_client"))
            return@post
        }

        val role = roles[clientId] ?: "provider"
        call.respond(
            TokenResponse(
                access_token = jwtConfig.generateToken(clientId, role),
                expires_in = jwtConfig.tokenTtlSeconds
            )
        )
    }

    authenticate("api-key") {
        post("/oauth/revoke") {
            val payload = call.principal<JWTPrincipal>()!!.payload
            denylist.revoke(
                jti = payload.id,
                expiresAtMs = payload.expiresAt.time
            )
            auditRepo.save(
                AuditLog(
                    auditId = UUID.randomUUID().toString(),
                    action = "REVOKE",
                    encounterId = null,
                    accessedBy = payload.subject,
                    accessedAt = Instant.now().toString(),
                    ipAddress = call.request.local.remoteAddress
                )
            )
            call.respond(HttpStatusCode.OK, mapOf("message" to "Token revoked"))
        }
    }
}
