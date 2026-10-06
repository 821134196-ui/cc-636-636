package com.corp.offboarding.service;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.OutcomeDecision;
import com.corp.offboarding.model.TaskStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

/**
 * 转发同步执行器（安全语义集中在这里）：
 * <ul>
 *   <li>每次执行/重试都重新加载规则并实时核对授权——任务里的版本快照只用于审计，
 *       绝不能据此把已停用/已到期/接收人已调岗的规则"重新打开"；</li>
 *   <li>授权失效 → 任务终态 SKIPPED_NO_AUTH；私人/敏感/超范围 → 记录拦截结论，绝不投递；</li>
 *   <li>投递失败按退避重试；单封邮件×单条规则只有一个 outcome，天然去重；</li>
 *   <li>外部回执按回执号幂等：重复回执只记审计，不改变任何结论。</li>
 * </ul>
 */
@ApplicationScoped
public class SyncService {

    private static final int BATCH = 50;
    private static final long BACKOFF_BASE_MS = 5 * 60_000L;

    @Inject
    TimeService timeService;

    @Inject
    PolicyService policy;

    @Inject
    MailboxService mailbox;

    @Inject
    FailureInjector failureInjector;

    @Inject
    AuditService audit;

    /** 自注入代理，保证 REQUIRES_NEW 事务切面生效。 */
    @Inject
    Provider<SyncService> self;

    /** 调度入口：到期/生效维护 + 执行到期任务，返回处理条数。 */
    public int drain(ForwardingService forwarding, AutoReplyService replies) {
        replies.activateDue();
        forwarding.maintainSchedule();
        long now = timeService.now();
        List<Long> ids = findDueTaskIds(now);
        int processed = 0;
        for (Long id : ids) {
            self.get().processOne(id);
            processed++;
        }
        return processed;
    }

    @Transactional
    public List<Long> findDueTaskIds(long now) {
        return SyncTask.find(
                "((status = ?1) or (status = ?2 and attempts < maxAttempts and nextAttemptAt <= ?3))" +
                        " order by nextAttemptAt asc",
                TaskStatus.PENDING, TaskStatus.FAILED, now)
                .page(0, BATCH).list().stream().map(t -> ((SyncTask) t).id).toList();
    }

    /** 手动/测试入口：立即重试指定任务（包括旧任务）。 */
    public ProcessReport retryTask(Long taskId) {
        SyncTask task = SyncTask.findById(taskId);
        if (task == null) {
            throw new NotFoundException("同步任务不存在: " + taskId);
        }
        return self.get().processOne(taskId);
    }

    /** 单任务独立事务执行，失败不影响其他任务。 */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProcessReport processOne(Long taskId) {
        long now = timeService.now();
        SyncTask task = SyncTask.findById(taskId);
        if (task == null) {
            return ProcessReport.MISSING;
        }
        if (task.status == TaskStatus.SUCCEEDED || task.status == TaskStatus.SKIPPED_NO_AUTH) {
            return ProcessReport.ALREADY_DONE;
        }
        task.status = TaskStatus.RUNNING;
        task.attempts += 1;
        task.updatedAt = now;

        ForwardingRule rule = ForwardingRule.findById(task.ruleId);
        MailMessage mail = MailMessage.findById(task.mailId);
        if (rule == null || mail == null) {
            return finishSkipped(task, rule, "规则或邮件已删除", now);
        }

        // 1) 实时授权核对——不看 task.ruleVersionSnapshot
        Authorization auth = policy.checkAuthorization(rule);
        if (!auth.authorized()) {
            // 关键：旧任务重试发现授权失效，只终结任务，绝不重新启用规则
            return finishSkipped(task, rule, auth.reason(), now);
        }

        // 2) 已存在结论（含历史成功投递）→ 幂等收敛，不重复投递
        ForwardOutcome existing = ForwardOutcome.find("mailId = ?1 and ruleId = ?2",
                mail.id, rule.id).firstResult();
        if (existing != null) {
            task.status = TaskStatus.SUCCEEDED;
            task.receiptId = existing.receiptId;
            task.acknowledged = true;
            task.updatedAt = now;
            audit.record("SYSTEM", "SYNC_DEDUPED", "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "existingDecision", existing.decision.name(),
                            "receiptId", existing.receiptId));
            return ProcessReport.DEDUPED;
        }

