package com.example.exitmail.rest;

import com.example.exitmail.model.MailMessage;
import com.example.exitmail.service.MailboxService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/mailbox")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MailboxResource {

    @Inject
    MailboxService mailboxService;

    @GET
    @Path("/inbox")
    public List<MailMessage> inbox(@QueryParam("address") String address) {
        return mailboxService.inbox(address);
    }

    @GET
    @Path("/outbox")
    public List<MailMessage> outbox() {
        return mailboxService.outbox();
    }

    /** 模拟外部来信 */
    @POST
    @Path("/receive")
    public MailMessage receive(ReceiveReq req) {
        return mailboxService.receive(req.from(), req.to(), req.subject(), req.body(),
                Boolean.TRUE.equals(req.privateFlag()));
    }

    public record ReceiveReq(String from, String to, String subject, String body, Boolean privateFlag) {
    }
}
