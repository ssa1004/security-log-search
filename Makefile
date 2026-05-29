# security-log-search — 자주 쓰는 명령 단일 진입점
#
#   make up         인프라(Postgres/Kafka/OpenSearch/ClickHouse/Flink) 기동
#   make ps         컨테이너 상태
#   make logs       인프라 로그 follow
#   make down       인프라 정지 (볼륨 유지)
#   make clean      인프라 정지 + 볼륨 삭제 (옛 데이터 제거)
#   make build      전체 gradle 빌드
#   make test       전체 테스트
#   make run        Spring Boot 앱 호스트 실행 (:8080)
#   make run-streaming  Flink correlation job jar 빌드 → 로컬 Flink 클러스터에 submit
#   make seed       데모 tenant(globex) + 기본 알람 룰 시드
#   make demo       Sigma 룰 import → 트리거 이벤트 → alert 흐름 데모
#
# 앱은 호스트에서 ./gradlew bootRun 으로 띄운다 — Kafka 는 localhost:29092 로 붙는다
# (docker-compose.yml 의 EXTERNAL listener). 컨테이너끼리는 kafka:9092.
# 자세한 건 README "빠른 실행".

COMPOSE := docker compose -f infrastructure/docker/docker-compose.yml
GRADLE  := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help up ps logs down clean build test \
        run run-streaming seed demo urls

help: ## 이 도움말
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## 인프라 기동 (Postgres/Kafka/OpenSearch/ClickHouse/Flink)
	$(COMPOSE) up -d
	@echo "→ OpenSearch Dashboards http://localhost:5601 · Flink UI :8081 · ClickHouse :8123"

ps: ## 컨테이너 상태
	$(COMPOSE) ps

logs: ## 인프라 로그 follow
	$(COMPOSE) logs -f --tail=100

down: ## 인프라 정지 (볼륨 유지)
	$(COMPOSE) down

clean: ## 인프라 정지 + 볼륨 삭제 (다음 기동 시 깨끗한 상태)
	$(COMPOSE) down -v

build: ## 전체 gradle 빌드 (테스트 제외)
	$(GRADLE) build -x test

test: ## 전체 테스트
	$(GRADLE) test

run: ## Spring Boot 앱 호스트 실행 (:8080, Kafka 는 localhost:29092)
	$(GRADLE) :security-bootstrap:bootRun

run-streaming: ## Flink job jar 빌드 → 로컬 Flink 클러스터에 submit
	$(GRADLE) :security-streaming:jar
	flink run -c com.example.security.streaming.job.AlertCorrelationJob \
	  security-streaming/build/libs/security-streaming-0.1.0.jar \
	  --bootstrap localhost:29092

seed: ## 데모 tenant(globex) + 기본 알람 룰 시드 (앱이 떠 있어야 함)
	./scripts/seed_demo_data.sh

demo: ## Sigma 룰 import → 트리거 이벤트 → alert 흐름 데모 (앱이 떠 있어야 함)
	./scripts/import_sigma_demo.sh

urls: ## 주요 UI / 엔드포인트
	@echo "앱            http://localhost:8080  (/swagger · /actuator)"
	@echo "OpenSearch Dashboards  http://localhost:5601"
	@echo "Flink Web UI  http://localhost:8081"
	@echo "ClickHouse    http://localhost:8123"
