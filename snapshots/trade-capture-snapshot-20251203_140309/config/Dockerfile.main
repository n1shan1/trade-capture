# build stage
FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# runtime stage
FROM eclipse-temurin:17-jre-alpine
ARG APP_HOME=/opt/app
WORKDIR ${APP_HOME}
COPY --from=build /workspace/target/pms-ingestion-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["sh","-c","java -XX:+UseContainerSupport -Xms256m -Xmx1g -jar /opt/app/app.jar"]
