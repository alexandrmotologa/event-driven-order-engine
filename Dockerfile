# ============================================================================
# Stage 1: Build & Package
# ============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Cache maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# Extract layered jar for fast container startups
WORKDIR /workspace/target
RUN JAR_FILE=$(ls event-driven-order-engine*.jar | grep -v '\.original$' | head -n 1) && java -Djarmode=layertools -jar "$JAR_FILE" extract

# ============================================================================
# Stage 2: Minimal Distroless / Alpine Runtime
# ============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security hardening: add non-privileged application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered Spring Boot jar components
COPY --from=builder /workspace/target/dependencies/ ./
COPY --from=builder /workspace/target/spring-boot-loader/ ./
COPY --from=builder /workspace/target/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/application/ ./

USER appuser:appgroup

EXPOSE 8080

# Production JVM optimizations: Java 21 Generational ZGC & Virtual Threads
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational \
                       -XX:+ExitOnOutOfMemoryError \
                       -Dspring.threads.virtual.enabled=true \
                       -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
