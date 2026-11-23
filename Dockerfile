# Stage 1: Build the React + Vite frontend
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# vite.config.ts outputs to ../src/main/resources/static -> /app/src/main/resources/static
RUN npm run build

# Stage 2: Resolve Maven dependencies and package the Spring Boot jar
FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
COPY --from=frontend-build /app/src/main/resources/static src/main/resources/static
RUN ./mvnw -B package -DskipTests

# Stage 3: Minimal non-root JRE runtime
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=backend-build --chown=app:app /app/target/*.jar /app/app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
