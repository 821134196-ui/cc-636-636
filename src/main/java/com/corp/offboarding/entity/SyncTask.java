package com.corp.offboarding.entity;

import com.corp.offboarding.model.TaskStatus;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * 邮件转发同步任务：一封邮件 × 一条规则对应一个任务。
 * 失败后由调度器重试；重试前必须重新核对当前授权，
 * 若规则已停用/到期/接收人调岗，则标记 SKIPPED_NO_AUTH，绝不重新打开规则。
 */
@Entity
@Table(name = "sync_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_mail_rule", columnNames = {"mail_id", "rule_id"}),
        indexes = {
                @Index(name = "idx_task_status", columnList = "status"),
                @Index(name = "idx_task_next", columnList = "next_attempt_at")
        })
public class SyncTask extends BaseEntity {

    @Column(name = "mail_id", nullable = false)
    public Long mailId;

    @Column(name = "rule_id", nullable = false)
    public Long ruleId;

    /** 任务创建时的规则版本；旧任务重试时不能用旧快照恢复授权。 */
    @Column(name = "rule_version_snapshot", nullable = false)
    public int ruleVersionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false)
    public int attempts;

    @Column(name = "max_attempts", nullable = false)
    public int maxAttempts = 5;

    @Column(name = "next_attempt_at", nullable = false)
    public long nextAttemptAt;

    @Column(name = "last_error", length = 500)
    public String lastError;

    @Column(name = "receipt_id", length = 64)
    public String receiptId;

    /** 外部回执是否已确认；重复回执只做幂等 no-op，不改变规则与任务结论。 */
    @Column(name = "acknowledged", nullable = false)
    public boolean acknowledged;

    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(name = "updated_at", nullable = false)
    public long updatedAt;
}
