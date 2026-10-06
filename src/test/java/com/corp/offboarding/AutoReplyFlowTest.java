package com.corp.offboarding;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.EmployeeStatus;
import com.corp.offboarding.model.ReplyStatus;
import com.corp.offboarding.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
/** 自动回复：HR 确认内容 → 按离职时间排程 → 到点才回复；修改产生新版本；停用后不再回复。 */
class AutoReplyFlowTest extends AbstractApiTest {

    @Inject AutoReplyService replies;
    @Inject EmployeeService employees;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject ForwardingService forwarding;
    @Inject TimeService clock;

    @Test
    void replyActivatesAtDepartureAndVersionsAreKept() {
        // 新增一名尚未离职的员工，走完整 HR 流程
        var bob = employees.create("测试离职者", "bob@corp.example", "销售部");
        long departure = clock.now() + 90_000;
        employees.markOffboarding(bob.id, departure, "HR");

        var rule = replies.confirm(bob.id, "本人已离职，请联系李娜。", departure, "HR");
        assertEquals(ReplyStatus.SCHEDULED, rule.status);

        // 离职时间之前来信：不回复，也不存在转发
        var before = intake.receive(bob.id, "c@x.example", "客户", "提前咨询", "正文",
                false, false);
        replies.activateDue();
        assertEquals(0, MailboxOutbox.count("sourceMailId = ?1 and type = 'AUTO_REPLY'",
                before.id));

        // HR 修改内容 → 新版本
        replies.confirm(bob.id, "本人已离职，业务请联系李娜（lina@corp.example）。",
                departure, "HR");
        assertEquals(2, AutoReplyVersion.count("ruleId = ?1", rule.id));

        // 推进到离职时间之后：到点生效
        clock.setTime(departure + 1000);
        assertEquals(1, replies.activateDue());
        AutoReplyRule activeRule = AutoReplyRule.findById(rule.id);
        assertEquals(ReplyStatus.ACTIVE, activeRule.status);

        var after = intake.receive(bob.id, "c@x.example", "客户", "离职后来信", "正文",
                false, false);
        assertEquals(1, MailboxOutbox.count("sourceMailId = ?1 and type = 'AUTO_REPLY'",
                after.id));

        // 停用后再来信：不再回复
        replies.disable(rule.id, "HR");
        var later = intake.receive(bob.id, "c@x.example", "客户", "停用后来信", "正文",
                false, false);
        sync.drain(forwarding, replies);
        assertEquals(0, MailboxOutbox.count("sourceMailId = ?1 and type = 'AUTO_REPLY'",
                later.id));

        // 历史版本可追溯 DISABLED 状态
        long disabledVersions = AutoReplyVersion.count(
                "ruleId = ?1 and status = ?2", rule.id, ReplyStatus.DISABLED);
        assertEquals(1, disabledVersions);
        Employee bobReloaded = Employee.findById(bob.id);
        assertEquals(EmployeeStatus.OFFBOARDED, bobReloaded.status);
    }
}
