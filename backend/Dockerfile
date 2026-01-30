# ===== build stage =====
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src

# 关键：强制 spring-boot repackage
RUN mvn -DskipTests clean package spring-boot:repackage && \
    echo "== target files ==" && ls -la target && \
    echo "== manifest of candidate jar ==" && \
    for f in target/*.jar; do echo "--- $f ---"; jar xf "$f" META-INF/MANIFEST.MF || true; cat META-INF/MANIFEST.MF || true; rm -f META-INF/MANIFEST.MF; done

# ===== runtime stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /tmp/
RUN rm -f /tmp/*-plain.jar /tmp/original-*.jar || true && \
    ls -la /tmp && \
    mv /tmp/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
