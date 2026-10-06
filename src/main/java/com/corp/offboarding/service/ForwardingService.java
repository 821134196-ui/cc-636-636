package com.corp.offboarding.service;

import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.entity.ForwardingRule;
import com.corp.offboarding.entity.ForwardingVersion;
import com.corp.offboarding.model.EmployeeStatus;
import com.corp.offboarding.model.ForwardStatus;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.model.StopReason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

/**
 * 转发授权生命周期（与自动回复完全分离，绝不授予邮箱登录权）：
 * 主管申请（接收人 + 邮件范围 + 截止日期）→ 审批通过 → 到点生效
 * → 到期 / 提前撤销 / 审批驳回 / 接收人调岗 立即停用。
 * 停用是终态：不可恢复，只能重新提交申请。
 */
@ApplicationScoped
public class ForwardingService {

    @Inject
    TimeService timeService;

    @Inject
    AuditService audit;

    /** 主管提交转发申请。 */
    @Transactional
    public ForwardingRule request(Long employeeId, Long recipientId, MailScope scope,
                                  String scopeCriteria, long validFrom, long validUntil,
                                  String managerActor) {
        Employee owner = Employee.findById(employeeId);
        if (owner == null || owner.status != EmployeeStatus.OFFBOARDED) {
            throw new BadRequestException("只能为已确认离职的员工申请转发");
        }
        Employee recipient = Employee.findById(recipientId);
        if (recipient == null) {
            throw new BadRequestException("接收人不存在");
        }
        if (recipient.id.equals(owner.id)) {
            throw new BadRequestException("接收人不能是离职员工本人");
        }
        if (recipient.status != EmployeeStatus.ACTIVE) {
            throw new BadRequestException("接收人当前不是在职状态，不能作为转发接收人");
        }
        if (scope == null) {
            throw new BadRequestException("必须指定邮件范围");
        }
        String criteria = normalizeCriteria(scope, scopeCriteria);
        if (validUntil <= validFrom) {
            throw new BadRequestException("截止日期必须晚于生效时间");
        }

        long now = timeService.now();
        ForwardingRule rule = new ForwardingRule();
        rule.employeeId = employeeId;
        rule.recipientId = recipientId;
        rule.recipientEmailSnapshot = recipient.email;
        rule.scopeType = scope.name();
        rule.scopeCriteria = criteria;
        rule.status = ForwardStatus.PENDING_APPROVAL;
        rule.requestedBy = managerActor;
        rule.validFrom = validFrom;
        rule.validUntil = validUntil;
        rule.currentVersion = 1;
        rule.createdAt = now;
        rule.updatedAt = now;
        rule.persist();
        snapshot(rule, "REQUESTED", managerActor, "主管提交转发申请，等待审批");
        audit.record(managerActor, "FORWARD_REQUESTED", "FORWARDING_RULE", rule.id,
                audit.detail("employee", owner.email, "recipient", recipient.email,
                        "scope", scope.name(), "scopeCriteria", criteria,
                        "validFrom", validFrom, "validUntil", validUntil,
                        "status", rule.status.name()));
        return rule;
    }

    /** 审批通过。 */
    @Transactional
    public ForwardingRule approve(Long ruleId, String approver) {
        ForwardingRule rule = mustGet(ruleId);
        if (rule.status != ForwardStatus.PENDING_APPROVAL) {
            throw new BadRequestException("仅待审批申请可以通过，当前状态: " + rule.status);
        }
        Employee recipient = Employee.findById(rule.recipientId);
        if (recipient == null || recipient.status != EmployeeStatus.ACTIVE) {
            throw new BadRequestException("审批时接收人已不在职，驳回申请");
        }
        long now = timeService.now();
        rule.approvedBy = approver;
        rule.updatedAt = now;
        rule.status = now >= rule.validFrom ? ForwardStatus.ACTIVE : ForwardStatus.SCHEDULED;
        if (rule.status == ForwardStatus.ACTIVE) {
            rule.activatedAt = now;
        }
        snapshot(rule, "APPROVED", approver,
                now >= rule.validFrom ? "审批通过，即时生效" : "审批通过，待生效");
        if (rule.status == ForwardStatus.ACTIVE) {
            snapshot(rule, "ACTIVATED", "SYSTEM", "审批时已到生效时间");
        }
        audit.record(approver, "FORWARD_APPROVED", "FORWARDING_RULE", rule.id,
                audit.detail("recipient", rule.recipientEmailSnapshot,
                        "scope", rule.scopeType, "validFrom", rule.validFrom,
                        "validUntil", rule.validUntil, "status", rule.status.name()));
        return rule;
    }

    /** 审批驳回（终态）。 */
    @Transactional
    public ForwardingRule deny(Long ruleId, String approver, String reason) {
        ForwardingRule rule = mustGet(ruleId);
        if (rule.status != ForwardStatus.PENDING_APPROVAL) {
            throw new BadRequestException("仅待审批申请可以驳回，当前状态: " + rule.status);
        }
        stop(rule, ForwardStatus.STOPPED, StopReason.APPROVAL_DENIED, approver,
                "审批驳回: " + reason, true);
        return rule;
    }

