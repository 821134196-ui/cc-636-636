package com.corp.offboarding.service;

import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.model.EmployeeStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class EmployeeService {

    @Inject
    AuditService audit;

    @Inject
    TimeService time;

    @Inject
    ForwardingService forwardingService;

    public List<Employee> list() {
        return Employee.listAll();
    }

    @Transactional
    public Employee create(String name, String email, String department) {
        if (Employee.findByEmail(email) != null) {
            throw new IllegalArgumentException("邮箱已存在: " + email);
        }
        Employee e = new Employee();
        e.name = name;
        e.email = email;
        e.department = department;
        e.status = EmployeeStatus.ACTIVE;
        e.persist();
        audit.record("ADMIN", "EMPLOYEE_CREATED", "EMPLOYEE", e.id,
                audit.detail("name", name, "email", email, "department", department));
        return e;
    }

    /**
     * 人事确认离职：登记离职时间。自动回复由 HR 随后确认内容并据此排程；
     * 不会授予任何人该邮箱的登录权。
     */
    @Transactional
    public Employee markOffboarding(Long employeeId, long departureAt, String actor) {
        Employee e = mustGet(employeeId);
        EmployeeStatus old = e.status;
        e.status = EmployeeStatus.OFFBOARDED;
        e.departureAt = departureAt;
        audit.record(actor, "OFFBOARDING_CONFIRMED", "EMPLOYEE", e.id,
                audit.detail("employee", e.email, "previousStatus", old.name(),
                        "departureAt", departureAt));
        return e;
    }

    /**
     * 接收人调岗：立即停止指向该接收人的所有生效中/待生效转发。
     */
    @Transactional
    public Employee reassign(Long employeeId, String actor) {
        Employee e = mustGet(employeeId);
        e.status = EmployeeStatus.REASSIGNED;
        audit.record(actor, "RECIPIENT_REASSIGNED", "EMPLOYEE", e.id,
                audit.detail("recipient", e.email,
                        "effect", "以该员工为接收人的有效转发规则立即停用"));
        // 同事务联动：立即停用所有指向该接收人的转发规则
        forwardingService.stopRulesForReassignedRecipient(e.id, actor);
        return e;
    }

    private Employee mustGet(Long id) {
        Employee e = Employee.findById(id);
        if (e == null) {
            throw new NotFoundException("员工不存在: " + id);
        }
        return e;
    }
}
