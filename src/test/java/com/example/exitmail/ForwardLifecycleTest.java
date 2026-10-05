package com.example.exitmail;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.MailMessage;
import com.example.exitmail.model.RuleScope;
import com.example.exitmail.model.RuleStatus;
import com.example.exitmail.model.SyncTask;
import com.example.exitmail.model.TaskStatus;
import com.example.exitmail.service.EmployeeService;
import com.example.exitmail.service.ForwardRuleService;
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

/**
 * 转发授权生命周期验证:
 * 到期、提前撤销、接收人调岗都必须立即停止转发;
 * 旧同步任务重试与重复回执不得恢复失效授权。
 */
@QuarkusTest
class ForwardLifecycleTest {

    @Inject
    EmployeeService employeeService;

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    MailboxService mailboxService;

    @Inject
    SyncService syncService;

    @Inject
    EntityManager em;

    private String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional
    Employee departed(String tag) {
        Employee e = employeeService.create("leaver-" + tag + "@corp.example", "离职" + tag, "销售部");
        employeeService.setStatus(e.id, EmployeeStatus.DEPARTED, "test");
        return e;
    }

    @Transactional
    Employee activeEmployee(String tag) {
        return employeeService.create("recv-" + tag + "@corp.example", "接替" + tag, "销售部");
    }

    @Transactional
    ForwardRule approvedRule(Employee leaver, String recipientEmail, Instant end) {
        ForwardRule r = forwardRuleService.request(leaver, recipientEmail, RuleScope.ALL_BUSINESS, null,
                Instant.now().minus(1, ChronoUnit.HOURS), end, "主管");
        return forwardRuleService.approve(r.id, "审批人");
    }

    private long forwardCount(String recipient) {
        return em.createQuery(
                        "select count(m) from MailMessage m where direction = com.example.exitmail.model.Direction.OUTBOUND "
                                + "and kind = com.example.exitmail.model.MailKind.FORWARD and toAddress = :r", Long.class)
                .setParameter("r", recipient).getSingleResult();
    }

    private long receiptCount(Long ruleId) {
        return em.createQuery("select count(r) from ForwardReceipt r where r.ruleId = :id", Long.class)
                .setParameter("id", ruleId).getSingleResult();
    }

    @Test
    @Transactional
    void approvedRuleForwardsBusinessMail() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        mailboxService.receive("client@customer.example", leaver.email, "合同续签咨询", "请报价", false);
        syncService.runFullSync();

