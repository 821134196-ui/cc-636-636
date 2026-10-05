package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 自动回复回执。dedupKey = 员工id:邮件id 唯一,
 * 同一发件邮件只回复一次,配置改版不会导致对历史邮件重复回复。
 */
@Entity
@Table(name = "auto_reply_receipt", uniqueConstraints = @UniqueConstraint(columnNames = "dedupKey"))
public class AutoReplyReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String dedupKey;

    @Column(nullable = false)
    public Long employeeId;

    /** 实际使用的配置版本 */
    @Column(nullable = false)
    public Long configId;

    @Column(nullable = false)
    public Long mailMessageId;

    @Column(nullable = false)
    public String toAddress;

    @Column(nullable = false)
    public Instant sentAt = Instant.now();

    public AutoReplyReceipt() {
    }

    public AutoReplyReceipt(Long employeeId, Long configId, Long mailMessageId, String toAddress) {
        this.dedupKey = employeeId + ":" + mailMessageId;
        this.employeeId = employeeId;
        this.configId = configId;
        this.mailMessageId = mailMessageId;
        this.toAddress = toAddress;
    }
}
