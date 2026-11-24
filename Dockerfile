# ==============================================================================
# Multi-Stage Dockerfile for TradeLedger
# Stage 1: Build & package with Maven
# Stage 2: Minimal hardened JRE runtime
# ==============================================================================

# Stage 1: Builder
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy build descriptor and wrapper to leverage layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build executable jar
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S tradeledger && adduser -S tradeledger -G tradeledger
USER tradeledger

# Copy compiled jar from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

# Production JVM options (G1GC, container-aware memory limits)
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

