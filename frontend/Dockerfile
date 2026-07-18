# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS build
ARG APP_VERSION=dev
ARG VCS_REF=unknown
WORKDIR /workspace/frontend

RUN corepack enable && corepack prepare pnpm@11.9.0 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
ENV VITE_APP_VERSION=$APP_VERSION
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="FinWatch Web" \
      org.opencontainers.image.version="$APP_VERSION" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.source="https://github.com/gimseyun690-jpg/FinWatch"

COPY deploy/nginx/portfolio.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html

ENV NGINX_ENVSUBST_FILTER=PUBLIC_HOST
USER 101:101
EXPOSE 8080 8443
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
