package com.corp.offboarding.service;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.ReplyStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Locale;

/**
 * 模拟外部来信处理：
 * 1. 邮件落库，自动识别/合并私人与敏感标记；
 * 2. 自动回复生效中则向外部发件人投递业务衔接提示（每封仅一次）；
 * 3. 为该员工每条 ACTIVE 转发规则创建同步任务（去重）。
 *    是否真正转发由 {@link SyncService} 执行时实时判定。
 */
@ApplicationScoped
public class MailIntakeService {

    /** 敏感主题词（中英），命中即强制不转发。 */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "密码", "工资", "薪酬", "薪资", "体检", "病历", "诊断", "辞退", "股权",
            "password", "passwd", "salary", "payroll", "medical", "diagnosis",
            "confidential", "nda");

    /** 明显的私人邮件标记。 */
    private static final List<String> PRIVATE_KEYWORDS = List.of(
            "私人", "私事", "家人", "private", "personal");

    @Inject
    TimeService timeService;

    @Inject
    MailboxService mailbox;

    @Inject
    AuditService audit;

    @Transactional
    public MailMessage receive(Long ownerId, String fromEmail, String fromName,
                               String subject, String body,
                               boolean markPrivate, boolean markSensitive) {
        Employee owner = Employee.findById(ownerId);
        if (owner == null) {
            throw new BadRequestException("收件员工不存在");
        }
        if (fromEmail == null || fromEmail.isBlank() || subject == null) {
            throw new BadRequestException("发件人与主题不能为空");
        }

        MailMessage mail = new MailMessage();
        mail.ownerId = ownerId;
        mail.fromEmail = fromEmail.trim();
        mail.fromName = fromName;
        mail.subject = subject;
        mail.body = body == null ? "" : body;
        mail.receivedAt = timeService.now();
        String haystack = (subject + " " + mail.body).toLowerCase(Locale.ROOT);
        mail.privateFlag = markPrivate || containsAny(haystack, PRIVATE_KEYWORDS);
        mail.sensitiveFlag = markSensitive || containsAny(haystack, SENSITIVE_KEYWORDS);
        mail.persist();

        audit.record("EXTERNAL:" + mail.fromEmail, "MAIL_RECEIVED", "MAIL", mail.id,
                audit.detail("owner", owner.email, "subject", subject,
                        "private", mail.privateFlag, "sensitive", mail.sensitiveFlag));

        sendAutoReplyIfActive(owner, mail);
        enqueueForwardingTasks(owner, mail);
        return mail;
    }

    private void sendAutoReplyIfActive(Employee owner, MailMessage mail) {
        AutoReplyRule reply = AutoReplyRule.findByEmployee(owner.id);
        if (reply != null && reply.status == ReplyStatus.ACTIVE && !mail.replySent) {
            mailbox.deliverAutoReply(mail.fromEmail, mail.id, mail.subject, reply.body);
            mail.replySent = true;
            audit.record("SYSTEM", "AUTO_REPLY_SENT", "MAIL", mail.id,
                    audit.detail("to", mail.fromEmail, "replyRuleId", reply.id,
                            "replyVersion", reply.currentVersion));
        }
    }

    private void enqueueForwardingTasks(Employee owner, MailMessage mail) {
        List<ForwardingRule> rules = ForwardingRule
                .list("employeeId = ?1 and status = ?2", owner.id, ForwardStatus.ACTIVE);
        long now = timeService.now();
        for (ForwardingRule rule : rules) {
            boolean exists = SyncTask.count("mailId = ?1 and ruleId = ?2",
                    mail.id, rule.id) > 0;
            if (exists) {
                continue;
            }
            SyncTask task = new SyncTask();
            task.mailId = mail.id;
            task.ruleId = rule.id;
            task.ruleVersionSnapshot = rule.currentVersion;
            task.attempts = 0;
            task.maxAttempts = 5;
            task.nextAttemptAt = now;
            task.createdAt = now;
            task.updatedAt = now;
            task.persist();
            audit.record("SYSTEM", "SYNC_TASK_CREATED", "SYNC_TASK", task.id,
                    audit.detail("mailId", mail.id, "ruleId", rule.id,
                            "ruleVersionSnapshot", rule.currentVersion,
                            "recipient", rule.recipientEmailSnapshot));
        }
    }

    private boolean containsAny(String haystack, List<String> keywords) {
        return keywords.stream().anyMatch(k -> haystack.contains(k.toLowerCase(Locale.ROOT)));
    }
}
