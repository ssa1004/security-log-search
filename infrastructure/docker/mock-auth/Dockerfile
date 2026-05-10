# auth-service stub — JWK Set + OIDC discovery 만 노출.
# 운영: https://github.com/ssa1004/auth-service
#
# 정적 파일을 이미지에 baking 해 둠 (Docker Desktop 의 file-sharing 제약 회피).
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY jwks.json /usr/share/nginx/html/jwks.json
COPY openid-configuration.json /usr/share/nginx/html/openid-configuration.json

EXPOSE 8080
