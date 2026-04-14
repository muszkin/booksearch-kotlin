FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ENV NODE_VERSION=22.16.0
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g pnpm@latest \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
COPY backend/ backend/
COPY frontend/ frontend/

RUN chmod +x gradlew \
    && ./gradlew :backend:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends calibre \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/backend/build/libs/booksearch-v2.jar app.jar

RUN mkdir -p /app/data/library

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
