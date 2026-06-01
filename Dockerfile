# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .mvn ./mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache openssl
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render usa $PORT, local usa 8081
ENV PORT=8081
EXPOSE 8081

# JVM flags compatibles con Alpine:
# -XX:+UseContainerSupport → detecta límites de memoria del contenedor
# -XX:MaxRAMPercentage=75.0 → usa 75% de RAM disponible para heap
# Render a veces inyecta JAVA_TOOL_OPTIONS con flags no compatibles con Alpine.
# Lo limpiamos para evitar errores de JVM.
ENV JAVA_TOOL_OPTIONS=""

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
