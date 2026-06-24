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

- `POST /api/v1/admin/indices/{tenantId}/rollover` — 수동 trigger (자동 ILM 외)
- `POST /api/v1/admin/indices/{tenantId}/ilm` — 정책 갱신

본 시스템의 application layer 는 항상 alias 만 사용 (실제 인덱스 이름 안 노출).

## 결과

- 인덱스 1개당 50GB 이하 — shard merge / rebalance 비용 통제됨
- 옛 데이터 자동으로 cold tier 로 이동 — 운영자 개입 불필요
- 보존 정책 변경 시 ILM 정책만 수정

## 다시 검토할 시점

- shard 당 50GB 가 너무 큰 경우 (검색 latency 증가) — 30GB 로 낮춤
- 운영 비용이 문제면 cold tier 를 별도 storage tier (S3 snapshot) 로

## 용어 풀이 (쉽게)

- **ILM (Index Lifecycle Management, 인덱스 수명주기 관리)** — 인덱스가 '생성→식어감→폐기'로 늙어 가는 단계를 정책으로 미리 정해 자동으로 처리하는 것. 새 우유는 앞 칸, 오래된 건 뒤로, 유통기한 지난 건 자동 폐기하는 냉장고.
- **rollover(롤오버)** — 인덱스가 정해진 크기·나이에 닿으면 더 안 키우고 '새 공책'으로 바꿔 적기 시작하는 것. 공책이 꽉 차면 다음 권을 펴는 셈.
- **alias(별칭) + atomic swap(무중단 교체)** — 검색은 항상 별명(read alias)으로만 부르고 실제 인덱스는 뒤에 숨긴다. 새 인덱스로 바꿀 때 별명이 가리키는 대상을 '한 순간에' 교체해 검색이 멈추지 않는다(간판은 그대로, 안쪽 매장만 순식간에 전환). 멀티테넌트 격리도 별칭으로.
- **write alias / read alias** — write는 '지금 새 로그를 적는 최신 인덱스 하나'를 가리키고, read는 '그 회사의 모든 인덱스'를 한꺼번에 가리켜 과거까지 검색되게 한다.
- **shard(샤드)** — 큰 인덱스를 여러 조각으로 쪼갠 단위. 한 조각이 너무 커지면 합치고 재배치하는 비용이 폭발해, 조각 크기를 50GB 이하로 통제한다(책을 권별로 나눠 한 권이 너무 두꺼워지지 않게).
- **force_merge / segment** — 인덱스 안 작은 조각(segment)들을 하나로 합쳐(force_merge) 조회를 빠르게 다지는 청소 작업. 흩어진 메모를 한 장으로 깔끔히 정리하는 셈.
- **hot / warm / cold tier** — 데이터 나이에 따라 두는 자리. 최근 건 빠른 SSD(hot), 좀 지난 건 보통 디스크(warm), 거의 안 보는 옛 건 싸고 느린 곳(cold)으로 내려 비용을 아낀다.
- **snapshot(스냅샷)** — 인덱스 상태를 통째로 떠서 S3 같은 저장소에 백업해 두는 것. 옛 데이터를 cold보다 더 싼 곳에 보관하거나 복구용으로 쓴다.

## 참고

- [OpenSearch ISM](https://opensearch.org/docs/latest/im-plugin/ism/index/)
- [Elastic ILM phases](https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-policy-definition.html) — OpenSearch ISM 의 모태
