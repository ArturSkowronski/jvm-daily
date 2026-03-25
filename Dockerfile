# ── JVM Build stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS jvm-builder
WORKDIR /build

# Cache Gradle wrapper and dependencies before copying source
COPY gradlew gradlew
COPY gradle/ gradle/
COPY settings.gradle.kts settings.gradle.kts
COPY app/build.gradle.kts app/build.gradle.kts
COPY vived-engine/build.gradle.kts vived-engine/build.gradle.kts
RUN ./gradlew :app:dependencies --no-daemon -q 2>&1 | tail -1

# Build distribution
COPY vived-engine/src vived-engine/src
COPY app/src app/src
RUN ./gradlew :app:installDist --no-daemon -q

# ── SvelteKit Build stage ───────────────────────────────────────────────────
FROM node:22-slim AS svelte-builder
WORKDIR /build
COPY viewer-svelte/package.json viewer-svelte/package-lock.json ./
RUN npm ci
COPY viewer-svelte/ ./
RUN npm run build

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.source="https://github.com/askowronski/jvm-daily"

WORKDIR /app

# Gradle distribution (bin/ + lib/)
COPY --from=jvm-builder /build/app/build/install/app/ /app/

# Config
COPY config/ /app/config/
COPY domains/ /app/domains/

# SvelteKit build output (static files served by Ktor)
COPY --from=svelte-builder /build/build/ /app/viewer/

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh /app/bin/app

# Persistent data lives in /data (mount a volume here)
RUN mkdir -p /data/output

ENV DUCKDB_PATH=/data/jvm-daily.duckdb
ENV OUTPUT_DIR=/data/output
ENV JOBRUNR_STORE=/data/jobrunr
ENV CONFIG_PATH=/app/config/sources.yml
ENV PIPELINE_CRON="0 7 * * *"
ENV OUTGRESS_DAYS=1
ENV DASHBOARD_PORT=8000
ENV VIEWER_PORT=8888

# JobRunr dashboard | viewer
EXPOSE 8000 8888

VOLUME /data

CMD ["/app/entrypoint.sh"]
