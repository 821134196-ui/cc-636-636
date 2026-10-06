package com.corp.offboarding.entity;

import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.StopReason;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * 转发授权规则（当前版本）。接替人员不获得邮箱登录权，
 * 只能在审批通过的"接收人 + 邮件范围 + 截止日期"内收到转发副本。
 */
@Entity
@Table(name = "forwarding_rule",
        indexes = @Index(name = "idx_fwd_recipient", columnList = "recipient_id"))
public class ForwardingRule extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    public Long employeeId;

    /** 接替人员（接收人），必须是在职员工。 */
    @Column(name = "recipient_id", nullable = false)
    public Long recipientId;

    /** 接收人邮箱快照，即使员工资料变更也能在审计中复原当时配置。 */
    @Column(name = "recipient_email_snapshot", nullable = false)
    public String recipientEmailSnapshot;

    /** 邮件范围类型，见 {@link com.corp.offboarding.model.MailScope}。 */
    @Column(name = "scope_type", nullable = false, length = 24)
    public String scopeType;

    /** 范围条件（发件人地址/主题关键词，分号分隔）；ALL_BUSINESS 时为空。 */
    @Column(name = "scope_criteria", length = 1000)
    public String scopeCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ForwardStatus status = ForwardStatus.PENDING_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 24)
    public StopReason stopReason;

    @Column(name = "requested_by")
    public String requestedBy;

    @Column(name = "approved_by")
    public String approvedBy;

    @Column(name = "valid_from", nullable = false)
    public long validFrom;

    @Column(name = "valid_until", nullable = false)
    public long validUntil;

    @Column(name = "current_version", nullable = false)
    public int currentVersion = 1;

    @Column(name = "activated_at")
    public Long activatedAt;

    @Column(name = "stopped_at")
    public Long stoppedAt;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(name = "updated_at", nullable = false)
    public long updatedAt;
}
