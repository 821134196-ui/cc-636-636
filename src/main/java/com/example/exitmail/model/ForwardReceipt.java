package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 转发回执。dedupKey = 规则id:邮件id 唯一,
 * 保证同一规则下同一封邮件只会产生一次处理结果(转发或被拦截),
 * 重复同步/重复回执不会重复转发,也不会让失效授权复活。
 */
@Entity
@Table(name = "forward_receipt", uniqueConstraints = @UniqueConstraint(columnNames = "dedupKey"))
public class ForwardReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String dedupKey;

    @Column(nullable = false)
    public Long ruleId;

    @Column(nullable = false)
    public Long mailMessageId;

    @Column(nullable = false)
    public String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ReceiptResult result;

    @Column(nullable = false)
    public Instant processedAt = Instant.now();

    public ForwardReceipt() {
    }

    public ForwardReceipt(Long ruleId, Long mailMessageId, String recipientEmail, ReceiptResult result) {
        this.dedupKey = ruleId + ":" + mailMessageId;
        this.ruleId = ruleId;
        this.mailMessageId = mailMessageId;
        this.recipientEmail = recipientEmail;
        this.result = result;
    }
}
