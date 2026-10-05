package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 邮件转发授权规则(按员工版本化)。
 * 主管申请 → 审批通过才生效;撤销、到期、接收人调岗都会使其进入终态,终态不可恢复。
 * 任何修改都产生新版本并回到待审批,旧版本同时停用。
 */
@Entity
@Table(name = "forward_rule")
public class ForwardRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 离职员工(邮箱属主) */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    @Column(nullable = false)
    public int version;

    /** 接替人邮箱(转发接收人) */
    @Column(nullable = false)
    public String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RuleScope scope = RuleScope.ALL_BUSINESS;

    /** scope = SUBJECT_KEYWORDS 时的关键词,逗号分隔 */
    public String scopeKeywords;

    @Column(nullable = false)
    public Instant startTime;

    /** 授权截止日期(必填,到期自动失效) */
    @Column(nullable = false)
    public Instant endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RuleStatus status = RuleStatus.PENDING_APPROVAL;

    /** 申请人(主管) */
    @Column(nullable = false)
    public String requestedBy;

    @Column(nullable = false)
    public Instant requestedAt = Instant.now();

    public String approvedBy;

    public Instant approvedAt;

    /** 进入终态的时间 */
    public Instant closedAt;

    /** REVOKED / EXPIRED / RECIPIENT_TRANSFERRED / SUPERSEDED / MANUAL */
    public String closeReason;

    /** 被哪个新版本取代(若有) */
    public Long supersededById;

    public String scopeDescription() {
        if (scope == RuleScope.SUBJECT_KEYWORDS) {
            return "主题含关键词: " + (scopeKeywords == null ? "" : scopeKeywords);
        }
        return "全部业务邮件";
    }
}
