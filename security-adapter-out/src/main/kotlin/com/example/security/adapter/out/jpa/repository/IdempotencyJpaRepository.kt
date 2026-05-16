package com.example.security.adapter.out.jpa.repository

import com.example.security.adapter.out.jpa.entity.IdempotencyKeyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyJpaRepository : JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.PK>
