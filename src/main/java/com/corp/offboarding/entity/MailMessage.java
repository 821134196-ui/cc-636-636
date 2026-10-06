package com.corp.offboarding.entity;

import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 模拟邮箱中的一封邮件。 */
@Entity
@Table(name = "mail_message",
        indexes = {
                @Index(name = "idx_mail_owner", columnList = "owner_id"),
                @Index(name = "idx_mail_received", columnList = "received_at")
        })
public class MailMessage extends BaseEntity {

    /** 邮箱归属人（通常是离职员工）。 */
    @Column(name = "owner_id", nullable = false)
    public Long ownerId;

    @Column(name = "from_email", nullable = false)
    public String fromEmail;

    @Column(name = "from_name")
    public String fromName;

    @Column(nullable = false, length = 500)
    public String subject;

    @Column(nullable = false, length = 5000)
    public String body;

    @Column(name = "received_at", nullable = false)
    public long receivedAt;

    /** 自动回复是否已发出（避免对同一封邮件重复回复）。 */
    @Column(name = "reply_sent", nullable = false)
    public boolean replySent;

    /** 人工/规则打上的标记：私人邮件，任何情况下不转发。 */
    @Column(name = "is_private", nullable = false)
    public boolean privateFlag;

    /** 人工/规则打上的标记：敏感主题，任何情况下不转发。 */
    @Column(name = "is_sensitive", nullable = false)
    public boolean sensitiveFlag;
}
