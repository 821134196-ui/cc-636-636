package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 自动回复配置(按员工版本化)。
 * 每次人事修改内容/时间都会生成新版本,旧版本立即停用(SUPERSEDED),历史版本保留可审计。
 */
@Entity
@Table(name = "auto_reply_config")
public class AutoReplyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    public Employee employee;

    @Column(nullable = false)
    public int version;

    @Column(nullable = false, length = 4000)
    public String content;

    /** 离职生效起点 */
    @Column(nullable = false)
    public Instant startTime;

    /** 可选:自动回复截止时间点,到期自动停用 */
    public Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ConfigStatus status = ConfigStatus.ACTIVE;

    /** 创建人(人事) */
    @Column(nullable = false)
    public String createdBy;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant disabledAt;

    /** SUPERSEDED(被新版本取代)/ MANUAL(手动停用)/ END_REACHED(到期自动停用) */
    public String disableReason;

    public boolean isEffectiveAt(Instant now) {
        if (status != ConfigStatus.ACTIVE) {
            return false;
        }
        if (now.isBefore(startTime)) {
            return false;
        }
        return endTime == null || !now.isAfter(endTime);
    }
}
