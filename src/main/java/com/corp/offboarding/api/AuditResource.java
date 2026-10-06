package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.AuditLog;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 审计记录查询：配置变更 + 同步结果。 */
@Path("/api/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    Views views;

    @GET
    public List<Map<String, Object>> list(@QueryParam("entityType") String entityType,
                                          @QueryParam("entityId") Long entityId,
                                          @QueryParam("action") String action,
                                          @QueryParam("limit") @DefaultValue("200") int limit) {
        StringBuilder q = new StringBuilder("1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (entityType != null && !entityType.isBlank()) {
            q.append(" and entityType = ?").append(params.size() + 1);
            params.add(entityType);
        }
        if (entityId != null) {
            q.append(" and entityId = ?").append(params.size() + 1);
            params.add(entityId);
        }
        if (action != null && !action.isBlank()) {
            q.append(" and action = ?").append(params.size() + 1);
            params.add(action);
        }
        q.append(" order by id desc");
        return AuditLog.find(q.toString(), params.toArray())
                .page(0, Math.min(limit, 1000))
                .<AuditLog>list().stream().map(views::audit).toList();
    }
}
