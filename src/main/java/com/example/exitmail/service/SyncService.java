package com.example.exitmail.service;

import com.example.exitmail.model.AutoReplyConfig;
import com.example.exitmail.model.AutoReplyReceipt;
import com.example.exitmail.model.Direction;
import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.model.ForwardReceipt;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.MailKind;
import com.example.exitmail.model.MailMessage;
import com.example.exitmail.model.ReceiptResult;
import com.example.exitmail.model.RuleScope;
import com.example.exitmail.model.RuleStatus;
import com.example.exitmail.model.SyncTask;
import com.example.exitmail.model.TaskStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;

/**
 * 同步引擎:对离职员工邮箱执行自动回复与授权转发。
 *
 * 关键不变量:
 * 1. 每次执行(含失败重试)都通过 AuthorizationService 实时核对授权,
 *    授权失效只取消任务/跳过转发,绝不把规则改回生效。
 * 2. 私人邮件与敏感主题在授权有效时同样被拦截。
 * 3. 回执去重:同一规则+同一邮件只处理一次;同一邮件只自动回复一次。
 */
@ApplicationScoped
public class SyncService {

    @Inject
    EntityManager em;

    @Inject
    EmployeeService employeeService;

    @Inject
    AutoReplyService autoReplyService;

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    MailFilterService mailFilterService;

    @Inject
    MailboxService mailboxService;

    @Inject
    AuditService audit;

    @ConfigProperty(name = "exitmail.sync.max-attempts")
    int maxAttempts;

    public List<SyncTask> listTasks() {
        return em.createQuery("from SyncTask order by id desc", SyncTask.class)
                .setMaxResults(200).getResultList();
    }

    public List<ForwardReceipt> forwardReceipts() {
        return em.createQuery("from ForwardReceipt order by id desc", ForwardReceipt.class)
                .setMaxResults(200).getResultList();
    }

    public List<AutoReplyReceipt> autoReplyReceipts() {
        return em.createQuery("from AutoReplyReceipt order by id desc", AutoReplyReceipt.class)
                .setMaxResults(200).getResultList();
    }

    /** 执行所有待处理与可重试的失败任务。 */
    @Transactional
    public int runDueTasks() {
        List<SyncTask> tasks = em.createQuery(
                        "from SyncTask where status = :p or (status = :f and attempts < :max) order by id",
                        SyncTask.class)
                .setParameter("p", TaskStatus.PENDING)
                .setParameter("f", TaskStatus.FAILED)
                .setParameter("max", maxAttempts)
                .getResultList();
        for (SyncTask t : tasks) {
            executeTask(t);
        }
        return tasks.size();
    }

    /** 手动重试指定任务(旧任务重试同样实时核对授权)。 */
    @Transactional
    public SyncTask retryTask(Long taskId) {
        SyncTask t = em.find(SyncTask.class, taskId);
        if (t == null) {
            throw new NotFoundException("任务不存在: " + taskId);
        }
        if (t.status == TaskStatus.SUCCESS || t.status == TaskStatus.CANCELLED) {
            return t; // 终态任务不重复执行
        }
        executeTask(t);
        return t;
    }

    /** 全量扫描:对所有已离职员工的收件箱做幂等同步(回执去重保证不重复)。 */
    @Transactional
    public void runFullSync() {
        List<Employee> departed = em.createQuery("from Employee where status = :s", Employee.class)
                .setParameter("s", EmployeeStatus.DEPARTED).getResultList();
        for (Employee e : departed) {
            for (MailMessage msg : mailboxService.inboundFor(e.email)) {
                processMessage(e, msg);
            }
        }
        runDueTasks();
    }

    private void executeTask(SyncTask task) {
        task.attempts++;
        task.lastAttemptAt = Instant.now();
        MailMessage msg = em.find(MailMessage.class, task.mailMessageId);
        try {
            if (msg == null) {
                throw new IllegalStateException("邮件不存在: " + task.mailMessageId);
            }
            // 演示用故障注入:主题含 [flaky] 的邮件首次投递失败,重试成功
            if (msg.subject != null && msg.subject.contains("[flaky]") && task.attempts == 1) {
                throw new IllegalStateException("模拟邮箱 I/O 故障,请重试");
            }
            Employee owner = employeeService.findByEmail(task.mailbox);
            if (owner == null || owner.status != EmployeeStatus.DEPARTED) {
                task.status = TaskStatus.CANCELLED;
                task.lastError = "邮箱属主不是已离职状态,任务取消";
                audit.log("SYNC", "TASK_CANCELLED", "sync",
                        "任务 #" + task.id + ": " + task.lastError);
                return;
            }
            // 任务关联的规则必须当前仍然生效;失效则取消任务,绝不重新启用
            if (task.ruleId != null) {
                ForwardRule rule = em.find(ForwardRule.class, task.ruleId);
                String deny = authorizationService.checkForwardingAuthorized(rule, Instant.now());
                if (deny != null) {
                    task.status = TaskStatus.CANCELLED;
                    task.lastError = "授权已失效: " + deny;
                    audit.log("SYNC", "TASK_CANCELLED", "sync",
                            "任务 #" + task.id + " 取消:规则 id=" + task.ruleId + " " + deny
                                    + "(不重新启用已停用规则)");
                    return;
                }
            }
            processMessage(owner, msg);
            task.status = TaskStatus.SUCCESS;
        } catch (Exception e) {
            task.status = TaskStatus.FAILED;
            task.lastError = e.getMessage();
            audit.log("SYNC", "TASK_FAILED", "sync",
                    "任务 #" + task.id + " 第 " + task.attempts + " 次执行失败: " + e.getMessage());
        }
    }

