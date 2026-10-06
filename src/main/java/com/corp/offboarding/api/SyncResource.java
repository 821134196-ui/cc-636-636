package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.ForwardOutcome;
import com.corp.offboarding.entity.SyncTask;
import com.corp.offboarding.service.AutoReplyService;
import com.corp.offboarding.service.ForwardingService;
import com.corp.offboarding.service.SyncService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject
    SyncService sync;

    @Inject
    ForwardingService forwarding;

    @Inject
    AutoReplyService replies;

    @Inject
    Views views;

    /** 同步任务列表（管理员核对每次转发执行情况）。 */
    @GET
    @Path("/tasks")
    public List<Map<String, Object>> tasks(@QueryParam("status") String status) {
        var query = status == null || status.isBlank()
                ? SyncTask.find("order by id asc")
                : SyncTask.find("status = ?1 order by id asc",
                        com.corp.offboarding.model.TaskStatus.valueOf(status));
        return query.<SyncTask>list().stream().map(views::task).toList();
    }

    /** 处置结果列表（FORWARDED / BLOCKED_* / SKIPPED_INACTIVE）。 */
    @GET
    @Path("/outcomes")
    public List<Map<String, Object>> outcomes() {
        return ForwardOutcome.<ForwardOutcome>find("order by id desc").list().stream()
                .map(views::outcome).toList();
    }

    /** 手动触发一次排程维护 + 失败重试（模拟调度周期）。 */
    @POST
    @Path("/drain")
    public Map<String, Object> drain() {
        int n = sync.drain(forwarding, replies);
        return Map.of("processedTasks", n);
    }

    /** 立即重试某个（可能很旧的）失败任务；重试仍以当前授权为准。 */
    @POST
    @Path("/tasks/{id}/retry")
    public Map<String, Object> retry(@PathParam("id") Long id) {
        SyncService.ProcessReport report = sync.retryTask(id);
        return Map.of("report", report.name());
    }

    /** 模拟邮件服务回执（可重复发送验证幂等）。 */
    @POST
    @Path("/receipts/{receiptId}/ack")
    public Map<String, Object> ack(@PathParam("receiptId") String receiptId, JsonNode body) {
        String remote = body != null && body.has("remoteService")
                ? body.get("remoteService").asText() : "MAIL-GATEWAY";
        SyncService.ReceiptAck ack = sync.acknowledgeReceipt(receiptId, remote);
        return Map.of("first", ack.first(), "result", ack.result());
    }
}
