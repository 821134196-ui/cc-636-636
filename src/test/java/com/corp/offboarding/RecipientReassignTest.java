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
 * 接收人调岗：人事登记调岗后，以该员工为接收人的全部规则立即停用，
 * 排队中的旧同步任务重试也不能放行。
 */
class RecipientReassignTest extends AbstractApiTest {

    @Inject ForwardingService forwarding;
    @Inject AutoReplyService replies;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject EmployeeService employees;
    @Inject TimeService clock;

    @Test
    void reassigningRecipientStopsAllTheirRulesImmediately() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee zhao = employeeByEmail("zhaolei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long now = clock.now();

        // 两条指向赵磊的规则：一条 ACTIVE、一条 SCHEDULED；外加一条指向李娜的不应被波及
        var rActive = forwarding.request(owner.id, zhao.id, MailScope.ALL_BUSINESS,
                null, now, now + 3600_000, "主管");
        forwarding.approve(rActive.id, "审批人");
        var rFuture = forwarding.request(owner.id, zhao.id, MailScope.SENDER_ALLOWLIST,
                "x@y.com", now + 600_000, now + 7_200_000, "主管");
        forwarding.approve(rFuture.id, "审批人");
        var rOther = forwarding.request(owner.id, lina.id, MailScope.ALL_BUSINESS,
                null, now, now + 3600_000, "主管");
        forwarding.approve(rOther.id, "审批人");

        // 调岗前一封邮件进入队列
        MailMessage mail = intake.receive(owner.id, "buyer@partner.example",
                "客户", "合同", "正文", false, false);

        // 赵磊调岗：同事务联动立即停用其两条规则
        employees.reassign(zhao.id, "人事部");

        assertEquals(ForwardStatus.STOPPED, fwdRule(rActive.id).status);
        assertEquals(StopReason.RECIPIENT_REASSIGNED,
                fwdRule(rActive.id).stopReason);
        assertEquals(ForwardStatus.STOPPED, fwdRule(rFuture.id).status);
        assertEquals(StopReason.RECIPIENT_REASSIGNED,
                fwdRule(rFuture.id).stopReason);
        // 李娜的规则不受影响
        assertEquals(ForwardStatus.ACTIVE, fwdRule(rOther.id).status);

        // 队列中的旧任务执行时实时复核：接收人已调岗 → 跳过，不产生转发
        sync.drain(forwarding, replies);
        assertEquals(0, MailboxOutbox.count(
                "sourceMailId = ?1 and targetEmail = ?2 and type = 'FORWARD'",
                mail.id, zhao.email));
        SyncTask task = SyncTask.find("mailId = ?1 and ruleId = ?2", mail.id, rActive.id)
                .firstResult();
        assertEquals(TaskStatus.SKIPPED_NO_AUTH, task.status);

        // 给赵磊重新申请也必须被拒绝（调岗者不得再作为接收人）
        assertThrows(jakarta.ws.rs.BadRequestException.class, () ->
                forwarding.request(owner.id, zhao.id, MailScope.ALL_BUSINESS,
                        null, now, now + 3600_000, "主管"));
    }
}
