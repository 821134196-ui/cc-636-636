package com.example.exitmail.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(nullable = false)
    public String name;

    public String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EmployeeStatus status = EmployeeStatus.ACTIVE;

    public Instant statusChangedAt;

    public Employee() {
    }

    public Employee(String email, String name, String department) {
        this.email = email;
        this.name = name;
        this.department = department;
    }
}
