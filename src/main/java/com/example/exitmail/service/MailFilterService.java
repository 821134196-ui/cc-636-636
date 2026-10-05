package com.example.exitmail.service;

import com.example.exitmail.model.MailMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * 敏感内容过滤:私人邮件标记、敏感主题关键词命中即禁止转发。
 * 该过滤独立于转发授权——即使规则生效,命中过滤也一律不放行。
 */
@ApplicationScoped
public class MailFilterService {

    @ConfigProperty(name = "exitmail.sensitive-keywords")
    List<String> sensitiveKeywords;

    /**
     * @return null 表示可转发;否则返回拦截原因。
     */
    public String blockReason(MailMessage msg) {
        if (msg.privateFlag) {
            return "私人邮件(发件人标记)";
        }
        String subject = msg.subject == null ? "" : msg.subject.toLowerCase();
        for (String kw : sensitiveKeywords) {
            String k = kw.trim().toLowerCase();
            if (!k.isEmpty() && subject.contains(k)) {
                return "敏感主题关键词: " + kw.trim();
            }
        }
        return null;
    }
}