    /** 提前撤销：审批人/管理员随时停用，立即生效且不可恢复。 */
    @Transactional
    public ForwardingRule revoke(Long ruleId, String actor, String reason) {
        ForwardingRule rule = mustGet(ruleId);
        if (rule.status == ForwardStatus.STOPPED) {
            throw new BadRequestException("规则已停用，无需重复撤销");
        }
        stop(rule, ForwardStatus.STOPPED, StopReason.REVOKED, actor,
                "提前撤销授权: " + reason, false);
        return rule;
    }

    /**
     * 接收人调岗联动：以该员工为接收人的所有未停用规则立即停用。
     * 返回受影响规则数。
     */
    @Transactional
    public int stopRulesForReassignedRecipient(Long recipientId, String actor) {
        List<ForwardingRule> active = ForwardingRule.list("recipientId = ?1 and status <> ?2",
                recipientId, ForwardStatus.STOPPED);
        int n = 0;
        for (ForwardingRule rule : active) {
            stop(rule, ForwardStatus.STOPPED, StopReason.RECIPIENT_REASSIGNED, actor,
                    "接收人调岗，转发立即停用", false);
            n++;
        }
        return n;
    }

    /** 周期维护：到点生效 + 到期停用（幂等）。 */
    @Transactional
    public void maintainSchedule() {
        long now = timeService.now();
        for (ForwardingRule rule : ForwardingRule.<ForwardingRule>list(
                "status", ForwardStatus.SCHEDULED)) {
            if (now >= rule.validFrom && now <= rule.validUntil) {
                rule.status = ForwardStatus.ACTIVE;
                rule.activatedAt = now;
                rule.updatedAt = now;
                snapshot(rule, "ACTIVATED", "SYSTEM", "到达生效时间");
                audit.record("SYSTEM", "FORWARD_ACTIVATED", "FORWARDING_RULE", rule.id,
                        audit.detail("recipient", rule.recipientEmailSnapshot,
                                "activatedAt", now));
            }
        }
        for (ForwardingRule rule : ForwardingRule.<ForwardingRule>list(
                "status", ForwardStatus.ACTIVE)) {
            if (now > rule.validUntil) {
                stop(rule, ForwardStatus.STOPPED, StopReason.EXPIRED, "SYSTEM",
                        "授权到期，自动停用", false);
            }
        }
    }

    public List<ForwardingVersion> versions(Long ruleId) {
        return ForwardingVersion.find("ruleId = ?1 order by version asc, createdAt asc, id asc",
                ruleId).list();
    }

    private ForwardingRule mustGet(Long id) {
        ForwardingRule rule = ForwardingRule.findById(id);
        if (rule == null) {
            throw new NotFoundException("转发规则不存在: " + id);
        }
        return rule;
    }

    private void stop(ForwardingRule rule, ForwardStatus status, StopReason reason,
                      String actor, String note, boolean deniedFromRequest) {
        long now = timeService.now();
        rule.status = status;
        rule.stopReason = reason;
        rule.stoppedAt = now;
        rule.updatedAt = now;
        snapshot(rule, deniedFromRequest ? "DENIED" : "STOPPED", actor, note);
        String action = switch (reason) {
            case APPROVAL_DENIED -> "FORWARD_DENIED";
            case REVOKED -> "FORWARD_REVOKED";
            case EXPIRED -> "FORWARD_EXPIRED";
            case RECIPIENT_REASSIGNED -> "FORWARD_STOPPED_RECIPIENT_REASSIGNED";
        };
        audit.record(actor, action, "FORWARDING_RULE", rule.id,
                audit.detail("recipient", rule.recipientEmailSnapshot,
                        "scope", rule.scopeType, "scopeCriteria", rule.scopeCriteria,
                        "validFrom", rule.validFrom, "validUntil", rule.validUntil,
                        "stopReason", reason.name(), "stoppedAt", now,
                        "note", note));
    }

    private String normalizeCriteria(MailScope scope, String criteria) {
        if (scope == MailScope.ALL_BUSINESS) {
            return null;
        }
        if (criteria == null || criteria.isBlank()) {
            throw new BadRequestException(scope == MailScope.SENDER_ALLOWLIST
                    ? "发件人范围至少填写一个外部邮箱"
                    : "主题范围至少填写一个关键词");
        }
        String normalized = criteria.trim().replaceAll("\\s*[;；,，]\\s*", ";")
                .replaceAll(";+$", "");
        if (scope == MailScope.SENDER_ALLOWLIST) {
            for (String addr : normalized.split(";")) {
                if (!addr.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    throw new BadRequestException("发件人地址格式不正确: " + addr);
                }
            }
        }
        return normalized;
    }

    private void snapshot(ForwardingRule rule, String changeType, String by, String note) {
        ForwardingVersion v = new ForwardingVersion();
        v.ruleId = rule.id;
        v.version = rule.currentVersion;
        v.recipientId = rule.recipientId;
        v.recipientEmail = rule.recipientEmailSnapshot;
        v.scopeType = rule.scopeType;
        v.scopeCriteria = rule.scopeCriteria;
        v.status = rule.status;
        v.stopReason = rule.stopReason;
        v.changeType = changeType;
        v.changedBy = by;
        v.validFrom = rule.validFrom;
        v.validUntil = rule.validUntil;
        v.createdAt = timeService.now();
        v.note = note;
        v.persist();
    }
}
