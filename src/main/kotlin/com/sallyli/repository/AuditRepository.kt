package com.sallyli.repository

import com.sallyli.model.AuditLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

interface AuditRepository {
    fun save(log: AuditLog): AuditLog
    fun findAll(fromDate: String? = null, toDate: String? = null, accessedBy: String? = null): List<AuditLog>
}

class InMemoryAuditRepository : AuditRepository {
    private val store = ConcurrentHashMap<String, AuditLog>()

    override fun save(log: AuditLog): AuditLog {
        store[log.auditId] = log
        return log
    }

    override fun findAll(fromDate: String?, toDate: String?, accessedBy: String?): List<AuditLog> {
        val from = fromDate?.toInstantFloor()
        val to = toDate?.toInstantCeiling()
        return store.values.filter { log ->
            val at = Instant.parse(log.accessedAt)
            (from == null || at >= from) &&
            (to == null || at <= to) &&
            (accessedBy == null || log.accessedBy == accessedBy)
        }.sortedByDescending { it.accessedAt }
    }
}

// date-only "2026-03-13" → 2026-03-13T00:00:00Z; full timestamp parsed as-is
private fun String.toInstantFloor(): Instant =
    try { Instant.parse(this) }
    catch (_: Exception) { LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant() }

// date-only "2026-03-13" → 2026-03-13T23:59:59.999999999Z; full timestamp parsed as-is
private fun String.toInstantCeiling(): Instant =
    try { Instant.parse(this) }
    catch (_: Exception) { LocalDate.parse(this).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1) }
