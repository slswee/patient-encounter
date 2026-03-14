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

class AuditRoutesTest : BaseRouteTest() {

    // ── Auth guard ────────────────────────────────────────────────────────────

    @Test
    fun testNoTokenReturns401() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/audit/encounters").status)
    }

    // ── RBAC scoping ──────────────────────────────────────────────────────────

    @Test
    fun testProviderSeesOnlyOwnAuditEntries() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)
        createEncounter(token2, provider2Body)

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token1") }.bodyAsText()
        )
        assertEquals(1, logs.size)
        assertEquals("provider-001", logs[0].jsonObject["accessedBy"]!!.jsonPrimitive.content)
    }

    @Test
    fun testProviderCannotSeeOtherProvidersAuditEntries() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)
        createEncounter(token2, provider2Body)

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token2") }.bodyAsText()
        )
        assertTrue(logs.all { it.jsonObject["accessedBy"]!!.jsonPrimitive.content == "provider-002" })
    }

    @Test
    fun testAdminSeesAllAuditEntries() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)
        createEncounter(token2, provider2Body)

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer ${getAdminToken()}") }.bodyAsText()
        )
        assertEquals(2, logs.size)
        val providers = logs.map { it.jsonObject["accessedBy"]!!.jsonPrimitive.content }.toSet()
        assertTrue(providers.contains("provider-001"))
        assertTrue(providers.contains("provider-002"))
    }

    @Test
    fun testProviderWithNoActivitySeesEmptyList() = testApplication {
        setup()
        // provider-001 creates an encounter; provider-002 has done nothing
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")
        createEncounter(token1, provider1Body)

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token2") }.bodyAsText()
        )
        assertEquals(0, logs.size)
    }

    // ── Audit entry content ───────────────────────────────────────────────────

    @Test
    fun testAuditEntryFieldsForCreate() = testApplication {
        setup()
        val token = getToken()
        val encounterId = createEncounter(token, provider1Body)

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        assertEquals(1, logs.size)
        val entry = logs[0].jsonObject
        assertEquals("CREATE", entry["action"]!!.jsonPrimitive.content)
        assertEquals(encounterId, entry["encounterId"]!!.jsonPrimitive.content)
        assertEquals("provider-001", entry["accessedBy"]!!.jsonPrimitive.content)
        assertTrue(entry["accessedAt"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun testAuditEntryFieldsForRead() = testApplication {
        setup()
        val token = getToken()
        val encounterId = createEncounter(token, provider1Body)
        client.get("/encounters/$encounterId") { header("Authorization", "Bearer $token") }

        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $token") }.bodyAsText()
        )
        val readEntry = logs.first { it.jsonObject["action"]!!.jsonPrimitive.content == "READ" }.jsonObject
        assertEquals(encounterId, readEntry["encounterId"]!!.jsonPrimitive.content)
        assertEquals("provider-001", readEntry["accessedBy"]!!.jsonPrimitive.content)
    }

    // ── Cache-Control header ──────────────────────────────────────────────────

    @Test
    fun testAuditResponseHasNoCacheHeader() = testApplication {
        setup()
        val token = getAdminToken()
        val response = client.get("/audit/encounters") {
            header("Authorization", "Bearer $token")
        }
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
}
