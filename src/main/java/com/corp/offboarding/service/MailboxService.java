package com.corp.offboarding.service;

import com.corp.offboarding.entity.MailboxOutbox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * 本地模拟邮箱服务：不连接真实 SMTP。
 * - 是否失败由 {@link FailureInjector} 决定，调用方须在事务边界外先判定，
 *   避免异常穿透事务拦截器把外层事务标记为 rollback-only；
 * - 投递动作（写发件箱）复用调用方事务，成功时与任务结论原子提交，
 *   每次成功投递返回回执号供幂等确认。
 */
@ApplicationScoped
public class MailboxService {

    @Inject
    TimeService timeService;

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message) {
            super(message);
        }
    }

    /** 转发一封业务邮件副本。无独立事务：与同步任务结论同提交/同回滚。 */
    public String deliverForward(String targetEmail, Long sourceMailId, Long ruleId,
                                 String subject, String content) {
        return placeToOutbox("FORWARD", targetEmail, sourceMailId, ruleId,
                "Fwd: " + subject, content);
    }

    /** 向外部发件人发送自动回复。 */
    public String deliverAutoReply(String targetEmail, Long sourceMailId,
                                   String subject, String body) {
        return placeToOutbox("AUTO_REPLY", targetEmail, sourceMailId, null,
                "Re: " + subject, body);
    }

    private String placeToOutbox(String type, String targetEmail, Long sourceMailId,
                                 Long ruleId, String subject, String content) {
        MailboxOutbox out = new MailboxOutbox();
        out.type = type;
        out.targetEmail = targetEmail;
        out.sourceMailId = sourceMailId;
        out.ruleId = ruleId;
        out.subject = subject;
        out.content = content;
        out.sentAt = timeService.now();
        out.persist();
        // 回执号确定性可读（实际系统为邮件服务 Message-ID）
        return "RCPT-" + type + "-" + sourceMailId
                + (ruleId != null ? "-R" + ruleId : "")
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
