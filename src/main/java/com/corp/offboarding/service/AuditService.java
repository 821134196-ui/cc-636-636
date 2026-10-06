package com.corp.offboarding.service;

import com.corp.offboarding.entity.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 审计记录：配置变更与同步结果统一出口，detail 以有序 JSON 落库。 */
@ApplicationScoped
public class AuditService {

    @Inject
    ObjectMapper mapper;

    @Inject
    TimeService timeService;

    @Transactional
    public void record(String actor, String action, String entityType, Long entityId,
                       Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        log.actor = actor;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        try {
            log.detail = mapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (JsonProcessingException e) {
            log.detail = "{}";
        }
        log.createdAt = timeService.now();
        log.persist();
    }

    public Map<String, Object> detail(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
