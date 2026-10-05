package com.example.exitmail.service;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class EmployeeService {

    @Inject
    EntityManager em;

    @Inject
    ForwardRuleService forwardRuleService;

    @Inject
    AuditService audit;

    public List<Employee> list() {
        return em.createQuery("from Employee order by id", Employee.class).getResultList();
    }

    public Employee findByEmail(String email) {
        var rows = em.createQuery("from Employee where email = :e", Employee.class)
                .setParameter("e", email).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Employee get(Long id) {
        Employee e = em.find(Employee.class, id);
        if (e == null) {
            throw new NotFoundException("员工不存在: " + id);
        }
        return e;
    }

    @Transactional
    public Employee create(String email, String name, String department) {
        if (email == null || email.isBlank() || name == null || name.isBlank()) {
            throw new BadRequestException("邮箱和姓名必填");
        }
        if (findByEmail(email) != null) {
            throw new BadRequestException("邮箱已存在: " + email);
        }
        Employee e = new Employee(email.trim(), name.trim(), department);
        e.statusChangedAt = Instant.now();
        em.persist(e);
        audit.log("EMPLOYEE", "EMPLOYEE_CREATE", "admin", "新增员工 " + name + " <" + email + ">");
        return e;
    }

    /**
     * 状态变更。调岗(TRANSFERRED)会立即停用该员工作为接收人的所有转发授权。
     */
    @Transactional
    public Employee setStatus(Long id, EmployeeStatus status, String actor) {
        Employee e = get(id);
        EmployeeStatus old = e.status;
        if (old == status) {
            return e;
        }
        e.status = status;
        e.statusChangedAt = Instant.now();
        audit.log("EMPLOYEE", "STATUS_CHANGE", actor,
                "员工 " + e.name + " <" + e.email + "> 状态 " + old + " -> " + status);
        if (status == EmployeeStatus.TRANSFERRED) {
            forwardRuleService.onRecipientTransferred(e, actor);
        }
        return e;
    }
}
