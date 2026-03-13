package com.sallyli

import com.sallyli.repository.AuditRepository
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
    roles: Map<String, String>,
    jwtConfig: JwtConfig,
    denylist: TokenDenylist,
    auditRepo: AuditRepository
) {
    routing {
        authRoutes(validKeys, roles, jwtConfig, denylist, auditRepo)
        encounterRoutes(service)
        auditRoutes(service)
    }
}
