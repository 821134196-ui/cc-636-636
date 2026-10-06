package com.corp.offboarding.entity;

import com.corp.offboarding.model.ReplyStatus;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 自动回复规则的历史版本（含创建、正文修改、到点生效、停用）。 */
@Entity
@Table(name = "auto_reply_version",
        indexes = @Index(name = "idx_replyver_rule", columnList = "rule_id"))
public class AutoReplyVersion extends BaseEntity {

    @Column(name = "rule_id", nullable = false)
    public Long ruleId;

    @Column(nullable = false)
    public int version;

    @Column(nullable = false, length = 2000)
    public String body;

    @Column(name = "effective_at", nullable = false)
    public long effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ReplyStatus status;

    /** CREATED / UPDATED / ACTIVATED / DISABLED */
    @Column(name = "change_type", nullable = false, length = 24)
    public String changeType;

    @Column(name = "changed_by")
    public String changedBy;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(length = 500)
    public String note;
}
