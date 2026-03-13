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

    private val validApiKey = "test-api-key-provider-001"
    private val validBody = """{"patientId":"P123","providerId":"provider-001","encounterDate":"2026-03-10T10:00:00Z","encounterType":"INITIAL_ASSESSMENT","clinicalData":{"notes":"First visit"}}"""

    private fun ApplicationTestBuilder.setup() {
        environment {
            config = MapApplicationConfig(
                "api.keys" to "test-api-key-provider-001:provider-001,test-api-key-provider-002:provider-002"
            )
        }
        application { module() }
    }

    @Test
    fun testCreateEncounterSuccess() = testApplication {
        setup()
        val response = client.post("/encounters") {
            header("X-API-Key", validApiKey)
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
        val response = client.post("/encounters") {
            header("X-API-Key", validApiKey)
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
        val createResponse = client.post("/encounters") {
            header("X-API-Key", validApiKey)
            header("Content-Type", "application/json")
            setBody(validBody)
        }
        val created = Json.decodeFromString<JsonObject>(createResponse.bodyAsText())
        val encounterId = created["encounterId"]!!.jsonPrimitive.content

        val getResponse = client.get("/encounters/$encounterId") {
            header("X-API-Key", validApiKey)
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val body = Json.decodeFromString<JsonObject>(getResponse.bodyAsText())
        assertEquals(encounterId, body["encounterId"]!!.jsonPrimitive.content)
    }

    @Test
    fun testGetEncounterNotFound() = testApplication {
        setup()
        val response = client.get("/encounters/nonexistent-id-12345") {
            header("X-API-Key", validApiKey)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testAuditTrail() = testApplication {
        setup()
        val createResponse = client.post("/encounters") {
            header("X-API-Key", validApiKey)
            header("Content-Type", "application/json")
            setBody(validBody)
        }
        val created = Json.decodeFromString<JsonObject>(createResponse.bodyAsText())
        val encounterId = created["encounterId"]!!.jsonPrimitive.content

        client.get("/encounters/$encounterId") {
            header("X-API-Key", validApiKey)
        }

        val auditResponse = client.get("/audit/encounters") {
            header("X-API-Key", validApiKey)
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
    fun testInvalidApiKey() = testApplication {
        setup()
        val response = client.get("/encounters/any-id") {
            header("X-API-Key", "not-a-real-key")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testListEncounters() = testApplication {
        setup()
        client.post("/encounters") {
            header("X-API-Key", validApiKey)
            header("Content-Type", "application/json")
            setBody(validBody)
        }

        val listResponse = client.get("/encounters") {
            header("X-API-Key", validApiKey)
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val list = Json.decodeFromString<JsonArray>(listResponse.bodyAsText())
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun testPhiNotInLogs() {
        val converter = PhiRedactingConverter()

        val jsonMsg = """Processing encounter: "patientId": "P-SECRET-123", providerId: "doc-001""""
        val queryMsg = "Received request with patientId=P-SECRET-123&providerId=doc-001"

        val redactedJson = converter.redact(jsonMsg)
        val redactedQuery = converter.redact(queryMsg)

        assertFalse(redactedJson.contains("P-SECRET-123"), "PHI found in JSON log: $redactedJson")
        assertFalse(redactedQuery.contains("P-SECRET-123"), "PHI found in query log: $redactedQuery")
    }
}
