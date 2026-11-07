FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
COPY backend/src/main/java/com/FaceLit/backend/notification/service/NotificationCatalog.java /backend/src/main/java/com/FaceLit/backend/notification/service/NotificationCatalog.java
COPY reviewed-frontend/ /reviewed-frontend/
ARG PUBLIC_ORIGIN
ENV EXPO_PUBLIC_API_URL=$PUBLIC_ORIGIN
ENV EXPO_PUBLIC_EMBEDDING_API_URL=$PUBLIC_ORIGIN
ENV EXPO_PUBLIC_EMBEDDING_API_KEY=unused-server-gateway
ENV EXPO_NO_DOTENV=1
ENV CI=1
ENV FACELIT_FRONTEND_BASELINE_ROOT=/reviewed-frontend
RUN npm test && npx --no-install tsc --noEmit && npm run lint
RUN npx --no-install expo export --platform web

FROM caddy:2-alpine
COPY --from=build /build/dist /srv
COPY backend/deploy/hostinger/Caddyfile /etc/caddy/Caddyfile
