package com.corp.offboarding.service;

import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.entity.ForwardingRule;
import com.corp.offboarding.entity.MailMessage;
import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.MailScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Locale;

/**
 * 授权与内容策略：所有转发出口（实时执行、旧任务重试）都必须经过这里。
 * 规则：
 * 1. 仅 ACTIVE 规则、且处于审批的有效期内才放行；
 * 2. 接收人必须仍在职（调岗立即失效）；
 * 3. 私人邮件 / 敏感主题一律不放行，即使业务转发规则有效；
 * 4. 邮件必须落在主管申请并获批的范围内。
 */
@ApplicationScoped
public class PolicyService {

    @Inject
    TimeService timeService;

    /** 实时核对转发授权（不看任务快照）。 */
    public Authorization checkAuthorization(ForwardingRule rule) {
        long now = timeService.now();

        if (rule.status != ForwardStatus.ACTIVE) {
            return Authorization.deny("规则状态为 " + rule.status + "，未生效或已停用");
        }
        if (now < rule.validFrom) {
            return Authorization.deny("尚未到生效时间");
        }
        if (now > rule.validUntil) {
            return Authorization.deny("授权已到期");
        }
        Employee recipient = Employee.findById(rule.recipientId);
        if (recipient == null) {
            return Authorization.deny("接收人不存在");
        }
        if (recipient.status != com.corp.offboarding.model.EmployeeStatus.ACTIVE) {
            return Authorization.deny("接收人已"
                    + (recipient.status == com.corp.offboarding.model.EmployeeStatus.REASSIGNED
                        ? "调岗" : "离职"));
        }
        return Authorization.ok();
    }

    /** 私人 / 敏感主题是硬拦截，优先于范围判断。 */
    public boolean isBlockedContent(MailMessage mail) {
        return mail.privateFlag || mail.sensitiveFlag;
    }

    public String blockReason(MailMessage mail) {
        if (mail.privateFlag) {
            return "BLOCKED_PRIVATE";
        }
        if (mail.sensitiveFlag) {
            return "BLOCKED_SENSITIVE";
        }
        return null;
    }

    /** 判断邮件是否落入获批范围。 */
    public boolean inScope(MailMessage mail, ForwardingRule rule) {
        MailScope scope;
        try {
            scope = MailScope.valueOf(rule.scopeType);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return switch (scope) {
            case ALL_BUSINESS -> true;
            case SENDER_ALLOWLIST -> {
                String criteria = rule.scopeCriteria == null ? "" : rule.scopeCriteria;
                yield split(criteria).contains(mail.fromEmail.trim().toLowerCase(Locale.ROOT));
            }
            case SUBJECT_KEYWORDS -> {
                String subject = mail.subject.toLowerCase(Locale.ROOT);
                yield split(rule.scopeCriteria).stream().anyMatch(subject::contains);
            }
        };
    }

    private java.util.List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return java.util.List.of();
        }
        return Arrays.stream(s.split("[;；,，]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(x -> x.toLowerCase(Locale.ROOT))
                .toList();
    }
}
