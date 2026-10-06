package com.corp.offboarding.model;

/**
 * 转发规则状态机：
 * PENDING_APPROVAL 主管申请，等待审批
 * SCHEDULED        已审批但未到生效时间
 * ACTIVE           生效中（每次执行仍须实时核对授权）
 * STOPPED          已停用（见 {@link StopReason}），不可恢复，只能重新申请
 */
public enum ForwardStatus {
    PENDING_APPROVAL,
    SCHEDULED,
    ACTIVE,
    STOPPED
}
