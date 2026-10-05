package com.example.exitmail.rest;

import com.example.exitmail.model.AuditLog;
import com.example.exitmail.service.AuditService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    AuditService auditService;

    @GET
    public List<AuditLog> list(@QueryParam("category") String category,
                               @QueryParam("limit") @DefaultValue("200") int limit) {
        return auditService.list(category, limit);
    }
}
