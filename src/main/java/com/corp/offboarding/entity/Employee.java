package com.corp.offboarding.entity;

import com.corp.offboarding.model.EmployeeStatus;
import com.corp.offboarding.entity.BaseEntity;
import jakarta.persistence.*;

/** 员工（邮箱账号）。离职员工是自动回复/转发规则的主体，接收人也必须是在职员工。 */
@Entity
@Table(name = "employee",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_email", columnNames = "email"))
public class Employee extends BaseEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String email;

    public String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public EmployeeStatus status = EmployeeStatus.ACTIVE;

    /** 人事确认的离职时间（模拟时钟 epoch 毫秒），null 表示尚未确认离职。 */
    public Long departureAt;

    public static Employee findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
