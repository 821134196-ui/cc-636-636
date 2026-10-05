package com.example.exitmail.rest;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import com.example.exitmail.service.EmployeeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    EmployeeService employeeService;

    @GET
    public List<Employee> list() {
        return employeeService.list();
    }

    @POST
    public Employee create(CreateReq req) {
        return employeeService.create(req.email(), req.name(), req.department());
    }

    @POST
    @Path("/{id}/status")
    public Employee setStatus(@PathParam("id") Long id, StatusReq req) {
        return employeeService.setStatus(id, EmployeeStatus.valueOf(req.status()), req.actor());
    }

    public record CreateReq(String email, String name, String department) {
    }

    public record StatusReq(String status, String actor) {
    }
}
