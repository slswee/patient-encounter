package com.sallyli

import com.sallyli.routes.auditRoutes
import com.sallyli.routes.encounterRoutes
import com.sallyli.service.EncounterService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(service: EncounterService) {
    routing {
        encounterRoutes(service)
        auditRoutes(service)
    }
}
