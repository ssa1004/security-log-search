package com.example.security.bootstrap

import com.example.security.SecurityLogSearchApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Spring 컨텍스트가 정상 부팅되는지 — Flyway 가 H2 에 control plane 마이그레이션을 적용하고,
 * OpenSearch / ClickHouse 어댑터는 conditional 로 disable 된 상태에서 빈 wiring 검증.
 */
@SpringBootTest(classes = [SecurityLogSearchApplication::class])
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    fun context_loads() {
        // 컨텍스트 로딩만 확인 — 빈 wiring 실패 시 본 테스트가 실패함.
    }
}
