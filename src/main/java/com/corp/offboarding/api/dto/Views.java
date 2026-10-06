package com.corp.offboarding.api.dto;

import com.corp.offboarding.entity.*;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;

/** 把实体拼成前端需要的扁平视图（带员工/接收人显示名）。 */
@ApplicationScoped
public class Views {

    public Map<String, Object> employee(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("name", e.name);
        m.put("email", e.email);
        m.put("department", e.department);
        m.put("status", e.status.name());
        m.put("departureAt", e.departureAt);
        return m;
    }

    public Map<String, Object> reply(AutoReplyRule r) {
        Employee e = Employee.findById(r.employeeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("employeeId", r.employeeId);
        m.put("employeeName", e == null ? null : e.name);
        m.put("employeeEmail", e == null ? null : e.email);
        m.put("body", r.body);
        m.put("effectiveAt", r.effectiveAt);
        m.put("status", r.status.name());
        m.put("version", r.currentVersion);
        m.put("confirmedBy", r.confirmedBy);
        m.put("activatedAt", r.activatedAt);
        m.put("disabledAt", r.disabledAt);
        m.put("createdAt", r.createdAt);
        m.put("updatedAt", r.updatedAt);
        return m;
    }

    public Map<String, Object> replyVersion(AutoReplyVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id);
        m.put("version", v.version);
        m.put("body", v.body);
        m.put("effectiveAt", v.effectiveAt);
        m.put("status", v.status.name());
        m.put("changeType", v.changeType);
        m.put("changedBy", v.changedBy);
        m.put("createdAt", v.createdAt);
        m.put("note", v.note);
        return m;
    }

    public Map<String, Object> forwarding(ForwardingRule r) {
        Employee owner = Employee.findById(r.employeeId);
        Employee rcpt = Employee.findById(r.recipientId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("employeeId", r.employeeId);
        m.put("employeeName", owner == null ? null : owner.name);
        m.put("employeeEmail", owner == null ? null : owner.email);
        m.put("recipientId", r.recipientId);
        m.put("recipientName", rcpt == null ? null : rcpt.name);
        m.put("recipientEmail", r.recipientEmailSnapshot);
        m.put("recipientStatus", rcpt == null ? "MISSING" : rcpt.status.name());
        m.put("scopeType", r.scopeType);
        m.put("scopeCriteria", r.scopeCriteria);
        m.put("status", r.status.name());
        m.put("stopReason", r.stopReason == null ? null : r.stopReason.name());
        m.put("requestedBy", r.requestedBy);
        m.put("approvedBy", r.approvedBy);
        m.put("validFrom", r.validFrom);
        m.put("validUntil", r.validUntil);
        m.put("version", r.currentVersion);
        m.put("activatedAt", r.activatedAt);
        m.put("stoppedAt", r.stoppedAt);
        m.put("createdAt", r.createdAt);
        m.put("updatedAt", r.updatedAt);
        return m;
    }

    public Map<String, Object> forwardingVersion(ForwardingVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id);
        m.put("version", v.version);
        m.put("recipientId", v.recipientId);
        m.put("recipientEmail", v.recipientEmail);
        m.put("scopeType", v.scopeType);
        m.put("scopeCriteria", v.scopeCriteria);
        m.put("status", v.status.name());
        m.put("stopReason", v.stopReason == null ? null : v.stopReason.name());
        m.put("changeType", v.changeType);
        m.put("changedBy", v.changedBy);
        m.put("validFrom", v.validFrom);
        m.put("validUntil", v.validUntil);
        m.put("createdAt", v.createdAt);
        m.put("note", v.note);
        return m;
    }

    public Map<String, Object> mail(MailMessage mail) {
        Employee owner = Employee.findById(mail.ownerId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mail.id);
        m.put("ownerId", mail.ownerId);
        m.put("ownerEmail", owner == null ? null : owner.email);
        m.put("fromEmail", mail.fromEmail);
        m.put("fromName", mail.fromName);
        m.put("subject", mail.subject);
        m.put("body", mail.body);
        m.put("receivedAt", mail.receivedAt);
        m.put("replySent", mail.replySent);
        m.put("privateFlag", mail.privateFlag);
        m.put("sensitiveFlag", mail.sensitiveFlag);
        return m;
    }

    public Map<String, Object> task(SyncTask t) {
        ForwardingRule r = ForwardingRule.findById(t.ruleId);
        MailMessage m = MailMessage.findById(t.mailId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.id);
        map.put("mailId", t.mailId);
        map.put("mailSubject", m == null ? null : m.subject);
        map.put("ruleId", t.ruleId);
        map.put("recipientEmail", r == null ? null : r.recipientEmailSnapshot);
        map.put("ruleStatusNow", r == null ? "DELETED" : r.status.name());
        map.put("ruleVersionSnapshot", t.ruleVersionSnapshot);
        map.put("ruleVersionNow", r == null ? null : r.currentVersion);
        map.put("status", t.status.name());
        map.put("attempts", t.attempts);
        map.put("maxAttempts", t.maxAttempts);
        map.put("nextAttemptAt", t.nextAttemptAt);
        map.put("lastError", t.lastError);
        map.put("receiptId", t.receiptId);
        map.put("acknowledged", t.acknowledged);
        map.put("createdAt", t.createdAt);
        map.put("updatedAt", t.updatedAt);
        return map;
    }

    public Map<String, Object> outcome(ForwardOutcome o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id);
        m.put("mailId", o.mailId);
        m.put("ruleId", o.ruleId);
        m.put("recipientEmail", o.recipientEmail);
        m.put("decision", o.decision.name());
        m.put("receiptId", o.receiptId);
        m.put("attemptedAt", o.attemptedAt);
        m.put("ruleStatusSnapshot", o.ruleStatusSnapshot);
        return m;
    }

    public Map<String, Object> outbox(MailboxOutbox o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id);
        m.put("type", o.type);
        m.put("targetEmail", o.targetEmail);
        m.put("sourceMailId", o.sourceMailId);
        m.put("ruleId", o.ruleId);
        m.put("subject", o.subject);
        m.put("content", o.content);
        m.put("sentAt", o.sentAt);
        return m;
    }

    public Map<String, Object> audit(AuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("actor", a.actor);
        m.put("action", a.action);
        m.put("entityType", a.entityType);
        m.put("entityId", a.entityId);
        m.put("detail", a.detail);
        m.put("createdAt", a.createdAt);
        return m;
    }
}
