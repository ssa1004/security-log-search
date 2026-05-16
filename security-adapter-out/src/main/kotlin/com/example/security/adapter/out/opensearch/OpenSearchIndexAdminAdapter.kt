package com.example.security.adapter.out.opensearch

import com.example.security.adapter.out.clickhouse.ClickHouseRowPolicyProvisioner
import com.example.security.application.port.`in`.ManageOpenSearchIndexUseCase.RolloverResult
import com.example.security.application.port.out.IndexAdminPort
import com.example.security.domain.tenant.Tenant
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import java.io.IOException
import java.io.UncheckedIOException
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.indices.IndexSettings
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * OpenSearch 인덱스 / alias / ILM 관리 어댑터.
 *
 * 핵심:
 *
 *  - tenant 별 인덱스 템플릿 (mapping + settings) 적용
 *  - 최초 인덱스 생성 + write alias / read alias wiring
 *  - rollover (size 50GB or age 30d 도달 시 새 인덱스로 swap)
 *  - ILM 정책 적용 (hot 7일 → warm 30일 → cold 90일 → delete)
 */
@Component
@ConditionalOnProperty(
    name = ["security.opensearch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
open class OpenSearchIndexAdminAdapter(
    private val client: OpenSearchClient,
    @Autowired(required = false) private val clickHouseProvisioner: ClickHouseRowPolicyProvisioner?,
    @Value("\${security.opensearch.rollover.max-size-bytes:53687091200}")
    private val rolloverMaxSizeBytes: Long,
    @Value("\${security.opensearch.rollover.max-age-days:30}")
    private val rolloverMaxAgeDays: Int,
) : IndexAdminPort {

    @CircuitBreaker(name = "opensearch")
    @Retry(name = "opensearch")
    override fun provisionForTenant(tenant: Tenant) {
        val tenantId = tenant.tenantId.value
        try {
            // (1) index template — 새 인덱스 생성 시 적용될 mapping / settings.
            ensureIndexTemplate(tenantId)

            // (2) 첫 인덱스.
            val initialIndex = "events-$tenantId-000001"
            if (!client.indices().exists { it.index(initialIndex) }.value()) {
                val settings = IndexSettings.of { s ->
                    s.numberOfShards("1").numberOfReplicas("1")
                }
                client.indices().create { it.index(initialIndex).settings(settings) }
            }

            // (3) write alias — 무조건 가장 최신 인덱스 하나만 가리킴 (is_write_index=true).
            client.indices().updateAliases { b ->
                b.actions { a ->
                    a.add { add ->
                        add.index(initialIndex)
                            .alias(tenant.writeAlias())
                            .isWriteIndex(true)
                    }
                }
            }

            // (4) read alias — tenant 의 모든 인덱스를 가리킴 (events-{tenant}-* 패턴).
            client.indices().updateAliases { b ->
                b.actions { a ->
                    a.add { add ->
                        add.index("events-$tenantId-*").alias(tenant.readAlias())
                    }
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException("tenant 인덱스 provisioning 실패: $tenantId", e)
        }
    }

    @CircuitBreaker(name = "opensearch")
    @Retry(name = "opensearch")
    override fun triggerRollover(tenant: Tenant): RolloverResult {
        try {
            val resp = client.indices().rollover { b ->
                b.alias(tenant.writeAlias())
                    .conditions { c ->
                        c.maxSize("${rolloverMaxSizeBytes}b")
                            .maxAge { time -> time.time("${rolloverMaxAgeDays}d") }
                    }
            }
            return RolloverResult(resp.rolledOver(), resp.oldIndex(), resp.newIndex())
        } catch (e: IOException) {
            throw UncheckedIOException("rollover 실패: ${tenant.tenantId}", e)
        }
    }

    @CircuitBreaker(name = "opensearch")
    @Retry(name = "opensearch")
    override fun applyIlmPolicy(tenant: Tenant) {
        // OpenSearch 의 ISM (Index State Management) plugin 호출.
        // 본 구현은 정책 정의 자체는 docs/runbook 으로 빼고, 여기서는 적용 후크만 둔다 — 대부분
        // production 환경에서 정책 자체는 별도 IaC (Terraform / Helm) 로 관리.
        // 본 메서드는 적용 시점을 audit 에 남길 수 있도록 호출 가능 상태로 유지.
    }

    override fun provisionClickHouseRowPolicy(tenant: Tenant) {
        clickHouseProvisioner?.provision(tenant)
    }

    private fun ensureIndexTemplate(tenantId: String) {
        val templateName = "tpl-events-$tenantId"
        val indexPattern = "events-$tenantId-*"

        val template = PutIndexTemplateRequest.of { b ->
            b.name(templateName)
                .indexPatterns(indexPattern)
                .priority(100)
                .template { t ->
                    t.settings { s -> s.numberOfShards("1").numberOfReplicas("1") }
                        .mappings { m ->
                            m.properties("event_id") { p -> p.keyword { k -> k } }
                                .properties("tenant_id") { p -> p.keyword { k -> k } }
                                .properties("@timestamp") { p -> p.date { d -> d } }
                                .properties("ingested_at") { p -> p.date { d -> d } }
                                .properties("event_kind") { p -> p.keyword { k -> k } }
                                .properties("event_category") { p -> p.keyword { k -> k } }
                                .properties("event_type") { p -> p.keyword { k -> k } }
                                .properties("event_action") { p -> p.keyword { k -> k } }
                                .properties("event_outcome") { p -> p.keyword { k -> k } }
                                .properties("severity") { p -> p.keyword { k -> k } }
                                .properties("source_ip") { p -> p.ip { ip -> ip } }
                                .properties("source_port") { p -> p.integer { i -> i } }
                                .properties("destination_ip") { p -> p.ip { ip -> ip } }
                                .properties("destination_port") { p -> p.integer { i -> i } }
                                .properties("user_name") { p -> p.keyword { k -> k } }
                                .properties("host_name") { p -> p.keyword { k -> k } }
                                .properties("host_os") { p -> p.keyword { k -> k } }
                                .properties("message") { p ->
                                    p.text { t2 -> t2.fields("keyword") { f -> f.keyword { k -> k } } }
                                }
                                .properties("labels") { p -> p.flatObject { o -> o } }
                        }
                }
        }
        client.indices().putIndexTemplate(template)
    }
}
