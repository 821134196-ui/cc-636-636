package com.corp.offboarding.entity;

import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.StopReason;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 转发规则的历史版本：完整记录每次申请、审批、生效、停用的接收人/范围/时间。 */
@Entity
@Table(name = "forwarding_version",
        indexes = @Index(name = "idx_fwdver_rule", columnList = "rule_id"))
public class ForwardingVersion extends BaseEntity {

    @Column(name = "rule_id", nullable = false)
    public Long ruleId;

    @Column(nullable = false)
    public int version;

    @Column(name = "recipient_id", nullable = false)
    public Long recipientId;

    @Column(name = "recipient_email", nullable = false)
    public String recipientEmail;

    @Column(name = "scope_type", nullable = false, length = 24)
    public String scopeType;

    @Column(name = "scope_criteria", length = 1000)
    public String scopeCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ForwardStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 24)
    public StopReason stopReason;

    /** REQUESTED / APPROVED / ACTIVATED / DENIED / STOPPED */
    @Column(name = "change_type", nullable = false, length = 24)
    public String changeType;

    @Column(name = "changed_by")
    public String changedBy;

    @Column(name = "valid_from", nullable = false)
    public long validFrom;

    @Column(name = "valid_until", nullable = false)
    public long validUntil;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(length = 500)
    public String note;
}
