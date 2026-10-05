package com.example.exitmail.service;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.RuleStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;

/**
 * 转发授权的唯一判定入口。同步任务、全量扫描、重试都必须经过这里,
 * 以数据库中的实时状态为准,不允许缓存授权结论。
 */
@ApplicationScoped
public class AuthorizationService {

    @Inject
    EntityManager em;

    /**
     * @return null 表示当前授权有效;否则返回拒绝原因(中文,直接进审计)。
     */
    public String checkForwardingAuthorized(ForwardRule rule, Instant now) {
        if (rule == null) {
            return "转发规则不存在";
        }
        if (rule.status != RuleStatus.ACTIVE) {
            return "规则未处于生效状态(当前: " + rule.status + ")";
        }
        if (now.isBefore(rule.startTime)) {
            return "未到生效时间 " + rule.startTime;
        }
        if (now.isAfter(rule.endDate)) {
            return "已过截止日期 " + rule.endDate;
        }
        // 接收人若为内部员工,调岗/离职立即失去接收资格
        var rows = em.createQuery("from Employee where email = :e", Employee.class)
                .setParameter("e", rule.recipientEmail).getResultList();
        if (!rows.isEmpty()) {
            EmployeeStatus rs = rows.get(0).status;
            if (rs == EmployeeStatus.TRANSFERRED) {
                return "接收人已调岗,授权终止";
            }
            if (rs == EmployeeStatus.DEPARTED) {
                return "接收人已离职,授权终止";
            }
        }
        return null;
    }
}
