# ---- Etapa 1: compilar el proyecto con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Etapa 2: imagen final, solo con el JRE y el .jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/tickets-service.jar app.jar

# /app/efs  -> aqui se monta el EFS (docker run -v /mnt/efs:/app/efs)
# /app/data -> base de datos H2 con los metadatos de los tickets
RUN mkdir -p /app/efs /app/data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
