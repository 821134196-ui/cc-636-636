package com.example.exitmail.service;

import com.example.exitmail.model.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class AuditService {

    @Inject
    EntityManager em;

    @Transactional
    public void log(String category, String action, String actor, String details) {
        em.persist(new AuditLog(category, action, actor == null ? "system" : actor, details));
    }

    public List<AuditLog> list(String category, int limit) {
        String q = "from AuditLog " + (category == null || category.isBlank() ? "" : "where category = :cat ")
                + "order by timestamp desc, id desc";
        var query = em.createQuery(q, AuditLog.class);
        if (category != null && !category.isBlank()) {
            query.setParameter("cat", category);
        }
        return query.setMaxResults(Math.min(limit, 500)).getResultList();
    }
}
