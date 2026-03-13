package com.sallyli.routes

import com.sallyli.model.CallerContext
import com.sallyli.model.CreateEncounterRequest
import com.sallyli.service.EncounterService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.encounterRoutes(service: EncounterService) {
    authenticate("api-key") {
        post("/encounters") {
            val caller = call.callerContext()
            val request = call.receive<CreateEncounterRequest>()
            val ip = call.request.local.remoteAddress
            val encounter = service.createEncounter(request, caller, ip)
            call.respond(HttpStatusCode.Created, encounter)
        }

        get("/encounters/{id}") {
            val caller = call.callerContext()
            val id = call.parameters["id"]!!
            val ip = call.request.local.remoteAddress
            val encounter = service.getEncounter(id, caller, ip)
            call.respond(encounter)
        }

        get("/encounters") {
            val caller = call.callerContext()
            val fromDate = call.request.queryParameters["fromDate"]
            val toDate = call.request.queryParameters["toDate"]
            val providerId = call.request.queryParameters["providerId"]
            val patientId = call.request.queryParameters["patientId"]
            val encounters = service.listEncounters(fromDate, toDate, providerId, patientId, caller)
            call.respond(encounters)
        }
    }
}

internal fun io.ktor.server.application.ApplicationCall.callerContext(): CallerContext {
    val payload = principal<JWTPrincipal>()!!.payload
    return CallerContext(
        identity = payload.subject,
        role = payload.getClaim("role").asString() ?: "provider"
    )
}
