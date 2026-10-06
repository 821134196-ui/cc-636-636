package com.corp.offboarding;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.model.OutcomeDecision;
import com.corp.offboarding.model.StopReason;
import com.corp.offboarding.model.TaskStatus;
import com.corp.offboarding.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
/** 到期停用：超过截止日期后规则立即 STOPPED(EXPIRED)，新旧同步任务都不得再转发。 */
class ExpiryTest extends AbstractApiTest {

    @Inject ForwardingService forwarding;
    @Inject AutoReplyService replies;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject TimeService clock;

    private long setupActiveRuleWithShortTtl(Employee owner, Employee recipient) {
        long now = clock.now();
        var rule = forwarding.request(owner.id, recipient.id, MailScope.ALL_BUSINESS,
                null, now, now + 60_000, "主管");
        forwarding.approve(rule.id, "审批人");
        return rule.id;
    }

    @Test
    void ruleExpiresAndAllTasksAreRejectedWithoutReopening() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long ruleId = setupActiveRuleWithShortTtl(owner, lina);

        // 有效期内：正常转发
        intake.receive(owner.id, "buyer@partner.example", "客户", "合同", "正文", false, false);
        assertEquals(1, sync.drain(forwarding, replies));
        ForwardingRule rule = ForwardingRule.findById(ruleId);
        assertEquals(ForwardStatus.ACTIVE, rule.status);
        assertEquals(1, ForwardOutcome.count("ruleId = ?1 and decision = ?2",
                ruleId, OutcomeDecision.FORWARDED));

        // 时钟越过截止日期；来新邮件时规则尚未被维护扫描，任务仍会入队
        clock.advanceMinutes(2);
        MailMessage lateMail = intake.receive(owner.id, "buyer@partner.example",
                "客户", "发票", "正文", false, false);

        // drain 先执行到点维护：规则到期 STOPPED；随后处理任务时实时核对授权 → 跳过
        sync.drain(forwarding, replies);

        rule = fwdRule(ruleId);
        assertEquals(ForwardStatus.STOPPED, rule.status);
        assertEquals(StopReason.EXPIRED, rule.stopReason);
        assertNotNull(rule.stoppedAt);

        SyncTask queried = SyncTask.find("mailId = ?1 and ruleId = ?2",
                lateMail.id, ruleId).firstResult();
        SyncTask lateTask = freshTask(queried.id);
        assertEquals(TaskStatus.SKIPPED_NO_AUTH, lateTask.status);

        // 再重试一次旧任务：仍然跳过，规则不被重新打开
        var report = sync.retryTask(lateTask.id);
        assertEquals(SyncService.ProcessReport.ALREADY_DONE, report);
        assertEquals(ForwardStatus.STOPPED, fwdRule(ruleId).status);
        assertEquals(StopReason.EXPIRED, fwdRule(ruleId).stopReason);

        // 到期后邮件没有任何转发副本
        long forwardsOfLateMail = MailboxOutbox.count(
                "sourceMailId = ?1 and type = 'FORWARD'", lateMail.id);
        assertEquals(0, forwardsOfLateMail);

        // 审计中可核对停用时间
        List<String> actions = AuditLog.find("entityType = 'FORWARDING_RULE' and entityId = ?1",
                        ruleId).list().stream().map(a -> ((AuditLog) a).action).toList();
        assertTrue(actions.contains("FORWARD_EXPIRED"));
    }
}
