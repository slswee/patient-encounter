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

class AuthRoutesTest : BaseRouteTest() {

    // ── POST /oauth/token ─────────────────────────────────────────────────────

    @Test
    fun testValidCredentialsReturnToken() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=provider-001&client_secret=test-api-key-provider-001&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["access_token"]!!.jsonPrimitive.content.isNotEmpty())
        assertEquals("Bearer", body["token_type"]!!.jsonPrimitive.content)
        assertEquals(900, body["expires_in"]!!.jsonPrimitive.long)
    }

    @Test
    fun testWrongSecretReturns401() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=provider-001&client_secret=wrong-secret&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testMismatchedClientIdAndSecretReturns401() = testApplication {
        setup()
        // Secret belongs to provider-001, not provider-002
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=provider-002&client_secret=test-api-key-provider-001&grant_type=client_credentials")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testUnsupportedGrantTypeReturns400() = testApplication {
        setup()
        val response = client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=provider-001&client_secret=test-api-key-provider-001&grant_type=password")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("unsupported_grant_type", Json.decodeFromString<JsonObject>(response.bodyAsText())["error"]!!.jsonPrimitive.content)
    }

    // ── POST /oauth/revoke ────────────────────────────────────────────────────

    @Test
    fun testRevokedTokenIsRejected() = testApplication {
        setup()
        val token = getToken()

        // Token passes auth before revocation (404 = found auth layer, encounter missing)
        assertEquals(HttpStatusCode.NotFound, client.get("/encounters/x") {
            header("Authorization", "Bearer $token")
        }.status)

        assertEquals(HttpStatusCode.OK, client.post("/oauth/revoke") {
            header("Authorization", "Bearer $token")
        }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/encounters/x") {
            header("Authorization", "Bearer $token")
        }.status)
    }

    @Test
    fun testRevokeWithoutTokenReturns401() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/oauth/revoke").status)
    }

    @Test
    fun testRevokeWithInvalidJwtReturns401() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/oauth/revoke") {
            header("Authorization", "Bearer not-a-valid-jwt")
        }.status)
    }

    // ── Auth failure audit logging ────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.authFailureLogs(): JsonArray {
        val adminToken = getAdminToken()
        val logs = Json.decodeFromString<JsonArray>(
            client.get("/audit/encounters") { header("Authorization", "Bearer $adminToken") }.bodyAsText()
        )
        return JsonArray(logs.filter { it.jsonObject["action"]?.jsonPrimitive?.content == "AUTH_FAILURE" })
    }

    @Test
    fun testNoCredentialsWritesAuditLog() = testApplication {
        setup()
        client.get("/encounters/any")   // no Authorization header

        val failures = authFailureLogs()
        assertEquals(1, failures.size)
        assertEquals("NO_CREDENTIALS", failures[0].jsonObject["reason"]!!.jsonPrimitive.content)
        assertEquals("unknown", failures[0].jsonObject["accessedBy"]!!.jsonPrimitive.content)
    }

    @Test
    fun testInvalidTokenWritesAuditLog() = testApplication {
        setup()
        client.get("/encounters/any") { header("Authorization", "Bearer not-a-real-jwt") }

        val failures = authFailureLogs()
        assertEquals(1, failures.size)
        assertEquals("INVALID_TOKEN", failures[0].jsonObject["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun testRevokedTokenWritesAuditLog() = testApplication {
        setup()
        val token = getToken()
        client.post("/oauth/revoke") { header("Authorization", "Bearer $token") }
        client.get("/encounters/any") { header("Authorization", "Bearer $token") }

        val failures = authFailureLogs()
        assertEquals(1, failures.size)
        assertEquals("INVALID_TOKEN", failures[0].jsonObject["reason"]!!.jsonPrimitive.content)
        assertEquals("provider-001", failures[0].jsonObject["accessedBy"]!!.jsonPrimitive.content)
    }

    @Test
    fun testInvalidClientWritesAuditLog() = testApplication {
        setup()
        client.post("/oauth/token") {
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody("client_id=provider-001&client_secret=wrong-secret&grant_type=client_credentials")
        }

        val failures = authFailureLogs()
        assertEquals(1, failures.size)
        assertEquals("INVALID_CLIENT", failures[0].jsonObject["reason"]!!.jsonPrimitive.content)
        assertEquals("provider-001", failures[0].jsonObject["accessedBy"]!!.jsonPrimitive.content)
    }

    @Test
    fun testSuccessfulAuthDoesNotWriteFailureLog() = testApplication {
        setup()
        getToken()  // valid token exchange
        assertEquals(0, authFailureLogs().size)
    }

    @Test
    fun testRevokingOneTokenDoesNotAffectOthers() = testApplication {
        setup()
        val token1 = getToken(clientId = "provider-001", clientSecret = "test-api-key-provider-001")
        val token2 = getToken(clientId = "provider-002", clientSecret = "test-api-key-provider-002")

        client.post("/oauth/revoke") { header("Authorization", "Bearer $token1") }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/encounters/x") {
            header("Authorization", "Bearer $token1")
        }.status)
        // token2 still passes auth — 404 means it reached the route
        assertEquals(HttpStatusCode.NotFound, client.get("/encounters/x") {
            header("Authorization", "Bearer $token2")
        }.status)
    }
}
