package com.sallyli.model

data class CallerContext(val identity: String, val role: String) {
    val isAdmin: Boolean get() = role == "admin"
}
