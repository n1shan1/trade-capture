# build stage for simulator
FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# runtime stage
FROM eclipse-temurin:17-jre-alpine
ARG APP_HOME=/opt/app
WORKDIR ${APP_HOME}
COPY --from=build /workspace/target/trade-simulator-0.0.1-SNAPSHOT.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["sh","-c","java -XX:+UseContainerSupport -Xms128m -Xmx512m -jar /opt/app/app.jar"]
