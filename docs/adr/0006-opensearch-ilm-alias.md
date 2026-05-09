# ADR-0006 OpenSearch ILM + alias swap + hot/warm/cold tier

- 상태: Accepted
- 날짜: 2026-04-27

## 맥락

OpenSearch 인덱스는 시간이 지나면 두 가지 문제가 생긴다.

- 단일 인덱스가 너무 커지면 (수백 GB) shard rebalancing / merge 비용이 폭발
- 옛 데이터는 거의 조회 안 되는데 hot (SSD) 노드를 점유

## 검토한 대안

1. 단일 인덱스 + 매일 reindex — 운영 부담 큼, 다운타임 가능성
2. 매일 새 인덱스 생성 (events-acme-2026.05.09) — 단순하지만 인덱스가 너무 많아짐 (1년
   = 365개), shard 수 폭발
3. ILM (Index Lifecycle Management) + write alias + read alias — rollover 가 size / age
   조건 도달 시 자동 새 인덱스 생성, alias 만 swap

## 결정

대안 3 채택. tenant 별로 다음 alias 구조:

```
events-{tenant}-write   → 가장 최근 인덱스 1개 (is_write_index=true)
events-{tenant}-read    → tenant 의 모든 인덱스 (events-{tenant}-*)
```

ILM 정책 (예: tenant 보존 1년 = retention=365d):

| 단계 | 조건 | 액션 |
|---|---|---|
| hot | (default) | rollover when size >= 50GB or age >= 30d |
| warm | age >= 7d | force_merge max_num_segments=1 |
| cold | age >= 30d | (read-only) |
| delete | age >= 365d | delete |

운영자 호출:

- {@code POST /api/v1/admin/indices/{tenantId}/rollover} — 수동 trigger (자동 ILM 외)
- {@code POST /api/v1/admin/indices/{tenantId}/ilm} — 정책 갱신

본 시스템의 application layer 는 항상 alias 만 사용 (실제 인덱스 이름 안 노출).

## 결과

- 인덱스 1개당 50GB 이하 — shard merge / rebalance 비용 통제됨
- 옛 데이터 자동으로 cold tier 로 이동 — 운영자 개입 불필요
- 보존 정책 변경 시 ILM 정책만 수정

## 다시 검토할 시점

- shard 당 50GB 가 너무 큰 경우 (검색 latency 증가) — 30GB 로 낮춤
- 운영 비용이 문제면 cold tier 를 별도 storage tier (S3 snapshot) 로

## 참고

- [OpenSearch ISM](https://opensearch.org/docs/latest/im-plugin/ism/index/)
- [Elastic ILM phases](https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-policy-definition.html) — OpenSearch ISM 의 모태
