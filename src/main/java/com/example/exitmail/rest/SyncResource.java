package com.example.exitmail.rest;

import com.example.exitmail.model.AutoReplyReceipt;
import com.example.exitmail.model.ForwardReceipt;
import com.example.exitmail.model.SyncTask;
import com.example.exitmail.service.AutoReplyService;
import com.example.exitmail.service.ForwardRuleService;
import com.example.exitmail.service.SyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/api/sync")
@Produces(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject
    SyncService syncService;

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    AutoReplyService autoReplyService;

    /** 立即执行一次全量同步 + 到期检查 + 任务重试 */
    @POST
    @Path("/run")
    public Map<String, Object> run() {
        Instant now = Instant.now();
        int expiredRules = forwardRuleService.expireOverdue(now);
        int expiredConfigs = autoReplyService.expireOverdue(now);
        syncService.runFullSync();
        return Map.of("expiredRules", expiredRules, "expiredAutoReplies", expiredConfigs);
    }

    @GET
    @Path("/tasks")
    public List<SyncTask> tasks() {
        return syncService.listTasks();
    }

    @POST
    @Path("/tasks/{id}/retry")
    public SyncTask retry(@PathParam("id") Long id) {
        return syncService.retryTask(id);
    }

    @GET
    @Path("/receipts/forward")
    public List<ForwardReceipt> forwardReceipts() {
        return syncService.forwardReceipts();
    }

    @GET
    @Path("/receipts/auto-reply")
    public List<AutoReplyReceipt> autoReplyReceipts() {
        return syncService.autoReplyReceipts();
    }
}
