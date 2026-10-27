# Compilación
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# Ejecución: JRE mínimo, usuario sin privilegios
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S eduspace && adduser -S eduspace -G eduspace
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Lima"
USER eduspace
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
