# Build del backend (contexto = raiz del repo, ver render.yaml).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/mvnw backend/mvnw.cmd backend/pom.xml ./backend/
COPY backend/.mvn ./backend/.mvn
RUN chmod +x backend/mvnw
COPY backend/src ./backend/src
RUN ./backend/mvnw -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/backend/target/*.jar /app/app.jar
# V2+ de Flyway: application.properties apunta a filesystem:../shopify_db/...,
# con WORKDIR /app eso es /shopify_db/... (ver AGENTS.md: no duplicar en el jar).
COPY shopify_db/database/migrations /shopify_db/database/migrations
COPY backend/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
