package com.corp.offboarding.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试/演示用故障注入：让接下来的 n 次转发投递失败，
 * 以验证"旧任务失败后重试，仍须核对当前授权"。
 */
@ApplicationScoped
public class FailureInjector {

    private final AtomicInteger failuresRemaining = new AtomicInteger(0);

    public void failNext(int n) {
        failuresRemaining.set(n);
    }

    public boolean shouldFail() {
        while (true) {
            int v = failuresRemaining.get();
            if (v <= 0) {
                return false;
            }
            if (failuresRemaining.compareAndSet(v, v - 1)) {
                return true;
            }
        }
    }

    public int remaining() {
        return failuresRemaining.get();
    }
}
