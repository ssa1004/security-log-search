# ArgoCD ApplicationSet 사용 가이드

본 디렉토리는 GitOps (ArgoCD) 로 dev / staging / prod 세 환경의 배포를 관리합니다.

## 적용

```bash
kubectl apply -n argocd -f applicationset.yaml
```

ApplicationSet 컨트롤러가 본 manifest 를 보고 다음 3개의 Application 을 자동 생성합니다.

- `security-log-search-dev` — `security-log-search-dev` namespace, `values-dev.yaml`
- `security-log-search-staging` — `security-log-search-staging` namespace, `values-staging.yaml`
- `security-log-search-prod` — `security-log-search-prod` namespace, `values-prod.yaml`

각 Application 은 main branch 의 변경을 자동 sync (prune + selfHeal) 합니다.

## values 파일 구조

```
helm/security-log-search/
├── Chart.yaml
├── values.yaml              ← 공통 default
├── values-dev.yaml          ← dev override (replicas=1, autoscaling off)
├── values-staging.yaml      ← staging override (replicas=2)
└── values-prod.yaml         ← prod override (replicas=3, ingress + cert-manager)
```

## 새 환경 추가

`applicationset.yaml` 의 `generators.list.elements` 에 새 env 추가 + 같은 이름의
`values-{env}.yaml` 추가.

## 운영 주의

- prod 의 secret 값 (DB password, ClickHouse password) 은 SealedSecret 또는
  ExternalSecret 으로 분리 관리 필수. 본 chart 의 `templates/secret.yaml` 은 dev / staging
  편의용.
- prod 의 image tag 는 main branch 의 latest 가 아닌 태그된 release version 사용 권장.
  현재 chart 는 `image.tag=main` 으로 latest 가 자동 deploy — release tagging 도입 시
  값을 변경.
