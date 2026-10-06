package com.corp.offboarding.entity;

import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 本地模拟邮箱服务的发件箱：自动回复与转发副本都投递到这里。 */
@Entity
@Table(name = "mailbox_outbox",
        indexes = @Index(name = "idx_outbox_mail", columnList = "source_mail_id"))
public class MailboxOutbox extends BaseEntity {

    /** FORWARD / AUTO_REPLY */
    @Column(nullable = false, length = 16)
    public String type;

    @Column(name = "target_email", nullable = false)
    public String targetEmail;

    @Column(name = "source_mail_id", nullable = false)
    public Long sourceMailId;

    @Column(name = "rule_id")
    public Long ruleId;

    @Column(nullable = false, length = 500)
    public String subject;

    @Column(nullable = false, length = 5000)
    public String content;

    @Column(name = "sent_at", nullable = false)
    public long sentAt;
}
