# Build multi-stage desde la raíz del monorepo (ver docker-compose.yml: context .)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY finance-api-mock/pom.xml finance-api-mock/pom.xml
COPY custodia-pep/pom.xml custodia-pep/pom.xml
COPY finance-api-mock/src finance-api-mock/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl finance-api-mock -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/finance-api-mock/target/finance-api-mock.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
