package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 邮件同步任务。失败可重试;重试时必须重新核对当前授权,
 * 授权已失效则任务取消,绝不重新打开已停用规则。
 */
@Entity
@Table(name = "sync_task")
public class SyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 创建任务时命中的转发规则(仅作溯源,执行时以实时授权为准) */
    public Long ruleId;

    @Column(nullable = false)
    public Long mailMessageId;

    /** 目标邮箱(离职员工邮箱) */
    @Column(nullable = false)
    public String mailbox;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false)
    public int attempts = 0;

    @Column(length = 1000)
    public String lastError;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant lastAttemptAt;

    public SyncTask() {
    }

    public SyncTask(Long ruleId, Long mailMessageId, String mailbox) {
        this.ruleId = ruleId;
        this.mailMessageId = mailMessageId;
        this.mailbox = mailbox;
    }
}
