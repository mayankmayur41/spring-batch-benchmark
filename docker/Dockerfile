# Multi-stage Dockerfile: build with Maven and run with a slim JRE
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /workspace

# Copy only what is needed to build to leverage Docker caching
COPY pom.xml mvnw .mvn/ ./
COPY src ./src

# Build the application jar (no tests during image build to keep it fast)
RUN mvn -B -DskipTests package

# Final runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install utilities needed for Cloud SQL Auth Proxy bootstrap
RUN apt-get update && apt-get install -y --no-install-recommends curl netcat-openbsd ca-certificates && rm -rf /var/lib/apt/lists/*

# Copy jar from builder stage (assumes single jar in target)
COPY --from=builder /workspace/target/*.jar app.jar

# Download Cloud SQL Auth Proxy (v2) for secure DB connectivity
ARG CLOUD_SQL_PROXY_VERSION=2.11.2
RUN curl -sSL "https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v${CLOUD_SQL_PROXY_VERSION}/cloud-sql-proxy.linux.amd64" -o /app/cloud-sql-proxy \
    && chmod +x /app/cloud-sql-proxy

# Copy entrypoint script that bootstraps the Cloud SQL proxy
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080

# Create non-root user and change ownership of app files
RUN useradd -m appuser || true
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Healthcheck: ensure Spring Boot actuator health endpoint responds
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
