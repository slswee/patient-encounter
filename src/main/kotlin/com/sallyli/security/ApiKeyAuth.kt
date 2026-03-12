package com.sallyli.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

data class ApiKeyPrincipal(val identity: String) : Principal

class ApiKeyAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    val validKeys: Map<String, String> = config.validKeys

    class Config(name: String?) : AuthenticationProvider.Config(name) {
        var validKeys: Map<String, String> = emptyMap()
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.headers["X-API-Key"]
        val identity = apiKey?.let { validKeys[it] }
        if (identity != null) {
            context.principal(ApiKeyPrincipal(identity))
        } else {
            val cause = if (apiKey == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            context.challenge("ApiKeyAuth", cause) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
                challenge.complete()
            }
        }
    }
}

fun AuthenticationConfig.apiKey(name: String? = null, configure: ApiKeyAuthenticationProvider.Config.() -> Unit) {
    val config = ApiKeyAuthenticationProvider.Config(name).apply(configure)
    register(ApiKeyAuthenticationProvider(config))
}

fun Application.configureSecurity() {
    install(Authentication) {
        apiKey("api-key") {
            validKeys = mapOf(
                "test-api-key-provider-001" to "provider-001",
                "test-api-key-provider-002" to "provider-002"
            )
        }
    }
}
