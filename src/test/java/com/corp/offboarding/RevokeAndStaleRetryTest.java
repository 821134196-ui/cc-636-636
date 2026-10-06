package com.corp.offboarding;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.model.StopReason;
import com.corp.offboarding.model.TaskStatus;
import com.corp.offboarding.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
/**
 * 提前撤销 + 旧失败任务重试：
 * 邮件服务失败产生 FAILED 旧任务；审批撤销后重试必须重新核对当前授权，
 * 任务终结为 SKIPPED_NO_AUTH，规则保持 STOPPED，邮件不转发。
 */
class RevokeAndStaleRetryTest extends AbstractApiTest {

    @Inject ForwardingService forwarding;
    @Inject AutoReplyService replies;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject TimeService clock;
    @Inject FailureInjector failures;

    @Test
    void revokedRuleIsNeverReopenedByOldFailedTask() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long now = clock.now();
        var rule = forwarding.request(owner.id, lina.id, MailScope.ALL_BUSINESS,
                null, now, now + 24 * 3600_000, "王强(主管)");
        forwarding.approve(rule.id, "审批人");

        // 让本次投递失败：旧任务进入 FAILED
        failures.failNext(1);
        MailMessage mail = intake.receive(owner.id, "buyer@partner.example",
                "客户", "发货地址", "正文", false, false);
        sync.drain(forwarding, replies);

        SyncTask task = SyncTask.find("mailId = ?1 and ruleId = ?2", mail.id, rule.id)
                .firstResult();
        assertEquals(TaskStatus.FAILED, task.status);
        assertEquals(1, task.attempts);
        assertTrue(task.ruleVersionSnapshot >= 1);

        // 审批人提前撤销
        forwarding.revoke(rule.id, "管理员", "业务调整");
        assertEquals(ForwardStatus.STOPPED, fwdRule(rule.id).status);
        assertEquals(StopReason.REVOKED, fwdRule(rule.id).stopReason);

        // 旧任务重试：不允许凭旧快照恢复授权
        var report = sync.retryTask(task.id);
        assertEquals(SyncService.ProcessReport.SKIPPED_NO_AUTH, report);

        task = freshTask(task.id);
        assertEquals(TaskStatus.SKIPPED_NO_AUTH, task.status);
        assertEquals(ForwardStatus.STOPPED, fwdRule(rule.id).status);
        assertEquals(0, MailboxOutbox.count(
                "sourceMailId = ?1 and type = 'FORWARD'", mail.id));

        // 即使故障注入早已耗尽、再次重试也不会复活
        SyncService.ProcessReport again = sync.retryTask(task.id);
        assertEquals(SyncService.ProcessReport.ALREADY_DONE, again);
        assertEquals(0, MailboxOutbox.count(
                "sourceMailId = ?1 and type = 'FORWARD'", mail.id));
    }
}
