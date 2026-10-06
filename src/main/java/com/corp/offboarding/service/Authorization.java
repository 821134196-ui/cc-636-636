package com.corp.offboarding.service;

/** 授权实时核对结果。同步任务执行/重试时只认真实的当前状态，不采信任务创建时的快照。 */
public record Authorization(boolean authorized, String reason) {

    public static Authorization ok() {
        return new Authorization(true, "OK");
    }

    public static Authorization deny(String reason) {
        return new Authorization(false, reason);
    }
}
