package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.AutoReplyRule;
import com.corp.offboarding.service.AutoReplyService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/auto-replies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AutoReplyResource {

    @Inject
    AutoReplyService service;

    @Inject
    Views views;

    /** 管理员页面：自动回复配置列表（含当前生效状态）。 */
    @GET
    public List<Map<String, Object>> list() {
        return AutoReplyRule.<AutoReplyRule>list("order by id asc").stream()
                .map(views::reply).toList();
    }

    @GET
    @Path("/{id}/versions")
    public List<Map<String, Object>> versions(@PathParam("id") Long id) {
        return service.versions(id).stream().map(views::replyVersion).toList();
    }

    /** HR 确认/修改自动回复内容与生效时间。 */
    @POST
    @Path("/employees/{employeeId}/confirm")
    public Map<String, Object> confirm(@PathParam("employeeId") Long employeeId,
                                       JsonNode body) {
        Long effectiveAt = body.hasNonNull("effectiveAt")
                ? body.get("effectiveAt").asLong() : null;
        return views.reply(service.confirm(employeeId,
                body.path("body").asText(), effectiveAt,
                body.path("actor").asText("HR")));
    }

    @POST
    @Path("/{id}/disable")
    public Map<String, Object> disable(@PathParam("id") Long id, JsonNode body) {
        return views.reply(service.disable(id,
                body != null && body.has("actor") ? body.get("actor").asText() : "HR"));
    }

    /** 手动触发到点生效（演示/测试；生产由调度器周期执行）。 */
    @POST
    @Path("/maintain")
    public Map<String, Object> maintain() {
        return Map.of("activated", service.activateDue());
    }
}
