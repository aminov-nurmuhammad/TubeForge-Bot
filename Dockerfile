# syntax=docker/dockerfile:1.7
FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package

FROM denoland/deno:bin-2.9.3 AS deno

FROM eclipse-temurin:17-jre-jammy AS runtime
ARG YT_DLP_VERSION=2026.7.4
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       ca-certificates curl ffmpeg python3 python3-pip tini \
    && pip3 install --no-cache-dir "yt-dlp[default]==${YT_DLP_VERSION}" \
    && useradd --system --uid 10001 --create-home --home-dir /app tubeforge \
    && rm -rf /var/lib/apt/lists/*
COPY --from=deno /deno /usr/local/bin/deno
WORKDIR /app
COPY --from=build /workspace/target/tubeforge-bot-4.0.0.jar /app/tubeforge.jar
RUN mkdir -p /app/data /app/storage && chown -R tubeforge:tubeforge /app
USER tubeforge
EXPOSE 8080
VOLUME ["/app/data", "/app/storage"]
ENV MEDIA_STORAGE_PATH=/app/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/urandom"
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "/app/tubeforge.jar"]
