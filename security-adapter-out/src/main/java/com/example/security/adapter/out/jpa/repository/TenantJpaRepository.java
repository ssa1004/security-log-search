package com.example.security.adapter.out.jpa.repository;

import com.example.security.adapter.out.jpa.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, String> {}
