package io.github.toansh.mcp.audit;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_caller_created_at", columnList = "caller, created_at DESC"),
        @Index(name = "idx_audit_created_at", columnList = "created_at DESC")
})
public class AuditLog extends PanacheEntity {

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(nullable = false, length = 128)
    public String caller;

    @Column(nullable = false, length = 64)
    public String tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> args;

    @Column(name = "result_size")
    public Long resultSize;

    @Column(name = "latency_ms", nullable = false)
    public long latencyMs;

    @Column(nullable = false, length = 32)
    public String status;

    public static List<AuditLog> recentByCaller(String caller, int limit) {
        return find("caller = ?1 order by createdAt desc", caller)
                .page(0, limit)
                .list();
    }

    public static List<AuditLog> recentByTool(String tool, int limit) {
        return find("tool = ?1 order by createdAt desc", tool)
                .page(0, limit)
                .list();
    }
}
