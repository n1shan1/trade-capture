# Multi-stage build for kafka-consumer
FROM eclipse-temurin:21-jdk AS builder

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy pom.xml and download dependencies (for better caching)
COPY kafka-consumer/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY kafka-consumer/src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 4001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]