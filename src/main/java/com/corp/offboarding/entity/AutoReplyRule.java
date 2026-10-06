package com.corp.offboarding.entity;

import com.corp.offboarding.model.ReplyStatus;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 自动回复配置（一个离职员工一条当前规则，历史在 {@link AutoReplyVersion}）。 */
@Entity
@Table(name = "auto_reply_rule")
public class AutoReplyRule extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    public Long employeeId;

    /** 自动回复正文（人事确认）。 */
    @Column(nullable = false, length = 2000)
    public String body;

    /** 生效时间 = 人事确认的离职时间。 */
    @Column(name = "effective_at", nullable = false)
    public long effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ReplyStatus status = ReplyStatus.SCHEDULED;

    @Column(name = "current_version", nullable = false)
    public int currentVersion = 1;

    @Column(name = "confirmed_by")
    public String confirmedBy;

    @Column(name = "activated_at")
    public Long activatedAt;

    @Column(name = "disabled_at")
    public Long disabledAt;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(name = "updated_at", nullable = false)
    public long updatedAt;

    public static AutoReplyRule findByEmployee(Long employeeId) {
        return find("employeeId", employeeId).firstResult();
    }
}
