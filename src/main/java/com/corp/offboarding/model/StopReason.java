package com.corp.offboarding.model;

/** 转发停用原因，全部进入版本快照与审计记录。 */
public enum StopReason {
    APPROVAL_DENIED,
    REVOKED,
    EXPIRED,
    RECIPIENT_REASSIGNED
}
