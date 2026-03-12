package com.sallyli.repository

import com.sallyli.model.AuditLog
import java.util.concurrent.ConcurrentHashMap

interface AuditRepository {
    fun save(log: AuditLog): AuditLog
    fun findAll(fromDate: String? = null, toDate: String? = null): List<AuditLog>
}

class InMemoryAuditRepository : AuditRepository {
    private val store = ConcurrentHashMap<String, AuditLog>()

    override fun save(log: AuditLog): AuditLog {
        store[log.auditId] = log
        return log
    }

    override fun findAll(fromDate: String?, toDate: String?): List<AuditLog> {
        return store.values.filter { log ->
            (fromDate == null || log.accessedAt >= fromDate) &&
            (toDate == null || log.accessedAt <= toDate)
        }.sortedByDescending { it.accessedAt }
    }
}
