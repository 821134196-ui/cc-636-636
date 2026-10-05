package com.example.exitmail.service;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.RuleScope;
import com.example.exitmail.model.RuleStatus;
import com.example.exitmail.model.TaskStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 转发授权规则生命周期:
 * 主管申请(PENDING_APPROVAL)→ 审批(ACTIVE)→ 撤销/到期/调岗/手动(终态)。
 * 修改即新版本,旧版本立即停用;终态不可恢复,只能重新申请审批。
 */
@ApplicationScoped
public class ForwardRuleService {

    @Inject
    EntityManager em;

    @Inject
    AuditService audit;

    public ForwardRule get(Long id) {
        ForwardRule r = em.find(ForwardRule.class, id);
        if (r == null) {
            throw new NotFoundException("转发规则不存在: " + id);
        }
        return r;
    }

    public List<ForwardRule> history(Long employeeId) {
        return em.createQuery("from ForwardRule where employee.id = :id order by version desc", ForwardRule.class)
                .setParameter("id", employeeId).getResultList();
    }

    /** 每个员工邮箱的最新一版规则(管理页面列表) */
    public List<ForwardRule> latestPerEmployee() {
        List<ForwardRule> all = em.createQuery("from ForwardRule order by employee.id, version desc",
                ForwardRule.class).getResultList();
        Map<Long, ForwardRule> latest = new LinkedHashMap<>();
        for (ForwardRule r : all) {
            latest.putIfAbsent(r.employee.id, r);
        }
        return new ArrayList<>(latest.values());
    }

