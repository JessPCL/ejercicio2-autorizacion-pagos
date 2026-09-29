# Etapa 1: Construcción (Build)
FROM maven:3.9.4-eclipse-temurin-17-alpine AS builder
WORKDIR /app
# Copiamos el pom.xml y descargamos dependencias (aprovecha caché de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiamos el código fuente y compilamos
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Run)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]