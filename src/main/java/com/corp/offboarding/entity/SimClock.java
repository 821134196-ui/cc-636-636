package com.corp.offboarding.entity;

import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * 模拟时钟：全表唯一一行（id=1）。
 * 业务时间一律以此为准，便于验证"到期停用"等时间驱动行为。
 */
@Entity
@Table(name = "sim_clock")
public class SimClock extends BaseEntity {

    @Column(name = "now_epoch", nullable = false)
    public long nowEpoch;
}
