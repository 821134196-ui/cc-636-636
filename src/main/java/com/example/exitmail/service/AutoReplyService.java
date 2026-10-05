package com.example.exitmail.service;

import com.example.exitmail.model.AutoReplyConfig;
import com.example.exitmail.model.ConfigStatus;
import com.example.exitmail.model.Employee;
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
 * 自动回复配置:人事维护,版本化管理。
 * 每次修改生成新版本并立即取代旧版本,历史版本保留可查。
 */
@ApplicationScoped
public class AutoReplyService {

    @Inject
    EntityManager em;

    @Inject
    AuditService audit;

    public List<AutoReplyConfig> history(Long employeeId) {
        return em.createQuery("from AutoReplyConfig where employee.id = :id order by version desc",
                        AutoReplyConfig.class)
                .setParameter("id", employeeId).getResultList();
    }

    /** 每个员工的最新版本(管理页面列表) */
    public List<AutoReplyConfig> latestPerEmployee() {
        List<AutoReplyConfig> all = em.createQuery(
                "from AutoReplyConfig order by employee.id, version desc", AutoReplyConfig.class).getResultList();
        Map<Long, AutoReplyConfig> latest = new LinkedHashMap<>();
        for (AutoReplyConfig c : all) {
            latest.putIfAbsent(c.employee.id, c);
        }
        return new ArrayList<>(latest.values());
    }

    public AutoReplyConfig activeConfig(Employee employee, Instant now) {
        var rows = em.createQuery(
                        "from AutoReplyConfig where employee = :e and status = :s order by version desc",
                        AutoReplyConfig.class)
                .setParameter("e", employee).setParameter("s", ConfigStatus.ACTIVE)
                .setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        AutoReplyConfig cfg = rows.get(0);
        return cfg.isEffectiveAt(now) ? cfg : null;
    }

    /** 人事新建/修改自动回复:生成新版本,旧版本立即停用。 */
    @Transactional
    public AutoReplyConfig upsert(Employee employee, String content, Instant startTime, Instant endTime,
                                  String createdBy) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("自动回复内容不能为空");
        }
        if (startTime == null) {
            throw new BadRequestException("生效时间必填");
        }
        if (endTime != null && endTime.isBefore(startTime)) {
            throw new BadRequestException("截止时间不能早于生效时间");
        }
        int nextVersion = 1;
        for (AutoReplyConfig old : history(employee.id)) {
            nextVersion = Math.max(nextVersion, old.version + 1);
            if (old.status == ConfigStatus.ACTIVE) {
                old.status = ConfigStatus.DISABLED;
                old.disabledAt = Instant.now();
                old.disableReason = "SUPERSEDED";
                audit.log("CONFIG", "AUTO_REPLY_SUPERSEDED", createdBy,
                        "员工 " + employee.email + " 自动回复 v" + old.version + " 被新版本取代");
            }
        }
        AutoReplyConfig cfg = new AutoReplyConfig();
        cfg.employee = employee;
        cfg.version = nextVersion;
        cfg.content = content;
        cfg.startTime = startTime;
        cfg.endTime = endTime;
        cfg.createdBy = createdBy;
        em.persist(cfg);
        audit.log("CONFIG", "AUTO_REPLY_UPSERT", createdBy,
                "员工 " + employee.email + " 自动回复 v" + cfg.version
                        + " 生效时间 " + startTime + (endTime != null ? " 截止 " + endTime : " 长期"));
        return cfg;
    }

    @Transactional
    public AutoReplyConfig disable(Long id, String actor) {
        AutoReplyConfig cfg = em.find(AutoReplyConfig.class, id);
        if (cfg == null) {
            throw new NotFoundException("配置不存在: " + id);
        }
        if (cfg.status == ConfigStatus.ACTIVE) {
            cfg.status = ConfigStatus.DISABLED;
            cfg.disabledAt = Instant.now();
            cfg.disableReason = "MANUAL";
            audit.log("CONFIG", "AUTO_REPLY_DISABLE", actor,
                    "员工 " + cfg.employee.email + " 自动回复 v" + cfg.version + " 手动停用");
        }
        return cfg;
    }

    /** 调度器:截止时间点已过 → 自动停用。 */
    @Transactional
    public int expireOverdue(Instant now) {
        List<AutoReplyConfig> rows = em.createQuery(
                        "from AutoReplyConfig where status = :s and endTime is not null and endTime < :now",
                        AutoReplyConfig.class)
                .setParameter("s", ConfigStatus.ACTIVE).setParameter("now", now).getResultList();
        for (AutoReplyConfig cfg : rows) {
            cfg.status = ConfigStatus.DISABLED;
            cfg.disabledAt = now;
            cfg.disableReason = "END_REACHED";
            audit.log("CONFIG", "AUTO_REPLY_EXPIRED", "scheduler",
                    "员工 " + cfg.employee.email + " 自动回复 v" + cfg.version + " 到达截止时间,自动停用");
        }
        return rows.size();
    }
}
