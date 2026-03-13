package com.sallyli.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtConfig(secret: String) {
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    val issuer = "patient-encounter-api"
    val audience = "patient-encounter-clients"
    val tokenTtlSeconds = 900L  // 15 minutes

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(identity: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(identity)
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + tokenTtlSeconds * 1000))
        .sign(algorithm)
}