    /** 当前生效中的最新版本(同步时使用) */
    public ForwardRule latestActive(Employee employee) {
        var rows = em.createQuery(
                        "from ForwardRule where employee = :e and status = :s order by version desc", ForwardRule.class)
                .setParameter("e", employee).setParameter("s", RuleStatus.ACTIVE)
                .setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 主管申请转发授权。若已有旧版本(无论生效或待审批),旧版本立即停用,
     * 新申请回到待审批——接收人/范围/截止日期的任何变更都必须重新获批。
     */
    @Transactional
    public ForwardRule request(Employee employee, String recipientEmail, RuleScope scope, String scopeKeywords,
                               Instant startTime, Instant endDate, String requestedBy) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new BadRequestException("接收人邮箱必填");
        }
        recipientEmail = recipientEmail.trim();
        if (recipientEmail.equalsIgnoreCase(employee.email)) {
            throw new BadRequestException("接收人不能是离职员工本人");
        }
        if (startTime == null || endDate == null) {
            throw new BadRequestException("生效时间与截止日期必填");
        }
        if (!endDate.isAfter(startTime)) {
            throw new BadRequestException("截止日期必须晚于生效时间");
        }
        if (scope == RuleScope.SUBJECT_KEYWORDS && (scopeKeywords == null || scopeKeywords.isBlank())) {
            throw new BadRequestException("按关键词转发时必须填写关键词");
        }
        int nextVersion = 1;
        for (ForwardRule old : history(employee.id)) {
            nextVersion = Math.max(nextVersion, old.version + 1);
            if (old.status == RuleStatus.ACTIVE || old.status == RuleStatus.PENDING_APPROVAL) {
                close(old, RuleStatus.DISABLED, "SUPERSEDED", requestedBy,
                        "被新申请 v" + nextVersion + " 取代,需重新审批");
            }
        }
        ForwardRule rule = new ForwardRule();
        rule.employee = employee;
        rule.version = nextVersion;
        rule.recipientEmail = recipientEmail;
        rule.scope = scope == null ? RuleScope.ALL_BUSINESS : scope;
        rule.scopeKeywords = scopeKeywords;
        rule.startTime = startTime;
        rule.endDate = endDate;
        rule.requestedBy = requestedBy;
        em.persist(rule);
        for (ForwardRule old : history(employee.id)) {
            if (old.supersededById == null && old.status == RuleStatus.DISABLED
                    && "SUPERSEDED".equals(old.closeReason) && old.id.longValue() != rule.id.longValue()
                    && old.version == rule.version - 1) {
                old.supersededById = rule.id;
            }
        }
        audit.log("CONFIG", "RULE_REQUEST", requestedBy,
                "员工 " + employee.email + " 转发规则 v" + rule.version + " 申请: 接收人=" + recipientEmail
                        + " 范围=" + rule.scopeDescription() + " 生效=" + startTime + " 截止=" + endDate);
        return rule;
    }

    /** 审批通过 → 生效。审批时重新校验接收人资格与截止日期。 */
    @Transactional
    public ForwardRule approve(Long id, String approver) {
        ForwardRule rule = get(id);
        if (rule.status != RuleStatus.PENDING_APPROVAL) {
            throw new BadRequestException("仅待审批规则可审批,当前状态: " + rule.status);
        }
        if (!rule.endDate.isAfter(Instant.now())) {
            throw new BadRequestException("截止日期已过,无法批准,请重新申请");
        }
        var recipients = em.createQuery("from Employee where email = :e", Employee.class)
                .setParameter("e", rule.recipientEmail).getResultList();
        if (!recipients.isEmpty() && recipients.get(0).status != com.example.exitmail.model.EmployeeStatus.ACTIVE) {
            throw new BadRequestException("接收人当前不是在职状态,无法批准");
        }
        rule.status = RuleStatus.ACTIVE;
        rule.approvedBy = approver;
        rule.approvedAt = Instant.now();
        audit.log("APPROVAL", "RULE_APPROVE", approver,
                "员工 " + rule.employee.email + " 转发规则 v" + rule.version + " 批准生效: 接收人=" + rule.recipientEmail
                        + " 范围=" + rule.scopeDescription() + " 生效=" + rule.startTime + " 截止=" + rule.endDate);
        return rule;
    }

    /** 撤销审批/授权,立即生效,终态不可恢复。 */
    @Transactional
    public ForwardRule revoke(Long id, String actor) {
        ForwardRule rule = get(id);
        if (rule.status != RuleStatus.ACTIVE && rule.status != RuleStatus.PENDING_APPROVAL) {
            throw new BadRequestException("规则已处于终态: " + rule.status);
        }
        close(rule, RuleStatus.REVOKED, "REVOKED", actor, "审批撤销");
        cancelPendingTasks(rule, actor, "授权撤销");
        return rule;
    }

    /** 手动停用,终态。 */
    @Transactional
    public ForwardRule disable(Long id, String actor, String reason) {
        ForwardRule rule = get(id);
        if (rule.status != RuleStatus.ACTIVE && rule.status != RuleStatus.PENDING_APPROVAL) {
            throw new BadRequestException("规则已处于终态: " + rule.status);
        }
        close(rule, RuleStatus.DISABLED, "MANUAL", actor, reason == null ? "手动停用" : reason);
        cancelPendingTasks(rule, actor, "规则停用");
        return rule;
    }

    /** 接收人调岗:其作为接收人的所有生效/待审批规则立即停用。 */
    @Transactional
    public int onRecipientTransferred(Employee recipient, String actor) {
        List<ForwardRule> rows = em.createQuery(
                        "from ForwardRule where recipientEmail = :email and status in (:a, :p)", ForwardRule.class)
                .setParameter("email", recipient.email)
                .setParameter("a", RuleStatus.ACTIVE)
                .setParameter("p", RuleStatus.PENDING_APPROVAL)
                .getResultList();
        for (ForwardRule rule : rows) {
            close(rule, RuleStatus.DISABLED, "RECIPIENT_TRANSFERRED", actor,
                    "接收人 " + recipient.email + " 已调岗");
            cancelPendingTasks(rule, actor, "接收人调岗");
        }
        return rows.size();
    }

    /** 调度器:截止日期已过 → 到期失效。 */
    @Transactional
    public int expireOverdue(Instant now) {
        List<ForwardRule> rows = em.createQuery(
                        "from ForwardRule where status = :s and endDate < :now", ForwardRule.class)
                .setParameter("s", RuleStatus.ACTIVE).setParameter("now", now).getResultList();
        for (ForwardRule rule : rows) {
            close(rule, RuleStatus.EXPIRED, "EXPIRED", "scheduler", "到达截止日期 " + rule.endDate);
            cancelPendingTasks(rule, "scheduler", "授权到期");
        }
        return rows.size();
    }

    private void close(ForwardRule rule, RuleStatus target, String reason, String actor, String note) {
        rule.status = target;
        rule.closedAt = Instant.now();
        rule.closeReason = reason;
        String action = switch (reason) {
            case "REVOKED" -> "RULE_REVOKE";
            case "EXPIRED" -> "RULE_EXPIRED";
            case "RECIPIENT_TRANSFERRED" -> "RULE_RECIPIENT_TRANSFERRED";
            case "SUPERSEDED" -> "RULE_SUPERSEDED";
            default -> "RULE_DISABLE";
        };
        audit.log("CONFIG", action, actor,
                "员工 " + rule.employee.email + " 转发规则 v" + rule.version + " " + target + ": " + note
                        + "; 接收人=" + rule.recipientEmail + " 范围=" + rule.scopeDescription()
                        + " 生效=" + rule.startTime + " 截止=" + rule.endDate + " 停用时间=" + rule.closedAt);
    }

    /** 规则进入终态后,其未完成的同步任务一并取消,防止旧任务误转发。 */
    private void cancelPendingTasks(ForwardRule rule, String actor, String why) {
        var tasks = em.createQuery(
                        "from SyncTask where ruleId = :rid and status in (:p, :f)", com.example.exitmail.model.SyncTask.class)
                .setParameter("rid", rule.id)
                .setParameter("p", TaskStatus.PENDING)
                .setParameter("f", TaskStatus.FAILED)
                .getResultList();
        for (var t : tasks) {
            t.status = TaskStatus.CANCELLED;
            t.lastError = why + ",任务取消";
            audit.log("SYNC", "TASK_CANCELLED", actor,
                    "规则 v" + rule.version + "(id=" + rule.id + ") " + why + ",同步任务 #" + t.id + " 取消");
        }
    }
}
