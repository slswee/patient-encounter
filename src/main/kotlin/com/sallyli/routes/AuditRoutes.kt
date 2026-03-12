package com.sallyli.routes

import com.sallyli.service.EncounterService
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.auditRoutes(service: EncounterService) {
    authenticate("api-key") {
        get("/audit/encounters") {
            val fromDate = call.request.queryParameters["fromDate"]
            val toDate = call.request.queryParameters["toDate"]
            val logs = service.getAuditLogs(fromDate, toDate)
            call.respond(logs)
        }
    }
}
