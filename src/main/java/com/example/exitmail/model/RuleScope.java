package com.example.exitmail.model;

/** 转发邮件范围 */
public enum RuleScope {
    /** 全部业务邮件(仍受敏感/私人过滤约束) */
    ALL_BUSINESS,
    /** 仅主题包含指定关键词的邮件 */
    SUBJECT_KEYWORDS
}
