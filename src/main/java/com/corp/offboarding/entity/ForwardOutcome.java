package com.corp.offboarding.entity;

import com.corp.offboarding.model.OutcomeDecision;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 一封邮件 × 一条转发规则的处置结果（带幂等回执）。 */
@Entity
@Table(name = "forward_outcome",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outcome_mail_rule", columnNames = {"mail_id", "rule_id"}),
        indexes = @Index(name = "idx_outcome_receipt", columnList = "receipt_id"))
public class ForwardOutcome extends BaseEntity {

    @Column(name = "mail_id", nullable = false)
    public Long mailId;

    @Column(name = "rule_id", nullable = false)
    public Long ruleId;

    @Column(name = "recipient_id", nullable = false)
    public Long recipientId;

    @Column(name = "recipient_email", nullable = false)
    public String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public OutcomeDecision decision;

    /** 外部邮箱服务回执号，重复回执按 (receiptId) 幂等丢弃，不改变既有结论。 */
    @Column(name = "receipt_id", nullable = false, unique = true, length = 64)
    public String receiptId;

    @Column(name = "attempted_at", nullable = false)
    public long attemptedAt;

    /** 执行时规则状态快照（ACTIVE / EXPIRED / STOPPED...），用于审计核对。 */
    @Column(name = "rule_status_snapshot", length = 24)
    public String ruleStatusSnapshot;

    public static ForwardOutcome findByReceipt(String receiptId) {
        return find("receiptId", receiptId).firstResult();
    }
}
