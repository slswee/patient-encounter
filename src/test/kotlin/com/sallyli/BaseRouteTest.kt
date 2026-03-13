package com.sallyli

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.assertEquals

abstract class BaseRouteTest {

    protected val provider1Body = """{"patientId":"P123","providerId":"provider-001","encounterDate":"2026-03-10T10:00:00Z","encounterType":"INITIAL_ASSESSMENT","clinicalData":{"notes":"First visit"}}"""
    protected val provider2Body = """{"patientId":"P456","providerId":"provider-002","encounterDate":"2026-03-10T10:00:00Z","encounterType":"FOLLOW_UP","clinicalData":{"notes":"Follow up"}}"""

    protected fun ApplicationTestBuilder.setup() {
        environment {
            config = MapApplicationConfig(
                "api.keys" to "test-api-key-provider-001:provider-001,test-api-key-provider-002:provider-002,test-api-key-admin-001:admin-001",
                "api.roles" to "provider-001:provider,provider-002:provider,admin-001:admin",
                "jwt.secret" to "test-secret-for-testing"
            )
        }
        application { module() }
    }

    protected suspend fun ApplicationTestBuilder.getToken(
        clientId: String = "provider-001",
        clientSecret: String = "test-api-key-provider-001"
    ): String {
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=$clientId&client_secret=$clientSecret&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.OK, response.status, "Token request failed: ${response.bodyAsText()}")
        return Json.decodeFromString<JsonObject>(response.bodyAsText())["access_token"]!!.jsonPrimitive.content
    }

    protected suspend fun ApplicationTestBuilder.getAdminToken() =
        getToken(clientId = "admin-001", clientSecret = "test-api-key-admin-001")

    protected suspend fun ApplicationTestBuilder.createEncounter(token: String, body: String): String {
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(body)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString<JsonObject>(response.bodyAsText())["encounterId"]!!.jsonPrimitive.content
    }
}
