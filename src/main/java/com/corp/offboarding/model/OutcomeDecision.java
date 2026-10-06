package com.corp.offboarding.model;

/** 单封邮件针对单条转发规则的处置结论。 */
public enum OutcomeDecision {
    FORWARDED,
    BLOCKED_PRIVATE,
    BLOCKED_SENSITIVE,
    BLOCKED_OUT_OF_SCOPE,
    SKIPPED_INACTIVE
}
