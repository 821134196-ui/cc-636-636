package com.example.exitmail.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

/** 定时任务:授权到期检查 + 同步任务执行(含失败重试)。 */
@ApplicationScoped
public class ExpiryScheduler {

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    AutoReplyService autoReplyService;

    @Inject
    SyncService syncService;

    @Scheduled(every = "15s")
    void tick() {
        Instant now = Instant.now();
        forwardRuleService.expireOverdue(now);
        autoReplyService.expireOverdue(now);
        syncService.runDueTasks();
    }
}
