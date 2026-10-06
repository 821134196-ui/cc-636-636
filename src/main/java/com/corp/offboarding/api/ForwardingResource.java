package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.ForwardingRule;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.service.ForwardingService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/forwarding-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ForwardingResource {

    @Inject
    ForwardingService service;

    @Inject
    Views views;

    /** 管理员页面：转发配置列表（含当前生效状态、停用原因）。 */
    @GET
    public List<Map<String, Object>> list() {
        return ForwardingRule.<ForwardingRule>list("order by id asc").stream()
                .map(views::forwarding).toList();
    }

    @GET
    @Path("/{id}/versions")
    public List<Map<String, Object>> versions(@PathParam("id") Long id) {
        return service.versions(id).stream().map(views::forwardingVersion).toList();
    }

    /** 主管提交申请：接收人、邮件范围、截止日期。 */
    @POST
    @Path("/employees/{employeeId}/request")
    public Map<String, Object> request(@PathParam("employeeId") Long employeeId,
                                       JsonNode body) {
        ForwardingRule rule = service.request(
                employeeId,
                body.path("recipientId").asLong(),
                MailScope.valueOf(body.path("scopeType").asText()),
                body.path("scopeCriteria").asText(null),
                body.path("validFrom").asLong(),
                body.path("validUntil").asLong(),
                body.path("actor").asText("主管"));
        return views.forwarding(rule);
    }

    @POST
    @Path("/{id}/approve")
    public Map<String, Object> approve(@PathParam("id") Long id, JsonNode body) {
        return views.forwarding(service.approve(id,
                body != null && body.has("actor") ? body.get("actor").asText() : "审批人"));
    }

    @POST
    @Path("/{id}/deny")
    public Map<String, Object> deny(@PathParam("id") Long id, JsonNode body) {
        return views.forwarding(service.deny(id,
                body != null && body.has("actor") ? body.get("actor").asText() : "审批人",
                body != null && body.has("reason") ? body.get("reason").asText() : ""));
    }

    /** 提前撤销（立即停用，终态）。 */
    @POST
    @Path("/{id}/revoke")
    public Map<String, Object> revoke(@PathParam("id") Long id, JsonNode body) {
        return views.forwarding(service.revoke(id,
                body != null && body.has("actor") ? body.get("actor").asText() : "管理员",
                body != null && body.has("reason") ? body.get("reason").asText() : ""));
    }
}
