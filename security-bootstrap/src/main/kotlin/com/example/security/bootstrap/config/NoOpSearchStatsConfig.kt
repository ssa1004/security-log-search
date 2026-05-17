package com.example.security.bootstrap.config

import com.example.security.application.port.`in`.ManageOpenSearchIndexUseCase.RolloverResult
import com.example.security.application.port.out.EventSearchPort
import com.example.security.application.port.out.EventStatsPort
import com.example.security.application.port.out.IndexAdminPort
import com.example.security.application.query.SearchResult
import com.example.security.application.query.StatsResult
import com.example.security.domain.tenant.Tenant
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenSearch / ClickHouse 가 disabled (local / test profile) 일 때 SearchService /
 * AggregateLogStatsService / OnboardTenantService 가 빈 wiring 실패하지 않도록 NoOp 빈을 제공.
 *
 * 실제 검색 / 집계 / 인덱스 관리는 동작하지 않지만 Spring 컨텍스트 부팅과 단위 테스트는 통과.
 */
@Configuration
class NoOpSearchStatsConfig {

    @Bean
    @ConditionalOnMissingBean
    fun noopEventSearch(): EventSearchPort =
        object : EventSearchPort {
            override fun search(query: com.example.security.application.query.SearchQuery): SearchResult =
                SearchResult.empty()
        }

    @Bean
    @ConditionalOnMissingBean
    fun noopEventStats(): EventStatsPort =
        object : EventStatsPort {
            override fun aggregate(query: com.example.security.application.query.StatsQuery): StatsResult =
                StatsResult.empty()
        }

    @Bean
    @ConditionalOnMissingBean
    fun noopIndexAdmin(): IndexAdminPort =
        object : IndexAdminPort {
            override fun provisionForTenant(tenant: Tenant) {}

            override fun triggerRollover(tenant: Tenant): RolloverResult =
                RolloverResult(false, null, null)

            override fun applyIlmPolicy(tenant: Tenant) {}

            override fun provisionClickHouseRowPolicy(tenant: Tenant) {}
        }
}