    /**
     * 处理单封来信:先自动回复,再按当前授权转发。幂等,可重复调用。
     */
    public void processMessage(Employee owner, MailMessage msg) {
        if (msg.direction != Direction.INBOUND || msg.kind != MailKind.NORMAL) {
            return;
        }
        Instant now = Instant.now();
        // 1) 自动回复(每封来信只回一次,回执去重)
        AutoReplyConfig cfg = autoReplyService.activeConfig(owner, now);
        if (cfg != null && !autoReplyReceiptExists(owner.id, msg.id)) {
            mailboxService.sendOutbound(MailKind.AUTO_REPLY, owner.email, msg.fromAddress,
                    "【自动回复】" + (msg.subject == null ? "" : msg.subject), cfg.content, msg.id);
            em.persist(new AutoReplyReceipt(owner.id, cfg.id, msg.id, msg.fromAddress));
            audit.log("SYNC", "AUTO_REPLY_SENT", "sync",
                    "员工 " + owner.email + " 自动回复 v" + cfg.version + " -> " + msg.fromAddress
                            + " (邮件 #" + msg.id + ")");
        }
        // 2) 授权转发(实时核对 + 敏感过滤 + 回执去重)
        ForwardRule rule = forwardRuleService.latestActive(owner);
        if (rule == null) {
            return;
        }
        String deny = authorizationService.checkForwardingAuthorized(rule, now);
        if (deny != null) {
            // 规则处于 ACTIVE 但实时校验失败(如接收人刚调岗、刚过截止),只记录不转发
            if (!deny.startsWith("未到生效时间")) {
                audit.log("SYNC", "FORWARD_SKIPPED_UNAUTHORIZED", "sync",
                        "规则 v" + rule.version + "(id=" + rule.id + ") 邮件 #" + msg.id + " 未转发: " + deny);
            }
            return;
        }
        if (forwardReceiptExists(rule.id, msg.id)) {
            return; // 重复同步/重复回执:已处理过,直接跳过
        }
        if (!inScope(rule, msg)) {
            return; // 不在业务范围内,静默跳过(不算拦截)
        }
        String block = mailFilterService.blockReason(msg);
        if (block != null) {
            em.persist(new ForwardReceipt(rule.id, msg.id, rule.recipientEmail, ReceiptResult.BLOCKED_SENSITIVE));
            audit.log("SYNC", "FORWARD_BLOCKED_SENSITIVE", "sync",
                    "规则 v" + rule.version + " 邮件 #" + msg.id + " 被拦截(即使授权有效也不放行): " + block
                            + "; 主题=" + msg.subject);
            return;
        }
        mailboxService.sendOutbound(MailKind.FORWARD, owner.email, rule.recipientEmail,
                "【转发】" + (msg.subject == null ? "" : msg.subject),
                "原始发件人: " + msg.fromAddress + "\n\n" + (msg.body == null ? "" : msg.body), msg.id);
        em.persist(new ForwardReceipt(rule.id, msg.id, rule.recipientEmail, ReceiptResult.FORWARDED));
        audit.log("SYNC", "FORWARDED", "sync",
                "规则 v" + rule.version + " 邮件 #" + msg.id + " 已转发: " + owner.email + " -> "
                        + rule.recipientEmail + "; 范围=" + rule.scopeDescription()
                        + "; 截止=" + rule.endDate);
    }

    private boolean inScope(ForwardRule rule, MailMessage msg) {
        if (rule.scope != RuleScope.SUBJECT_KEYWORDS) {
            return true;
        }
        String subject = msg.subject == null ? "" : msg.subject.toLowerCase();
        for (String kw : rule.scopeKeywords.split("[,，]")) {
            if (!kw.trim().isEmpty() && subject.contains(kw.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean forwardReceiptExists(Long ruleId, Long messageId) {
        return !em.createQuery("select 1 from ForwardReceipt where dedupKey = :k", Integer.class)
                .setParameter("k", ruleId + ":" + messageId).setMaxResults(1).getResultList().isEmpty();
    }

    private boolean autoReplyReceiptExists(Long employeeId, Long messageId) {
        return !em.createQuery("select 1 from AutoReplyReceipt where dedupKey = :k", Integer.class)
                .setParameter("k", employeeId + ":" + messageId).setMaxResults(1).getResultList().isEmpty();
    }
}
