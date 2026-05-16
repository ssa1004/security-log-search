package com.example.security.application.exception

import com.example.security.domain.common.TenantId

class TenantNotFoundException(tenantId: TenantId) :
    RuntimeException("테넌트를 찾을 수 없음: ${tenantId.value}")
