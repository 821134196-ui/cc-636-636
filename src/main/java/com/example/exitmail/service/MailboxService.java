package com.example.exitmail.service;

import com.example.exitmail.model.Direction;
import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.MailKind;
import com.example.exitmail.model.MailMessage;
import com.example.exitmail.model.SyncTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/** 本地模拟邮箱服务:接收外部来信、记录出站邮件(自动回复/转发副本)。 */
@ApplicationScoped
public class MailboxService {

    @Inject
    EntityManager em;

    @Inject
    EmployeeService employeeService;

    @Inject
    ForwardRuleService forwardRuleService;

    public List<MailMessage> inbox(String address) {
        if (address == null || address.isBlank()) {
            return em.createQuery("from MailMessage where direction = :d order by receivedAt desc, id desc",
                            MailMessage.class)
                    .setParameter("d", Direction.INBOUND).setMaxResults(200).getResultList();
        }
        return em.createQuery(
                        "from MailMessage where direction = :d and toAddress = :a order by receivedAt desc, id desc",
                        MailMessage.class)
                .setParameter("d", Direction.INBOUND).setParameter("a", address)
                .setMaxResults(200).getResultList();
    }

    public List<MailMessage> outbox() {
        return em.createQuery("from MailMessage where direction = :d order by receivedAt desc, id desc",
                        MailMessage.class)
                .setParameter("d", Direction.OUTBOUND).setMaxResults(200).getResultList();
    }

    public List<MailMessage> inboundFor(String address) {
        return em.createQuery(
                        "from MailMessage where direction = :d and kind = :k and toAddress = :a order by id",
                        MailMessage.class)
                .setParameter("d", Direction.INBOUND).setParameter("k", MailKind.NORMAL)
                .setParameter("a", address).getResultList();
    }

    /**
     * 模拟外部来信投递。若收件人是已离职员工,为其生成一条同步任务,
     * 由调度器/手动触发执行(执行时实时核对授权)。
     */
    @Transactional
    public MailMessage receive(String from, String to, String subject, String body, boolean privateFlag) {
        MailMessage msg = new MailMessage(UUID.randomUUID().toString(), Direction.INBOUND, MailKind.NORMAL,
                from, to, subject, body, privateFlag);
        em.persist(msg);
        Employee owner = employeeService.findByEmail(to);
        if (owner != null && owner.status == EmployeeStatus.DEPARTED) {
            ForwardRule active = forwardRuleService.latestActive(owner);
            em.persist(new SyncTask(active == null ? null : active.id, msg.id, to));
        }
        return msg;
    }

    /** 出站邮件(自动回复或转发副本),写入发件箱。 */
    @Transactional
    public MailMessage sendOutbound(MailKind kind, String from, String to, String subject, String body,
                                    Long relatedMessageId) {
        MailMessage out = new MailMessage(UUID.randomUUID().toString(), Direction.OUTBOUND, kind,
                from, to, subject, body, false);
        out.relatedMessageId = relatedMessageId;
        em.persist(out);
        return out;
    }
}
