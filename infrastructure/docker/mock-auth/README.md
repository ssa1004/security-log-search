# mock-auth

`docker-compose.integration.yml` 의 auth-service stub 가 사용하는 정적 파일.

- `jwks.json` — RS256 public key 의 JWK Set. nginx 가 `/oauth2/jwks` 로 노출.
- `openid-configuration.json` — Spring Boot Resource Server 가 issuer-uri 로
  자동 디스커버리할 수 있게 한 OIDC discovery 문서. `/.well-known/openid-configuration`.
- `private-key.pem` — JWT 서명용 RSA private key. `scripts/integration-demo.sh`
  가 읽어서 mock JWT 를 발급. **테스트 용도 전용 — 운영 환경에 절대 사용 금지.**
- `nginx.conf` — 두 endpoint 만 응답하는 최소 설정.

운영 시에는 [auth-service](https://github.com/ssa1004/auth-service) 가 실제로
JWK Set 을 노출하고 token endpoint 를 제공합니다. 본 디렉토리의 파일은 그 stub 일
뿐입니다.
