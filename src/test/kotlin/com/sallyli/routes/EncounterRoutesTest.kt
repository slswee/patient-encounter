package com.sallyli.routes

import com.sallyli.BaseRouteTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterRoutesTest : BaseRouteTest() {

    // ── Auth guards ───────────────────────────────────────────────────────────

    @Test
    fun testNoTokenReturns401() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/encounters/any").status)
    }

    @Test
    fun testInvalidTokenReturns401() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/encounters/any") {
            header("Authorization", "Bearer not-a-jwt")
        }.status)
    }

    // ── POST /encounters ──────────────────────────────────────────────────────

    @Test
    fun testCreateEncounterReturns201WithEncounterId() = testApplication {
        setup()
        val token = getToken()
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(provider1Body)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["encounterId"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun testCreateEncounterValidationFailureReturns400() = testApplication {
        setup()
        val token = getToken()
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody("""{"patientId":"","providerId":"","encounterDate":"bad-date","encounterType":"INITIAL_ASSESSMENT","clinicalData":{}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Validation failed", Json.decodeFromString<JsonObject>(response.bodyAsText())["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun testProviderCannotCreateForAnotherProviderReturns403() = testApplication {
        setup()
        val token = getToken()   // provider-001 token
        assertEquals(HttpStatusCode.Forbidden, client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(provider2Body)  // attributed to provider-002
        }.status)
    }

    // ── GET /encounters/{id} ──────────────────────────────────────────────────

    @Test
    fun testGetOwnEncounterReturns200() = testApplication {
        setup()
        val token = getToken()
        val id = createEncounter(token, provider1Body)
        val response = client.get("/encounters/$id") { header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(id, Json.decodeFromString<JsonObject>(response.bodyAsText())["encounterId"]!!.jsonPrimitive.content)
    }

    @Test
    fun testGetUnknownEncounterReturns404() = testApplication {
        setup()
        val token = getToken()
        assertEquals(HttpStatusCode.NotFound, client.get("/encounters/nonexistent") {
            header("Authorization", "Bearer $token")
        }.status)
    }

    @Test
    fun testGetAnotherProvidersEncounterReturns403() = testApplication {
        setup()
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        val id = createEncounter(token2, provider2Body)

        val token1 = getToken()
        assertEquals(HttpStatusCode.Forbidden, client.get("/encounters/$id") {
            header("Authorization", "Bearer $token1")
        }.status)
    }

    @Test
    fun testAdminCanGetAnyEncounter() = testApplication {
        setup()
        val token = getToken()
        val id = createEncounter(token, provider1Body)

        assertEquals(HttpStatusCode.OK, client.get("/encounters/$id") {
            header("Authorization", "Bearer ${getAdminToken()}")
        }.status)
    }

    // ── GET /encounters ───────────────────────────────────────────────────────

    @Test
    fun testListReturnsOnlyOwnEncounters() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)
        createEncounter(token2, provider2Body)

        val list = Json.decodeFromString<JsonArray>(
            client.get("/encounters") { header("Authorization", "Bearer $token1") }.bodyAsText()
        )
        assertEquals(1, list.size)
        assertEquals("provider-001", list[0].jsonObject["providerId"]!!.jsonPrimitive.content)
    }

    @Test
    fun testAdminListReturnsAllEncounters() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)
        createEncounter(token2, provider2Body)

        val list = Json.decodeFromString<JsonArray>(
            client.get("/encounters") { header("Authorization", "Bearer ${getAdminToken()}") }.bodyAsText()
        )
        assertEquals(2, list.size)
    }

    // ── GET /audit/encounters ─────────────────────────────────────────────────

    @Test
    fun testAuditTrailRecordsCreateAndRead() = testApplication {
        setup()
        val token = getToken()
        val id = createEncounter(token, provider1Body)
        client.get("/encounters/$id") { header("Authorization", "Bearer $token") }

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        assertEquals(2, logs.size)
        val actions = logs.map { it.jsonObject["action"]!!.jsonPrimitive.content }.toSet()
        assertTrue(actions.contains("CREATE"))
        assertTrue(actions.contains("READ"))
    }

    @Test
    fun testListEncountersWritesAuditEntry() = testApplication {
        setup()
        val token = getToken()
        val encounterId = createEncounter(token, provider1Body)

        client.get("/encounters") { header("Authorization", "Bearer $token") }

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        val listEntries = logs.filter { it.jsonObject["action"]!!.jsonPrimitive.content == "LIST" }
        assertEquals(1, listEntries.size)
        assertEquals(encounterId, listEntries[0].jsonObject["encounterId"]!!.jsonPrimitive.content)
        assertEquals("provider-001", listEntries[0].jsonObject["accessedBy"]!!.jsonPrimitive.content)
    }

    @Test
    fun testListEncountersWritesOneEntryPerResult() = testApplication {
        setup()
        val token = getToken()
        createEncounter(token, provider1Body)
        createEncounter(token, provider1Body)

        client.get("/encounters") { header("Authorization", "Bearer $token") }

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        val listEntries = logs.filter { it.jsonObject["action"]!!.jsonPrimitive.content == "LIST" }
        assertEquals(2, listEntries.size)
    }

    @Test
    fun testListEncountersWithNoResultsWritesNoAuditEntry() = testApplication {
        setup()
        val token = getToken()  // provider-001, no encounters created

        client.get("/encounters") { header("Authorization", "Bearer $token") }

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        val listEntries = logs.filter { it.jsonObject["action"]!!.jsonPrimitive.content == "LIST" }
        assertEquals(0, listEntries.size)
    }

    // ── Cache-Control headers ─────────────────────────────────────────────────

    @Test
    fun testCreateEncounterResponseHasNoCacheHeader() = testApplication {
        setup()
        val token = getToken()
        val response = client.post("/encounters") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(provider1Body)
        }
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun testGetEncounterResponseHasNoCacheHeader() = testApplication {
        setup()
        val token = getToken()
        val encounterId = createEncounter(token, provider1Body)
        val response = client.get("/encounters/$encounterId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun testListEncountersResponseHasNoCacheHeader() = testApplication {
        setup()
        val token = getToken()
        val response = client.get("/encounters") {
            header("Authorization", "Bearer $token")
        }
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
}
