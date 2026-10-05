package com.example.exitmail.rest;

import com.example.exitmail.model.AutoReplyConfig;
import com.example.exitmail.model.Employee;
import com.example.exitmail.service.AutoReplyService;
import com.example.exitmail.service.EmployeeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

@Path("/api/auto-replies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AutoReplyResource {

    @Inject
    AutoReplyService autoReplyService;

    @Inject
    EmployeeService employeeService;

    /** 每个员工的最新版本 */
    @GET
    public List<AutoReplyConfig> latest() {
        return autoReplyService.latestPerEmployee();
    }

    /** 某员工的全部历史版本 */
    @GET
    @Path("/history/{employeeId}")
    public List<AutoReplyConfig> history(@PathParam("employeeId") Long employeeId) {
        return autoReplyService.history(employeeId);
    }

    /** 人事新建/修改(生成新版本) */
    @POST
    public AutoReplyConfig upsert(UpsertReq req) {
        Employee employee = requireEmployee(req.employeeEmail());
        return autoReplyService.upsert(employee, req.content(), parse(req.startTime()),
                parse(req.endTime()), req.createdBy() == null ? "人事" : req.createdBy());
    }

    @POST
    @Path("/{id}/disable")
    public AutoReplyConfig disable(@PathParam("id") Long id, ActorReq req) {
        return autoReplyService.disable(id, req == null ? "admin" : req.actor());
    }

    private Employee requireEmployee(String email) {
        Employee e = employeeService.findByEmail(email);
        if (e == null) {
            throw new NotFoundException("员工不存在: " + email);
        }
        return e;
    }

    static Instant parse(String s) {
        return (s == null || s.isBlank()) ? null : Instant.parse(s);
    }

    public record UpsertReq(String employeeEmail, String content, String startTime, String endTime,
                            String createdBy) {
    }

    public record ActorReq(String actor) {
    }
}
