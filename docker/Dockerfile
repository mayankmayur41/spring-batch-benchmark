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

# Copy jar from builder stage (assumes single jar in target)
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

# Use non-root user for better security (create app user)
RUN useradd -m appuser || true
USER appuser

# Healthcheck: ensure Spring Boot actuator health endpoint responds
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Use sane JVM defaults for container environments
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
