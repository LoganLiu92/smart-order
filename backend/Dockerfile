# ===== build stage =====
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ===== runtime stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# Spring Boot 默认会产出 *-SNAPSHOT.jar 或类似名称，这里用通配
COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
