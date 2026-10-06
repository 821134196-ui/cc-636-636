package com.corp.offboarding.model;

/**
 * 主管申请时选择的邮件范围。
 * ALL_BUSINESS     全部业务邮件（仍强制过滤私人/敏感）
 * SENDER_ALLOWLIST 仅指定外部发件人
 * SUBJECT_KEYWORDS 仅主题含指定关键词
 */
public enum MailScope {
    ALL_BUSINESS,
    SENDER_ALLOWLIST,
    SUBJECT_KEYWORDS
}
