# ── Stage 1: Build frontend (Next.js → static export) ─────────────────────────
FROM node:22-alpine AS frontend

WORKDIR /app/web
COPY transmute-playground/web/package.json transmute-playground/web/package-lock.json ./
RUN npm ci
COPY transmute-playground/web/ .
RUN npm run build

# ── Stage 2: Build server (Ktor shadow JAR) ───────────────────────────────────
FROM gradle:8.13-jdk17 AS build

WORKDIR /app
COPY . .

# Build the server shadow JAR
RUN gradle :transmute-playground:server:shadowJar --no-daemon --stacktrace

# ── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

# Install GStreamer runtime (needed for codec operations)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        gstreamer1.0-tools \
        gstreamer1.0-plugins-base \
        gstreamer1.0-plugins-good \
        gstreamer1.0-plugins-bad \
        gstreamer1.0-plugins-ugly \
        gstreamer1.0-libav \
        libgstreamer1.0-0 \
        libgstreamer-plugins-base1.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the shadow JAR
COPY --from=build /app/transmute-playground/server/build/libs/*-all.jar /app/server.jar

# Copy the Next.js static export
COPY --from=frontend /app/web/out/ /app/static/

# Environment
ENV TRANSMUTE_STATIC_DIR=/app/static
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/server.jar"]