        // 3) 内容硬拦截：私人/敏感即使规则有效也不放行
        String blocked = policy.blockReason(mail);
        if (blocked != null) {
            OutcomeDecision decision =
                    "BLOCKED_PRIVATE".equals(blocked)
                            ? OutcomeDecision.BLOCKED_PRIVATE
                            : OutcomeDecision.BLOCKED_SENSITIVE;
            recordOutcome(mail, rule, decision, task, now, rule.status.name());
            task.status = TaskStatus.SUCCEEDED;
            task.updatedAt = now;
            audit.record("SYSTEM",
                    decision == OutcomeDecision.BLOCKED_PRIVATE
                            ? "SYNC_BLOCKED_PRIVATE" : "SYNC_BLOCKED_SENSITIVE",
                    "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "recipient", rule.recipientEmailSnapshot,
                            "subject", mail.subject));
            return ProcessReport.BLOCKED;
        }

        // 4) 范围核对
        if (!policy.inScope(mail, rule)) {
            recordOutcome(mail, rule, OutcomeDecision.BLOCKED_OUT_OF_SCOPE, task, now,
                    rule.status.name());
            task.status = TaskStatus.SUCCEEDED;
            task.updatedAt = now;
            audit.record("SYSTEM", "SYNC_BLOCKED_OUT_OF_SCOPE", "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "scope", rule.scopeType, "scopeCriteria", rule.scopeCriteria));
            return ProcessReport.OUT_OF_SCOPE;
        }

        // 5) 投递（可能失败 → 退避重试，重试时回到第 1 步重新核对授权）
        // 故障判定放在 try 内但在任何事务性调用之前：抛异常不会污染当前事务
        try {
            if (failureInjector.shouldFail()) {
                throw new MailboxService.DeliveryException("模拟邮件服务暂不可用，转发投递失败");
            }
            String content = "（转发自离职员工邮箱，仅限获批业务范围）\n\n" + mail.body;
            String receiptId = mailbox.deliverForward(
                    rule.recipientEmailSnapshot, mail.id, rule.id, mail.subject, content);
            recordOutcome(mail, rule, OutcomeDecision.FORWARDED, task, now,
                    rule.status.name(), receiptId);
            task.status = TaskStatus.SUCCEEDED;
            task.receiptId = receiptId;
            task.updatedAt = now;
            audit.record("SYSTEM", "SYNC_FORWARDED", "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "recipient", rule.recipientEmailSnapshot,
                            "scope", rule.scopeType,
                            "validUntil", rule.validUntil,
                            "ruleVersion", rule.currentVersion,
                            "taskSnapshotVersion", task.ruleVersionSnapshot,
                            "receiptId", receiptId));
            return ProcessReport.FORWARDED;
        } catch (Exception e) {
            task.status = TaskStatus.FAILED;
            task.lastError = truncate(e.getMessage());
            task.nextAttemptAt = now + (long) task.attempts * BACKOFF_BASE_MS;
            task.updatedAt = now;
            boolean exhausted = task.attempts >= task.maxAttempts;
            audit.record("SYSTEM", exhausted ? "SYNC_FAILED_EXHAUSTED" : "SYNC_FAILED_RETRY",
                    "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "attempts", task.attempts, "maxAttempts", task.maxAttempts,
                            "nextAttemptAt", task.nextAttemptAt,
                            "error", task.lastError,
                            "note", "重试前将重新核对当前授权，已停用规则不会恢复"));
            return ProcessReport.FAILED_WILL_RETRY;
        }
    }

    /**
     * 外部邮件服务回执：按回执号幂等。
     * 重复回执返回 false，只写审计，绝不触发重新投递或规则变更。
     */
    @Transactional
    public ReceiptAck acknowledgeReceipt(String receiptId, String remoteService) {
        ForwardOutcome outcome = ForwardOutcome.findByReceipt(receiptId);
        if (outcome == null) {
            throw new NotFoundException("未知回执号: " + receiptId);
        }
        SyncTask task = SyncTask.find("receiptId", receiptId).firstResult();
        if (task != null && task.acknowledged) {
            audit.record(remoteService, "SYNC_RECEIPT_DUPLICATE", "SYNC_TASK", task.id,
                    audit.detail("receiptId", receiptId, "decision", outcome.decision.name(),
                            "note", "重复回执被幂等丢弃，授权状态不变"));
            return new ReceiptAck(false, "DUPLICATE_IGNORED");
        }
        if (task != null) {
            task.acknowledged = true;
            task.updatedAt = timeService.now();
        }
        audit.record(remoteService, "SYNC_RECEIPT_ACKED", "SYNC_TASK",
                task == null ? null : task.id,
                audit.detail("receiptId", receiptId, "decision", outcome.decision.name()));
        return new ReceiptAck(true, "ACKNOWLEDGED");
    }

    private ProcessReport finishSkipped(SyncTask task, ForwardingRule rule,
                                        String reason, long now) {
        task.status = TaskStatus.SKIPPED_NO_AUTH;
        task.lastError = truncate(reason);
        task.updatedAt = now;
        MailMessage mail = MailMessage.findById(task.mailId);
        if (mail != null && rule != null
                && ForwardOutcome.count("mailId = ?1 and ruleId = ?2", mail.id, rule.id) == 0) {
            recordOutcome(mail, rule, OutcomeDecision.SKIPPED_INACTIVE, task, now,
                    rule.status.name());
        }
        audit.record("SYSTEM", "SYNC_SKIPPED_NO_AUTH", "SYNC_TASK", task.id,
                audit.detail("mailId", task.mailId, "ruleId", task.ruleId,
                        "ruleStatusNow", rule == null ? "DELETED" : rule.status.name(),
                        "stopReason",
                        rule == null || rule.stopReason == null ? null : rule.stopReason.name(),
                        "taskSnapshotVersion", task.ruleVersionSnapshot,
                        "reason", reason,
                        "note", "旧任务重试时授权已失效，规则保持停用，未重新打开"));
        return ProcessReport.SKIPPED_NO_AUTH;
    }

    private void recordOutcome(MailMessage mail, ForwardingRule rule, OutcomeDecision decision,
                               SyncTask task, long now, String statusSnapshot) {
        recordOutcome(mail, rule, decision, task, now, statusSnapshot,
                "OUT-" + mail.id + "-" + rule.id);
    }

    private void recordOutcome(MailMessage mail, ForwardingRule rule, OutcomeDecision decision,
                               SyncTask task, long now, String statusSnapshot,
                               String receiptId) {
        ForwardOutcome o = new ForwardOutcome();
        o.mailId = mail.id;
        o.ruleId = rule.id;
        o.recipientId = rule.recipientId;
        o.recipientEmail = rule.recipientEmailSnapshot;
        o.decision = decision;
        o.receiptId = receiptId;
        o.attemptedAt = now;
        o.ruleStatusSnapshot = statusSnapshot;
        o.persist();
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    public enum ProcessReport {
        FORWARDED, BLOCKED, OUT_OF_SCOPE, FAILED_WILL_RETRY,
        SKIPPED_NO_AUTH, DEDUPED, ALREADY_DONE, MISSING
    }

    public record ReceiptAck(boolean first, String result) {
    }
}
