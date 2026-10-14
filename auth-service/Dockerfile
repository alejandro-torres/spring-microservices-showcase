# syntax=docker/dockerfile:1.7
# Imagen de auth-service. Se construye desde la raíz del repositorio, porque necesita los módulos compartidos:
#   docker build -f auth-service/Dockerfile -t showcase/auth-service .

# --- Compilación: Maven Wrapper con la caché de dependencias compartida entre builds ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# sharing=locked: las imágenes se construyen en paralelo y no deben escribir la caché a la vez.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -q -B -pl auth-service -am package -Dmaven.test.skip=true -Djacoco.skip=true
# Capas de Spring Boot: las dependencias (que casi nunca cambian) quedan en capas distintas que el código, así
# una nueva versión solo vuelve a subir la capa de la aplicación.
RUN java -Djarmode=tools -jar auth-service/target/auth-service-*.jar extract --layers --launcher \
        --destination /workspace/extracted

# --- Ejecución: solo el JRE, con un usuario sin privilegios ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 9000
# La JVM ajusta la memoria al límite del contenedor.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:9000/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
