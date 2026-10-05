package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/** 本地模拟邮箱中的邮件(收件与发件共用一张表) */
@Entity
@Table(name = "mail_message")
public class MailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 模拟 RFC Message-ID,全局唯一 */
    @Column(nullable = false, unique = true)
    public String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MailKind kind = MailKind.NORMAL;

    @Column(nullable = false)
    public String fromAddress;

    @Column(nullable = false)
    public String toAddress;

    @Column(length = 500)
    public String subject;

    @Column(length = 8000)
    public String body;

    /** 发件人标记的私人邮件,一律不得转发 */
    @Column(nullable = false)
    public boolean privateFlag = false;

    @Column(nullable = false)
    public Instant receivedAt = Instant.now();

    /** 出站邮件关联的原始入站邮件 id */
    public Long relatedMessageId;

    public MailMessage() {
    }

    public MailMessage(String messageId, Direction direction, MailKind kind,
                       String from, String to, String subject, String body, boolean privateFlag) {
        this.messageId = messageId;
        this.direction = direction;
        this.kind = kind;
        this.fromAddress = from;
        this.toAddress = to;
        this.subject = subject;
        this.body = body;
        this.privateFlag = privateFlag;
    }
}
