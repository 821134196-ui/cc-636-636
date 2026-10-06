package com.corp.offboarding.scheduler;

import com.corp.offboarding.service.AutoReplyService;
import com.corp.offboarding.service.ForwardingService;
import com.corp.offboarding.service.SyncService;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 周期维护（测试环境关闭，由测试手动驱动以保证确定性）：
 * 自动回复到点生效、转发到点生效/到期停用、失败同步任务重试。
 * 只有在建表 + 演示数据播种完成（StartupEvent）之后才放行，
 * 避免与启动期事务争抢 SQLite 的唯一写连接。
 */
@ApplicationScoped
public class MaintenanceScheduler {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Inject
    SyncService syncService;

    @Inject
    ForwardingService forwardingService;

    @Inject
    AutoReplyService autoReplyService;

    void onStarted(@Observes StartupEvent ev) {
        // DataSeeder 完成后 StartupEvent 的所有观察者才返回；这里仅翻转门控。
        // CDI 观察者按优先级执行：先建表等待 + 播种（DataSeeder），后置门控。
        ready.set(true);
    }

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!ready.get()) {
            return;
        }
        try {
            syncService.drain(forwardingService, autoReplyService);
        } catch (Exception e) {
            // 单次维护失败不应杀死调度循环（下周期重试）
            System.err.println("[scheduler] 周期维护失败，将在下周期重试: " + e.getMessage());
        }
    }
}
