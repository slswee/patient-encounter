package com.sallyli

import com.sallyli.security.PhiRedactingConverter
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {

    private val validClientId = "provider-001"
    private val validClientSecret = "test-api-key-provider-001"
    private val validBody = """{"patientId":"P123","providerId":"provider-001","encounterDate":"2026-03-10T10:00:00Z","encounterType":"INITIAL_ASSESSMENT","clinicalData":{"notes":"First visit"}}"""

    private fun ApplicationTestBuilder.setup() {
        environment {
            config = MapApplicationConfig(
                "api.keys" to "test-api-key-provider-001:provider-001,test-api-key-provider-002:provider-002",
                "jwt.secret" to "test-secret-for-testing"
            )
        }
        application { module() }
    }

    private suspend fun ApplicationTestBuilder.getToken(
        clientId: String = validClientId,
        clientSecret: String = validClientSecret
    ): String {
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=$clientId&client_secret=$clientSecret&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.OK, response.status, "Token request failed: ${response.bodyAsText()}")
        return Json.decodeFromString<JsonObject>(response.bodyAsText())["access_token"]!!.jsonPrimitive.content
    }

    @Test
    fun testTokenEndpoint() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=$validClientId&client_secret=$validClientSecret&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["access_token"]!!.jsonPrimitive.content.isNotEmpty())
        assertEquals("Bearer", body["token_type"]!!.jsonPrimitive.content)
        assertEquals(900, body["expires_in"]!!.jsonPrimitive.long)
    }

    @Test
    fun testTokenEndpointInvalidSecret() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=$validClientId&client_secret=wrong-secret&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testTokenEndpointWrongGrantType() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=$validClientId&client_secret=$validClientSecret&grant_type=password")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testCreateEncounterSuccess() = testApplication {
        setup()
        val token = getToken()
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body.containsKey("encounterId"))
        assertTrue(body["encounterId"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun testCreateEncounterValidationFailure() = testApplication {
        setup()
        val token = getToken()
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody("""{"patientId":"","providerId":"","encounterDate":"not-a-date","encounterType":"INITIAL_ASSESSMENT","clinicalData":{}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("Validation failed", body["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun testGetEncounterFound() = testApplication {
        setup()
        val token = getToken()

        val createResponse = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(validBody)
        }
        val encounterId = Json.decodeFromString<JsonObject>(createResponse.bodyAsText())["encounterId"]!!.jsonPrimitive.content

        val getResponse = client.get("/encounters/$encounterId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals(encounterId, Json.decodeFromString<JsonObject>(getResponse.bodyAsText())["encounterId"]!!.jsonPrimitive.content)
    }

    @Test
    fun testGetEncounterNotFound() = testApplication {
        setup()
        val token = getToken()
        val response = client.get("/encounters/nonexistent-id-12345") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testAuditTrail() = testApplication {
        setup()
        val token = getToken()

        val createResponse = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(validBody)
        }
        val encounterId = Json.decodeFromString<JsonObject>(createResponse.bodyAsText())["encounterId"]!!.jsonPrimitive.content

        client.get("/encounters/$encounterId") {
            header("Authorization", "Bearer $token")
        }

        val auditResponse = client.get("/audit/encounters") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, auditResponse.status)
        val logs = Json.decodeFromString<JsonArray>(auditResponse.bodyAsText())
        assertEquals(2, logs.size)

        val actions = logs.map { it.jsonObject["action"]!!.jsonPrimitive.content }.toSet()
        assertTrue(actions.contains("CREATE"))
        assertTrue(actions.contains("READ"))
    }

    @Test
    fun testUnauthorized() = testApplication {
        setup()
        val response = client.get("/encounters/any-id")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testInvalidToken() = testApplication {
        setup()
        val response = client.get("/encounters/any-id") {
            header("Authorization", "Bearer not-a-valid-jwt")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testListEncounters() = testApplication {
        setup()
        val token = getToken()

        client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(validBody)
        }

        val listResponse = client.get("/encounters") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(Json.decodeFromString<JsonArray>(listResponse.bodyAsText()).isNotEmpty())
    }

    @Test
    fun testPhiNotInLogs() {
        val converter = PhiRedactingConverter()

        val jsonMsg = """Processing encounter: "patientId": "P-SECRET-123", providerId: "doc-001""""
        val queryMsg = "Received request with patientId=P-SECRET-123&providerId=doc-001"

        assertFalse(converter.redact(jsonMsg).contains("P-SECRET-123"), "PHI found in JSON log")
        assertFalse(converter.redact(queryMsg).contains("P-SECRET-123"), "PHI found in query log")
    }
}
