package com.example.exitmail.rest;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.ForwardRule;
import com.example.exitmail.model.RuleScope;
import com.example.exitmail.service.EmployeeService;
import com.example.exitmail.service.ForwardRuleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

@Path("/api/forward-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ForwardRuleResource {

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    EmployeeService employeeService;

    /** 每个员工邮箱的最新一版规则 */
    @GET
    public List<ForwardRule> latest() {
        return forwardRuleService.latestPerEmployee();
    }

    /** 某员工邮箱的全部历史版本 */
    @GET
    @Path("/history/{employeeId}")
    public List<ForwardRule> history(@PathParam("employeeId") Long employeeId) {
        return forwardRuleService.history(employeeId);
    }

    /** 主管申请转发授权(生成新版本,待审批) */
    @POST
    public ForwardRule request(RuleReq req) {
        Employee employee = employeeService.findByEmail(req.employeeEmail());
        if (employee == null) {
            throw new NotFoundException("员工不存在: " + req.employeeEmail());
        }
        return forwardRuleService.request(employee, req.recipientEmail(),
                req.scope() == null ? RuleScope.ALL_BUSINESS : RuleScope.valueOf(req.scope()),
                req.scopeKeywords(), parse(req.startTime()), parse(req.endDate()),
                req.requestedBy() == null ? "主管" : req.requestedBy());
    }

    @POST
    @Path("/{id}/approve")
    public ForwardRule approve(@PathParam("id") Long id, ActorReq req) {
        return forwardRuleService.approve(id, req == null ? "审批人" : req.actor());
    }

    @POST
    @Path("/{id}/revoke")
    public ForwardRule revoke(@PathParam("id") Long id, ActorReq req) {
        return forwardRuleService.revoke(id, req == null ? "审批人" : req.actor());
    }

    @POST
    @Path("/{id}/disable")
    public ForwardRule disable(@PathParam("id") Long id, DisableReq req) {
        return forwardRuleService.disable(id, req == null ? "admin" : req.actor(),
                req == null ? null : req.reason());
    }

    static Instant parse(String s) {
        return (s == null || s.isBlank()) ? null : Instant.parse(s);
    }

    public record RuleReq(String employeeEmail, String recipientEmail, String scope, String scopeKeywords,
                          String startTime, String endDate, String requestedBy) {
    }

    public record ActorReq(String actor) {
    }

    public record DisableReq(String actor, String reason) {
    }
}
