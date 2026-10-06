package com.corp.offboarding.api;

import com.corp.offboarding.bootstrap.DataSeeder;
import com.corp.offboarding.entity.*;
import com.corp.offboarding.service.FailureInjector;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/** 仅 dev/test 开放的管理操作：重置演示数据、注入邮件服务故障。 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    FailureInjector failureInjector;

    @Inject
    DataSeeder seeder;

    @Inject
    Provider<AdminResource> self;

    @ConfigProperty(name = "app.allow-reset", defaultValue = "false")
    boolean allowReset;

    @POST
    @Path("/fail-next")
    public Map<String, Object> failNext(JsonNode body) {
        int n = body.path("count").asInt(1);
        failureInjector.failNext(n);
        return Map.of("failuresScheduled", n);
    }

    @GET
    @Path("/failure-status")
    public Map<String, Object> failureStatus() {
        return Map.of("failuresRemaining", failureInjector.remaining());
    }

    /** 清空全部业务数据并重新播种（含故障注入计数清零）。 */
    @POST
    @Path("/reset")
    public Map<String, Object> reset() {
        if (!allowReset) {
            throw new ForbiddenException("生产环境禁止重置数据");
        }
        self.get().deleteAll();
        failureInjector.failNext(0);
        seeder.seedOnce();
        return Map.of("reset", true);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void deleteAll() {
        ForwardOutcome.deleteAll();
        SyncTask.deleteAll();
        MailboxOutbox.deleteAll();
        ForwardingVersion.deleteAll();
        ForwardingRule.deleteAll();
        AutoReplyVersion.deleteAll();
        AutoReplyRule.deleteAll();
        MailMessage.deleteAll();
        AuditLog.deleteAll();
        Employee.deleteAll();
        SimClock.deleteAll();
    }
}
