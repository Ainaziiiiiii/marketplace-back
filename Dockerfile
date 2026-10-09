# Сборка всего приложения. Контекст — корень репозитория:
#   docker build -t green-eco-mall .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/green-eco-mall.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# порт задаёт хостинг через PORT, см. application.yml; API — под /api
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
