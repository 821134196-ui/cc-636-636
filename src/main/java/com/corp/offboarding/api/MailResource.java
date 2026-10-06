package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.MailMessage;
import com.corp.offboarding.entity.MailboxOutbox;
import com.corp.offboarding.service.MailIntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 本地模拟邮箱：外部来信投递 + 发件箱查看。 */
@Path("/api/mail")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MailResource {

    @Inject
    MailIntakeService intake;

    @Inject
    Views views;

    /** 模拟外部联系人向离职员工邮箱发信。 */
    @POST
    @Path("/employees/{ownerId}/inbound")
    public Map<String, Object> inbound(@PathParam("ownerId") Long ownerId, JsonNode body) {
        MailMessage mail = intake.receive(ownerId,
                body.path("fromEmail").asText(),
                body.path("fromName").asText(null),
                body.path("subject").asText(),
                body.path("body").asText(""),
                body.path("markPrivate").asBoolean(false),
                body.path("markSensitive").asBoolean(false));
        return views.mail(mail);
    }

    /** 收件箱（模拟邮箱中该员工收到的信）。 */
    @GET
    @Path("/employees/{ownerId}/inbox")
    public List<Map<String, Object>> inbox(@PathParam("ownerId") Long ownerId) {
        return MailMessage.<MailMessage>find("ownerId = ?1 order by receivedAt desc, id desc",
                        ownerId)
                .list().stream().map(views::mail).toList();
    }

    /** 模拟邮箱发件箱：自动回复 + 转发副本。 */
    @GET
    @Path("/outbox")
    public List<Map<String, Object>> outbox() {
        return MailboxOutbox.<MailboxOutbox>find("order by sentAt desc, id desc").list()
                .stream().map(views::outbox).toList();
    }
}
