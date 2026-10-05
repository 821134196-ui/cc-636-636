package com.example.exitmail;

import com.example.exitmail.model.AutoReplyConfig;
import com.example.exitmail.model.ConfigStatus;
import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.service.AutoReplyService;
import com.example.exitmail.service.EmployeeService;
import com.example.exitmail.service.MailboxService;
import com.example.exitmail.service.SyncService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 自动回复:版本化、按离职时间生效、去重、到期停用。 */
@QuarkusTest
class AutoReplyLifecycleTest {

    @Inject
    EmployeeService employeeService;

    @Inject
    AutoReplyService autoReplyService;

    @Inject
    MailboxService mailboxService;

    @Inject
    SyncService syncService;

    @Inject
    EntityManager em;

    private long autoReplyCount(String leaverEmail) {
        return em.createQuery(
                        "select count(m) from MailMessage m where direction = com.example.exitmail.model.Direction.OUTBOUND "
                                + "and kind = com.example.exitmail.model.MailKind.AUTO_REPLY and fromAddress = :e", Long.class)
                .setParameter("e", leaverEmail).getSingleResult();
    }

    @Test
    @Transactional
    void autoReplySentOncePerMessageAndVersioned() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Employee leaver = employeeService.create("ar-" + tag + "@corp.example", "离职" + tag, "技术部");
        employeeService.setStatus(leaver.id, EmployeeStatus.DEPARTED, "人事");

        AutoReplyConfig v1 = autoReplyService.upsert(leaver, "我已离职,业务请联系李娜 lina@corp.example",
                Instant.now().minus(1, ChronoUnit.HOURS), null, "人事-赵敏");
        assertEquals(1, v1.version);

        mailboxService.receive("client@customer.example", leaver.email, "业务咨询", "您好", false);
        syncService.runFullSync();
        assertEquals(1, autoReplyCount(leaver.email));

        // 重复同步不重复回复
        syncService.runFullSync();
        assertEquals(1, autoReplyCount(leaver.email));

        // 人事修改内容 → 新版本,旧版本停用;历史来信不重复回复
        AutoReplyConfig v2 = autoReplyService.upsert(leaver, "我已离职,业务请联系王强 wangqiang@corp.example",
                Instant.now().minus(1, ChronoUnit.HOURS), null, "人事-赵敏");
        assertEquals(2, v2.version);
        List<AutoReplyConfig> history = autoReplyService.history(leaver.id);
        assertEquals(2, history.size());
        assertEquals(ConfigStatus.DISABLED, history.get(1).status);
        assertEquals("SUPERSEDED", history.get(1).disableReason);

        syncService.runFullSync();
        assertEquals(1, autoReplyCount(leaver.email), "历史来信不因改版重复回复");

        // 新来信使用新版本内容回复
        mailboxService.receive("other@customer.example", leaver.email, "新的咨询", "", false);
        syncService.runFullSync();
        assertEquals(2, autoReplyCount(leaver.email));
    }

    @Test
    @Transactional
    void autoReplyRespectsWindowAndExpiry() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Employee leaver = employeeService.create("ar2-" + tag + "@corp.example", "离职" + tag, "技术部");
        employeeService.setStatus(leaver.id, EmployeeStatus.DEPARTED, "人事");

        // 生效时间在未来 → 暂不回复
        autoReplyService.upsert(leaver, "我即将离职",
                Instant.now().plus(1, ChronoUnit.DAYS), null, "人事");
        mailboxService.receive("a@customer.example", leaver.email, "提前来信", "", false);
        syncService.runFullSync();
        assertEquals(0, autoReplyCount(leaver.email));

        // 截止时间已过 → 调度器自动停用
        String tag2 = UUID.randomUUID().toString().substring(0, 8);
        Employee leaver2 = employeeService.create("ar3-" + tag2 + "@corp.example", "离职" + tag2, "技术部");
        employeeService.setStatus(leaver2.id, EmployeeStatus.DEPARTED, "人事");
        AutoReplyConfig cfg = autoReplyService.upsert(leaver2, "我已离职",
                Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.SECONDS), "人事");
        em.createQuery("update AutoReplyConfig set endTime = :past where id = :id")
                .setParameter("past", Instant.now().minus(1, ChronoUnit.SECONDS))
                .setParameter("id", cfg.id).executeUpdate();
        em.flush();
        em.clear();
        assertEquals(1, autoReplyService.expireOverdue(Instant.now()));

        mailboxService.receive("b@customer.example", leaver2.email, "到期后来信", "", false);
        syncService.runFullSync();
        assertEquals(0, autoReplyCount(leaver2.email), "自动回复到期后不再发送");
    }
}
