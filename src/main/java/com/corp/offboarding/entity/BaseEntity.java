package com.corp.offboarding.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 所有实体的主键基类。
 *
 * 刻意使用 IDENTITY（SQLite INTEGER PRIMARY KEY AUTOINCREMENT）而非 PanacheEntity
 * 默认的 SEQUENCE/TABLE 生成器：后者在 JTA 下会挂起当前事务并新开连接更新
 * *_SEQ 表，与 SQLite"同一时刻仅一个写连接"的模型冲突（SQLITE_BUSY_SNAPSHOT）。
 * IDENTITY 在插入的同一连接上取回自增 rowid，无额外连接、无序列表。
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
