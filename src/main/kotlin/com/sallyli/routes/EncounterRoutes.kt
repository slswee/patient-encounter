package com.sallyli.routes

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
            val identity = call.principal<JWTPrincipal>()!!.payload.subject
            val request = call.receive<CreateEncounterRequest>()
            val ip = call.request.local.remoteAddress
            val encounter = service.createEncounter(request, identity, ip)
            call.respond(HttpStatusCode.Created, encounter)
        }

        get("/encounters/{id}") {
            val identity = call.principal<JWTPrincipal>()!!.payload.subject
            val id = call.parameters["id"]!!
            val ip = call.request.local.remoteAddress
            val encounter = service.getEncounter(id, identity, ip)
            call.respond(encounter)
        }

        get("/encounters") {
            val fromDate = call.request.queryParameters["fromDate"]
            val toDate = call.request.queryParameters["toDate"]
            val providerId = call.request.queryParameters["providerId"]
            val patientId = call.request.queryParameters["patientId"]
            val encounters = service.listEncounters(fromDate, toDate, providerId, patientId)
            call.respond(encounters)
        }
    }
}
