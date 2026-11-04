# syntax=docker/dockerfile:1.7
# Image ofproduct-service. Built from the repository root, because it needs the shared modules:
#   docker build -f product-service/Dockerfile -t showcase/product-service .

# --- Build: Maven Wrapper with the dependency cache shared between builds ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# sharing=locked: the images are built in parallel and must not write to the cache at once.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -q -B -pl product-service -am package -Dmaven.test.skip=true -Djacoco.skip=true
# Spring Boot layers: the dependencies (which rarely change) go in different layers from the code, so
# a new version only re-uploads the application layer.
RUN java -Djarmode=tools -jar product-service/target/product-service-*.jar extract --layers --launcher \
        --destination /workspace/extracted

# --- Runtime: only the JRE, with an unprivileged user ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8081
# The JVM adjusts its memory to the container limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8081/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
