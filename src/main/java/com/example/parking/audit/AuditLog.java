package com.example.parking.audit;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name="idx_audit_ts", columnList="ts"),
                @Index(name="idx_audit_user", columnList="username"),
                @Index(name="idx_audit_action", columnList="action"),
                @Index(name="idx_audit_path", columnList="path")
        }
)
@Immutable     // Hibernate: disallow updates (insert-only)
@DynamicInsert // Use DB defaults if fields are null
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 255)
    private String username;

    @Column(length = 255)
    private String roles;

    @Column(length = 45)
    private String ip;

    @Column(length = 16)
    private String method;

    @Column(length = 512)
    private String path;

    private Integer status;

    @Column(name="user_agent", length = 512)
    private String userAgent;

    @Column(length = 1024)
    private String message;

    protected AuditLog() {} // JPA

    private AuditLog(Builder b) {
        this.ts = b.ts != null ? b.ts : Instant.now();
        this.action = b.action;
        this.username = b.username;
        this.roles = b.roles;
        this.ip = b.ip;
        this.method = b.method;
        this.path = b.path;
        this.status = b.status;
        this.userAgent = b.userAgent;
        this.message = b.message;
    }

    // Getters only (immutability)
    public Long getId() { return id; }
    public Instant getTs() { return ts; }
    public String getAction() { return action; }
    public String getUsername() { return username; }
    public String getRoles() { return roles; }
    public String getIp() { return ip; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public Integer getStatus() { return status; }
    public String getUserAgent() { return userAgent; }
    public String getMessage() { return message; }

    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private Instant ts;
        private String action, username, roles, ip, method, path, userAgent, message;
        private Integer status;
        public Builder ts(Instant v){ this.ts=v; return this; }
        public Builder action(String v){ this.action=v; return this; }
        public Builder username(String v){ this.username=v; return this; }
        public Builder roles(String v){ this.roles=v; return this; }
        public Builder ip(String v){ this.ip=v; return this; }
        public Builder method(String v){ this.method=v; return this; }
        public Builder path(String v){ this.path=v; return this; }
        public Builder status(Integer v){ this.status=v; return this; }
        public Builder userAgent(String v){ this.userAgent=v; return this; }
        public Builder message(String v){ this.message=v; return this; }
        public AuditLog build(){ return new AuditLog(this); }
    }
}
