package com.corp.offboarding;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.model.OutcomeDecision;
import com.corp.offboarding.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
/**
 * 内容与范围策略：
 * - 私人邮件 / 敏感主题即使业务转发规则 ACTIVE 也不得转发；
 * - SENDER_ALLOWLIST / SUBJECT_KEYWORDS 范围之外的邮件不转发；
 * - 同一邮件 × 同一规则只有一个处置结论（天然幂等，不重复投递）。
 */
class ContentFilterAndScopeTest extends AbstractApiTest {

    @Inject ForwardingService forwarding;
    @Inject AutoReplyService replies;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject TimeService clock;

    private long approvedRule(Employee owner, Employee rcpt, MailScope scope, String criteria) {
        long now = clock.now();
        var rule = forwarding.request(owner.id, rcpt.id, scope, criteria,
                now, now + 24 * 3600_000, "主管");
        forwarding.approve(rule.id, "审批人");
        return rule.id;
    }

    private OutcomeDecision drainAndDecision(long mailId, long ruleId) {
        sync.drain(forwarding, replies);
        ForwardOutcome o = ForwardOutcome
                .find("mailId = ?1 and ruleId = ?2", mailId, ruleId).firstResult();
        return o == null ? null : o.decision;
    }

    @Test
    void privateAndSensitiveMailsAreNeverForwarded() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long ruleId = approvedRule(owner, lina, MailScope.ALL_BUSINESS, null);

        var priv = intake.receive(owner.id, "friend@x.example", "朋友", "周末打球",
                "私人事情", true, false);
        assertEquals(OutcomeDecision.BLOCKED_PRIVATE,
                drainAndDecision(priv.id, ruleId));

        var sens = intake.receive(owner.id, "hr@x.example", "HR", "年度调薪通知",
                "工资保密", false, false); // "调薪/工资" 命中敏感词
        assertEquals(OutcomeDecision.BLOCKED_SENSITIVE,
                drainAndDecision(sens.id, ruleId));

        var sensFlag = intake.receive(owner.id, "a@x.example", "A", "普通主题",
                "正文", false, true);
        assertEquals(OutcomeDecision.BLOCKED_SENSITIVE,
                drainAndDecision(sensFlag.id, ruleId));

        assertEquals(0, MailboxOutbox.count("type = 'FORWARD'"));
    }

    @Test
    void scopeRestrictionsAreEnforced() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");

        long senderRule = approvedRule(owner, lina, MailScope.SENDER_ALLOWLIST,
                "allowed@partner.example");
        var in = intake.receive(owner.id, "allowed@partner.example", "客户",
                "合同", "正文", false, false);
        var out = intake.receive(owner.id, "stranger@partner.example", "陌生人",
                "合同", "正文", false, false);
        assertEquals(OutcomeDecision.FORWARDED, drainAndDecision(in.id, senderRule));
        assertEquals(OutcomeDecision.BLOCKED_OUT_OF_SCOPE,
                drainAndDecision(out.id, senderRule));

        long kwRule = approvedRule(owner, lina, MailScope.SUBJECT_KEYWORDS, "合同;发票");
        var hit = intake.receive(owner.id, "x@partner.example", "X",
                "Q4 合同盖章", "正文", false, false);
        var miss = intake.receive(owner.id, "x@partner.example", "X",
                "团建安排", "正文", false, false);
        assertEquals(OutcomeDecision.FORWARDED, drainAndDecision(hit.id, kwRule));
        assertEquals(OutcomeDecision.BLOCKED_OUT_OF_SCOPE,
                drainAndDecision(miss.id, kwRule));
    }

    @Test
    void repeatedDrainDoesNotDuplicateForward() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long ruleId = approvedRule(owner, lina, MailScope.ALL_BUSINESS, null);
        var mail = intake.receive(owner.id, "buyer@partner.example", "客户",
                "合同", "正文", false, false);

        sync.drain(forwarding, replies);
        sync.drain(forwarding, replies);
        sync.drain(forwarding, replies);

        assertEquals(1, ForwardOutcome.count("mailId = ?1 and ruleId = ?2",
                mail.id, ruleId));
        assertEquals(1, MailboxOutbox.count(
                "sourceMailId = ?1 and type = 'FORWARD'", mail.id));
        assertEquals(1, SyncTask.count("mailId = ?1 and ruleId = ?2", mail.id, ruleId));
    }
}
