package com.cooking.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false)
    public String email;

    public String phone;

    @Column(name = "full_name", nullable = false)
    public String fullName;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "avatar_url")
    public String avatarUrl;

    public String bio;

    @Column(name = "account_status", insertable = false)
    public String accountStatus;

    public Long getId() {
        return id;
    }
}
