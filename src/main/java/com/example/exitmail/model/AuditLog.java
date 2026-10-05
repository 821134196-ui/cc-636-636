package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/** 审计日志:配置变更与同步结果全量留痕 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Instant timestamp = Instant.now();

    /** CONFIG / APPROVAL / SYNC / EMPLOYEE */
    @Column(nullable = false)
    public String category;

    /** 如 RULE_REQUEST / RULE_APPROVE / FORWARDED / FORWARD_BLOCKED_SENSITIVE ... */
    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public String actor;

    @Column(length = 4000)
    public String details;

    public AuditLog() {
    }

    public AuditLog(String category, String action, String actor, String details) {
        this.category = category;
        this.action = action;
        this.actor = actor;
        this.details = details;
    }
}
