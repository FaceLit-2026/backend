FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 facelit \
    && useradd --uid 10001 --gid 10001 --create-home facelit
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
USER 10001:10001
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl --fail --silent --show-error --max-time 3 http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
