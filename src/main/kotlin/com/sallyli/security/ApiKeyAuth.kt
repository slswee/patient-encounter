package com.sallyli.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun parseApiKeys(raw: String): Map<String, String> =
    raw.split(",")
        .filter { it.contains(":") }
        .associate { entry ->
            val (key, identity) = entry.trim().split(":", limit = 2)
            key to identity
        }

fun Application.configureSecurity(jwtConfig: JwtConfig, denylist: TokenDenylist) {
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
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
            }
        }
    }
}
