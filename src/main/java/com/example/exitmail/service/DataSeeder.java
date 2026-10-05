package com.example.exitmail.service;

import com.example.exitmail.model.Employee;
import com.example.exitmail.model.EmployeeStatus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

/** 首次启动时写入演示数据(仅当员工表为空)。 */
@ApplicationScoped
public class DataSeeder {

    @Inject
    EntityManager em;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        Long count = em.createQuery("select count(e) from Employee e", Long.class).getSingleResult();
        if (count > 0) {
            return;
        }
        seed("zhangwei@corp.example", "张伟", "销售部", EmployeeStatus.DEPARTED);
        seed("lina@corp.example", "李娜", "销售部", EmployeeStatus.ACTIVE);
        seed("wangqiang@corp.example", "王强", "销售部", EmployeeStatus.ACTIVE);
        seed("chenchen@corp.example", "陈晨", "市场部", EmployeeStatus.ACTIVE);
        seed("zhaomin@corp.example", "赵敏", "人力资源部", EmployeeStatus.ACTIVE);
    }

    private void seed(String email, String name, String dept, EmployeeStatus status) {
        Employee e = new Employee(email, name, dept);
        e.status = status;
        e.statusChangedAt = Instant.now();
        em.persist(e);
    }
}
