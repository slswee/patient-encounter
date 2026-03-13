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
