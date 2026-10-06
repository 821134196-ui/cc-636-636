package com.corp.offboarding.service;

import com.corp.offboarding.entity.AutoReplyRule;
import com.corp.offboarding.entity.AutoReplyVersion;
import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.model.EmployeeStatus;
import com.corp.offboarding.model.ReplyStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

/**
 * 自动回复生命周期：人事确认离职时间与回复内容 → 按离职时间排程 → 到点生效 → 可停用。
 * 每次内容修改与状态变化都写历史版本。
 */
@ApplicationScoped
public class AutoReplyService {

    @Inject
    TimeService timeService;

    @Inject
    AuditService audit;

    /** HR 确认（首次创建或修改内容）。生效时间取离职时间。 */
    @Transactional
    public AutoReplyRule confirm(Long employeeId, String body, Long effectiveAt, String hrActor) {
        Employee employee = Employee.findById(employeeId);
        if (employee == null) {
            throw new NotFoundException("员工不存在: " + employeeId);
        }
        if (employee.status != EmployeeStatus.OFFBOARDED) {
            throw new BadRequestException("人事尚未确认该员工离职，不能配置自动回复");
        }
        if (body == null || body.isBlank()) {
            throw new BadRequestException("自动回复内容不能为空");
        }
        long eff = effectiveAt != null ? effectiveAt : employee.departureAt;
        if (eff <= 0) {
            throw new BadRequestException("缺少离职时间，无法排程自动回复");
        }

        long now = timeService.now();
        AutoReplyRule rule = AutoReplyRule.findByEmployee(employeeId);
        if (rule == null) {
            rule = new AutoReplyRule();
            rule.employeeId = employeeId;
            rule.body = body;
            rule.effectiveAt = eff;
            rule.status = ReplyStatus.SCHEDULED;
            rule.currentVersion = 1;
            rule.confirmedBy = hrActor;
            rule.createdAt = now;
            rule.updatedAt = now;
            rule.persist();
            snapshot(rule, "CREATED", hrActor, "人事确认离职并设置自动回复");
            audit.record(hrActor, "REPLY_CONFIRMED", "AUTO_REPLY_RULE", rule.id,
                    audit.detail("employee", employee.email, "version", 1,
                            "effectiveAt", eff));
        } else {
            if (rule.status == ReplyStatus.DISABLED) {
                throw new BadRequestException("该自动回复已停用，如需恢复请重新创建配置");
            }
            rule.currentVersion += 1;
            rule.body = body;
            rule.effectiveAt = eff;
            rule.confirmedBy = hrActor;
            rule.updatedAt = now;
            if (rule.status == ReplyStatus.ACTIVE && eff > now) {
                rule.status = ReplyStatus.SCHEDULED;
            }
            snapshot(rule, "UPDATED", hrActor, "人事更新自动回复内容/生效时间");
            audit.record(hrActor, "REPLY_UPDATED", "AUTO_REPLY_RULE", rule.id,
                    audit.detail("employee", employee.email,
                            "version", rule.currentVersion, "effectiveAt", eff));
        }
        // 若离职时间已到（补录等），立即转生效
        activateDue();
        return rule;
    }

    /** 手动停用（不删除，保留历史）。 */
    @Transactional
    public AutoReplyRule disable(Long ruleId, String actor) {
        AutoReplyRule rule = mustGet(ruleId);
        if (rule.status == ReplyStatus.DISABLED) {
            return rule;
        }
        rule.status = ReplyStatus.DISABLED;
        rule.disabledAt = timeService.now();
        rule.updatedAt = rule.disabledAt;
        snapshot(rule, "DISABLED", actor, "手动停用自动回复");
        audit.record(actor, "REPLY_DISABLED", "AUTO_REPLY_RULE", rule.id,
                audit.detail("version", rule.currentVersion, "disabledAt", rule.disabledAt));
        return rule;
    }

    /** 到点生效（调度器周期调用，幂等）。 */
    @Transactional
    public int activateDue() {
        long now = timeService.now();
        int n = 0;
        for (AutoReplyRule rule : AutoReplyRule
                .<AutoReplyRule>list("status", ReplyStatus.SCHEDULED)) {
            if (now >= rule.effectiveAt) {
                rule.status = ReplyStatus.ACTIVE;
                rule.activatedAt = now;
                rule.updatedAt = now;
                snapshot(rule, "ACTIVATED", "SYSTEM", "到达离职时间，自动回复生效");
                Employee e = Employee.findById(rule.employeeId);
                audit.record("SYSTEM", "REPLY_ACTIVATED", "AUTO_REPLY_RULE", rule.id,
                        audit.detail("employee", e == null ? null : e.email,
                                "activatedAt", now));
                n++;
            }
        }
        return n;
    }

    public List<AutoReplyVersion> versions(Long ruleId) {
        return AutoReplyVersion.find("ruleId order by version asc, createdAt asc, id asc")
                .list();
    }

    private AutoReplyRule mustGet(Long id) {
        AutoReplyRule rule = AutoReplyRule.findById(id);
        if (rule == null) {
            throw new NotFoundException("自动回复规则不存在: " + id);
        }
        return rule;
    }

    private void snapshot(AutoReplyRule rule, String changeType, String by, String note) {
        AutoReplyVersion v = new AutoReplyVersion();
        v.ruleId = rule.id;
        v.version = rule.currentVersion;
        v.body = rule.body;
        v.effectiveAt = rule.effectiveAt;
        v.status = rule.status;
        v.changeType = changeType;
        v.changedBy = by;
        v.createdAt = timeService.now();
        v.note = note;
        v.persist();
    }
}
