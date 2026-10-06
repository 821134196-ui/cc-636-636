package com.corp.offboarding.bootstrap;

import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.service.AutoReplyService;
import com.corp.offboarding.service.EmployeeService;
import com.corp.offboarding.service.MailIntakeService;
import com.corp.offboarding.service.TimeService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 幂等演示数据：一组员工 + 一位已确认离职的销售。
 * 转发申请/审批留给页面操作，避免启动即自动授权。
 *
 * dev/test 下 Hibernate 的 schema 迁移在独立的 JPA 启动线程执行，
 * 与 StartupEvent 播种事务在 SQLite 上可能短暂撞锁（SQLITE_BUSY）。
 * 因此：先等全部表建完，再在独立事务中带锁退避重试播种。
 */
@ApplicationScoped
public class DataSeeder {

    private static final String[] REQUIRED_TABLES = {
            "employee", "sim_clock", "auto_reply_rule", "auto_reply_version",
            "forwarding_rule", "forwarding_version", "mail_message",
            "forward_outcome", "sync_task", "audit_log", "mailbox_outbox"};

    @Inject
    EmployeeService employees;

    @Inject
    AutoReplyService replies;

    @Inject
    MailIntakeService intake;

    @Inject
    TimeService time;

    @Inject
    EntityManager em;

    @Inject
    Provider<DataSeeder> self;

    void onStart(@Observes StartupEvent ev) {
        if (!waitForSchema()) {
            throw new IllegalStateException("等待数据库建表超时");
        }
        for (int attempt = 1; attempt <= 15; attempt++) {
            try {
                self.get().seedOnce();
                return;
            } catch (RuntimeException e) {
                if (!isLockFailure(e) || attempt == 15) {
                    throw e;
                }
                sleep(500L * attempt);
            }
        }
    }

    private boolean waitForSchema() {
        for (int i = 0; i < 60; i++) {
            try {
                if (tablesReady()) {
                    return true;
                }
            } catch (RuntimeException e) {
                if (!isLockFailure(e)) {
                    throw e;
                }
            }
            sleep(1000);
        }
        return false;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    boolean tablesReady() {
        @SuppressWarnings("unchecked")
        java.util.List<String> names = em.createNativeQuery(
                        "select name from sqlite_master where type = 'table'").getResultList();
        return names.containsAll(java.util.Arrays.asList(REQUIRED_TABLES));
    }

    private boolean isLockFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && (msg.contains("SQLITE_BUSY") || msg.contains("locked")
                    || msg.contains("acquisition timeout"))) {
                return true;
            }
        }
        return false;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void seedOnce() {
        if (Employee.count() > 0) {
            return;
        }
        var alice = employees.create("张伟（离职销售）", "zhangwei@corp.example", "销售部");
        employees.create("李娜（接替销售）", "lina@corp.example", "销售部");
        employees.create("王强（销售主管）", "wangqiang@corp.example", "销售部");
        employees.create("陈静（HRBP）", "chenjing@corp.example", "人事部");
        employees.create("赵磊（其他部门）", "zhaolei@corp.example", "产品部");

        long departure = time.now() - 60_000L;
        employees.markOffboarding(alice.id, departure, "陈静(HR)");
        replies.confirm(alice.id,
                "您好，张伟已于 2026-10-05 离职，后续业务由销售部李娜（lina@corp.example）"
                        + "衔接。给您带来不便敬请谅解，紧急事项请联系销售主管王强。",
                departure, "陈静(HR)");

        // 一封正常业务来信（会触发自动回复；转发任务需审批后手动触发/等待调度）
        intake.receive(alice.id, "client.chen@acme-client.com", "陈经理",
            "Q4 采购合同进度确认", "想确认一下之前谈的 Q4 框架合同什么时候能签署？",
            false, false);
        // 一封敏感主题来信，即使存在有效转发规则也不得转发
        intake.receive(alice.id, "hr@headhunter.example", "猎头",
            "关于薪资和股权安排的私人沟通", "方便聊聊新机会的薪酬包吗？", false, false);
    }
}
