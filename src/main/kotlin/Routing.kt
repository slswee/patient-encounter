package com.sallyli

import com.sallyli.routes.auditRoutes
import com.sallyli.routes.authRoutes
import com.sallyli.routes.encounterRoutes
import com.sallyli.security.JwtConfig
import com.sallyli.security.TokenDenylist
import com.sallyli.service.EncounterService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    service: EncounterService,
    validKeys: Map<String, String>,
    jwtConfig: JwtConfig,
    denylist: TokenDenylist
) {
    routing {
        authRoutes(validKeys, jwtConfig, denylist)
        encounterRoutes(service)
        auditRoutes(service)
    }
}
