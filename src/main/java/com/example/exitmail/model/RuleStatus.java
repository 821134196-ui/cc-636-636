package com.example.exitmail.model;

/**
 * 转发规则状态机:
 * PENDING_APPROVAL(待审批) → ACTIVE(生效中) → REVOKED(审批撤销)/ EXPIRED(到期)/ DISABLED(停用)
 * 终态不可回退,任何变更只能产生新版本并重新审批。
 */
public enum RuleStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REVOKED,
    EXPIRED,
    DISABLED
}
