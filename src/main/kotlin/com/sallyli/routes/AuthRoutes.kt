package com.sallyli.routes

import com.sallyli.security.JwtConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long
)

fun Route.authRoutes(validKeys: Map<String, String>, jwtConfig: JwtConfig) {
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
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_client"))
            return@post
        }

        call.respond(
            TokenResponse(
                access_token = jwtConfig.generateToken(clientId),
                expires_in = jwtConfig.tokenTtlSeconds
            )
        )
    }
}
