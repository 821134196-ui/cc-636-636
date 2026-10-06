package com.corp.offboarding.model;

/** 自动回复规则状态：人事确认后按离职时间排程，到点生效，可手动停用。 */
public enum ReplyStatus {
    SCHEDULED,
    ACTIVE,
    DISABLED
}
