package com.corp.offboarding.service;

import com.corp.offboarding.entity.SimClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 模拟业务时钟：所有生效/到期/重试时间判断都以此为准，
 * 不使用系统墙钟，从而可以确定性地验证"到期停用"。
 * 全表固定一行 id=1，用原生 upsert 维护，避免手工赋 ID 的游离实体问题。
 */
@ApplicationScoped
public class TimeService {

    /** 演示起点：2026-10-05 09:00 本地时间。 */
    public static final long BASE_EPOCH =
            java.time.LocalDateTime.of(2026, 10, 5, 9, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

    @Inject
    EntityManager em;

    @Transactional
    public long now() {
        SimClock clock = SimClock.findById((long) 1);
        if (clock == null) {
            em.createNativeQuery("insert or ignore into sim_clock (id, now_epoch) values (1, ?1)")
                    .setParameter(1, BASE_EPOCH)
                    .executeUpdate();
            clock = SimClock.findById((long) 1);
        }
        return clock.nowEpoch;
    }

    @Transactional
    public long setTime(long epoch) {
        now(); // 保证行存在
        int updated = em.createNativeQuery("update sim_clock set now_epoch = ?1 where id = 1")
                .setParameter(1, epoch)
                .executeUpdate();
        if (updated == 0) {
            em.createNativeQuery("insert into sim_clock (id, now_epoch) values (1, ?1)")
                    .setParameter(1, epoch)
                    .executeUpdate();
        }
        return epoch;
    }

    /** 把模拟时钟向前推进若干分钟并返回新的当前时间。 */
    @Transactional
    public long advanceMinutes(long minutes) {
        return setTime(now() + minutes * 60_000L);
    }
}