        assertEquals(1, forwardCount(recv.email));
        assertEquals(1, receiptCount(rule.id));
    }

    @Test
    @Transactional
    void expiredRuleStopsForwarding() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        // 批准时截止在未来,随后用调度器到期检查使其失效
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(2, ChronoUnit.SECONDS));

        mailboxService.receive("client@customer.example", leaver.email, "订单进度", "请回复", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email));

        // 直接把截止日期改到过去,模拟时间流逝后触发到期检查
        em.createQuery("update ForwardRule set endDate = :past where id = :id")
                .setParameter("past", Instant.now().minus(1, ChronoUnit.SECONDS))
                .setParameter("id", rule.id).executeUpdate();
        em.flush();
        em.clear();
        int expired = forwardRuleService.expireOverdue(Instant.now());
        assertEquals(1, expired);
        assertEquals(RuleStatus.EXPIRED, forwardRuleService.get(rule.id).status);

        // 到期后来信不再转发
        mailboxService.receive("client2@customer.example", leaver.email, "新的询价", "请报价", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email), "到期后不得再转发");
    }

    @Test
    @Transactional
    void revokedRuleStopsForwardingImmediately() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        mailboxService.receive("a@customer.example", leaver.email, "报价请求", "", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email));

        forwardRuleService.revoke(rule.id, "审批人");
        assertEquals(RuleStatus.REVOKED, forwardRuleService.get(rule.id).status);

        mailboxService.receive("b@customer.example", leaver.email, "追加订单", "", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email), "撤销后不得再转发");
    }

    @Test
    @Transactional
    void recipientTransferStopsForwarding() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        mailboxService.receive("a@customer.example", leaver.email, "技术咨询", "", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email));

        // 接收人调岗 → 规则立即停用
        employeeService.setStatus(recv.id, EmployeeStatus.TRANSFERRED, "人事");
        assertEquals(RuleStatus.DISABLED, forwardRuleService.get(rule.id).status);
        assertEquals("RECIPIENT_TRANSFERRED", forwardRuleService.get(rule.id).closeReason);

        mailboxService.receive("b@customer.example", leaver.email, "再次咨询", "", false);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email), "接收人调岗后不得再转发");
    }

    @Test
    @Transactional
    void retriedTaskDoesNotReviveDisabledRule() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        // 来信生成同步任务,但任务尚未执行
        MailMessage msg = mailboxService.receive("c@customer.example", leaver.email, "待同步邮件", "", false);
        List<SyncTask> tasks = em.createQuery("from SyncTask where mailMessageId = :m", SyncTask.class)
                .setParameter("m", msg.id).getResultList();
        assertEquals(1, tasks.size());
        SyncTask task = tasks.get(0);
        assertEquals(rule.id, task.ruleId);

        // 任务执行前规则被停用(模拟审批撤销)
        forwardRuleService.revoke(rule.id, "审批人");
        // 撤销时未执行任务已被一并取消
        assertEquals(TaskStatus.CANCELLED, em.find(SyncTask.class, task.id).status);

        // 旧任务重试:不得转发,不得把规则改回生效
        syncService.retryTask(task.id);
        assertEquals(0, forwardCount(recv.email));
        assertEquals(RuleStatus.REVOKED, forwardRuleService.get(rule.id).status, "旧任务不得恢复失效授权");

        // 全量同步同样遵守失效授权
        syncService.runFullSync();
        assertEquals(0, forwardCount(recv.email));
        assertEquals(RuleStatus.REVOKED, forwardRuleService.get(rule.id).status);
    }

    @Test
    @Transactional
    void failedTaskRetriesButRespectsCurrentAuthorization() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        // [flaky] 主题首次执行失败
        MailMessage msg = mailboxService.receive("d@customer.example", leaver.email, "[flaky] 重要客户", "", false);
        syncService.runDueTasks();
        SyncTask task = em.createQuery("from SyncTask where mailMessageId = :m", SyncTask.class)
                .setParameter("m", msg.id).getSingleResult();
        assertEquals(TaskStatus.FAILED, task.status);

        // 失败期间授权被撤销 → 重试应取消任务而不是转发
        forwardRuleService.revoke(rule.id, "审批人");
        // 撤销已把 FAILED 任务取消
        assertEquals(TaskStatus.CANCELLED, em.find(SyncTask.class, task.id).status);
        syncService.retryTask(task.id);
        assertEquals(0, forwardCount(recv.email));
        assertEquals(RuleStatus.REVOKED, forwardRuleService.get(rule.id).status);
    }

    @Test
    @Transactional
    void duplicateSyncDoesNotDuplicateForward() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        mailboxService.receive("e@customer.example", leaver.email, "重复同步测试", "", false);
        syncService.runFullSync();
        syncService.runFullSync();
        syncService.runFullSync();

        assertEquals(1, forwardCount(recv.email), "重复同步不得重复转发");
        assertEquals(1, receiptCount(rule.id), "同一规则同一邮件只产生一条回执");
    }

    @Test
    @Transactional
    void sensitiveAndPrivateMailNeverForwarded() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        mailboxService.receive("hr@corp.example", leaver.email, "薪资调整确认", "请查收", false);
        mailboxService.receive("friend@personal.example", leaver.email, "周末聚会", "私人聚会", true);
        mailboxService.receive("client@customer.example", leaver.email, "正常业务往来", "报价", false);
        syncService.runFullSync();

        assertEquals(1, forwardCount(recv.email), "只有正常业务邮件可转发");
        // 两封被拦截的邮件产生 BLOCKED 回执,重复同步也不会放行
        long blocked = em.createQuery(
                        "select count(r) from ForwardReceipt r where r.ruleId = :id and r.result = "
                                + "com.example.exitmail.model.ReceiptResult.BLOCKED_SENSITIVE", Long.class)
                .setParameter("id", rule.id).getSingleResult();
        assertEquals(2, blocked);
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email));
    }

    @Test
    @Transactional
    void pendingRuleDoesNotForwardUntilApproved() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = forwardRuleService.request(leaver, recv.email, RuleScope.ALL_BUSINESS, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), "主管");

        mailboxService.receive("f@customer.example", leaver.email, "待审批期间来信", "", false);
        syncService.runFullSync();
        assertEquals(0, forwardCount(recv.email), "未审批不得转发");

        forwardRuleService.approve(rule.id, "审批人");
        syncService.runFullSync();
        assertEquals(1, forwardCount(recv.email), "审批后补发历史来信");
    }

    @Test
    @Transactional
    void editCreatesNewVersionAndSupersedesOld() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule v1 = approvedRule(leaver, recv.email, Instant.now().plus(1, ChronoUnit.DAYS));

        // 主管修改接收人 → 旧版本立即停用,新版本待审批
        Employee recv2 = employeeService.create("recv2-" + tag + "@corp.example", "接替二" + tag, "销售部");
        ForwardRule v2 = forwardRuleService.request(leaver, recv2.email, RuleScope.ALL_BUSINESS, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.DAYS), "主管");

        assertEquals(RuleStatus.DISABLED, forwardRuleService.get(v1.id).status);
        assertEquals("SUPERSEDED", forwardRuleService.get(v1.id).closeReason);
        assertEquals(RuleStatus.PENDING_APPROVAL, v2.status);
        assertEquals(2, v2.version);

        mailboxService.receive("g@customer.example", leaver.email, "改版期间来信", "", false);
        syncService.runFullSync();
        assertEquals(0, forwardCount(recv.email));
        assertEquals(0, forwardCount(recv2.email));

        forwardRuleService.approve(v2.id, "审批人");
        syncService.runFullSync();
        assertEquals(0, forwardCount(recv.email), "旧接收人不再接收");
        assertEquals(1, forwardCount(recv2.email));
    }

    @Test
    @Transactional
    void keywordScopeLimitsForwardedMail() {
        String tag = uniq();
        Employee leaver = departed(tag);
        Employee recv = activeEmployee(tag);
        ForwardRule rule = forwardRuleService.request(leaver, recv.email, RuleScope.SUBJECT_KEYWORDS, "合同,报价",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.DAYS), "主管");
        forwardRuleService.approve(rule.id, "审批人");

        mailboxService.receive("h@customer.example", leaver.email, "合同条款确认", "", false);
        mailboxService.receive("i@customer.example", leaver.email, "团建通知", "", false);
        syncService.runFullSync();

        assertEquals(1, forwardCount(recv.email), "仅关键词范围内的邮件被转发");
    }
}
