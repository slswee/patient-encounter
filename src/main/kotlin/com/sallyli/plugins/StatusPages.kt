package com.sallyli.plugins

import com.sallyli.model.ErrorResponse
import com.sallyli.service.ForbiddenException
import com.sallyli.service.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Validation failed", cause.reasons))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Resource not found"))
        }
        exception<ForbiddenException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unexpected error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("An unexpected error occurred"))
        }
    }
}
