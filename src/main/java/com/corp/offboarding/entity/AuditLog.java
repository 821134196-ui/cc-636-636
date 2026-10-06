package com.corp.offboarding.entity;

import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * 审计日志：所有配置变更与同步结果均落库，
 * 便于核对每次转发规则的接收人、邮件范围、生效与停用时间。
 */
@Entity
@Table(name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
                @Index(name = "idx_audit_created", columnList = "created_at")
        })
public class AuditLog extends BaseEntity {

    /** 操作人（HR / 主管 / 审批人 / 系统调度器 SYSTEM）。 */
    @Column(nullable = false)
    public String actor;

    /** 动作码，如 FORWARD_REQUESTED / FORWARD_REVOKED / SYNC_SKIPPED_NO_AUTH。 */
    @Column(nullable = false, length = 48)
    public String action;

    @Column(name = "entity_type", nullable = false, length = 24)
    public String entityType;

    @Column(name = "entity_id")
    public Long entityId;

    /** 结构化明细 JSON（接收人、范围、起止时间、停用原因等）。 */
    @Column(nullable = false, length = 4000)
    public String detail;

    @Column(name = "created_at", nullable = false)
    public long createdAt;
}
