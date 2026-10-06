package com.corp.offboarding.model;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** 执行/重试时发现授权已失效，规则不会被重新打开 */
    SKIPPED_NO_AUTH
}
