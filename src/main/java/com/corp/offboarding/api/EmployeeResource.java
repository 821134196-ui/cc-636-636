package com.corp.offboarding.api;

import com.corp.offboarding.api.dto.Views;
import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.service.EmployeeService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    EmployeeService service;

    @Inject
    Views views;

    @GET
    public List<Map<String, Object>> list() {
        return service.list().stream().map(views::employee).toList();
    }

    @POST
    public Map<String, Object> create(JsonNode body) {
        Employee e = service.create(
                body.path("name").asText(),
                body.path("email").asText(),
                body.path("department").asText(null));
        return views.employee(e);
    }

    /** 人事确认离职：提供 departureAt（epoch 毫秒）。 */
    @POST
    @Path("/{id}/offboarding")
    public Map<String, Object> markOffboarding(@PathParam("id") Long id, JsonNode body) {
        long departure = body.path("departureAt").asLong();
        String actor = body.path("actor").asText("HR");
        return views.employee(service.markOffboarding(id, departure, actor));
    }

    /** 接收人调岗：立即停用其所有转发。 */
    @POST
    @Path("/{id}/reassign")
    public Map<String, Object> reassign(@PathParam("id") Long id, JsonNode body) {
        String actor = body != null && body.has("actor") ? body.get("actor").asText() : "ADMIN";
        return views.employee(service.reassign(id, actor));
    }
}
