# Etapa 1: Construcción
FROM eclipse-temurin:17-alpine AS builder
WORKDIR /app

COPY . .

# 1. Dar permisos de ejecución al wrapper de Maven
RUN chmod +x ./mvnw

# 2. Compilar el proyecto (asegúrate de que tu proyecto no requiera red para descargar deps en este paso)
RUN ./mvnw clean package -DskipTests

# Etapa 2: Ejecución (Runtime)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 3. Copiar el JAR con el nombre EXACTO que genera tu pom.xml (artifactId-version.jar)
COPY --from=builder /app/target/ChatBot-0.0.1-SNAPSHOT.jar app.jar

# 4. Variables de entorno correctas para Spring Boot
# SERVER_PORT hace que Spring escuche en el puerto 8081
ENV SERVER_PORT=8081
# JAVA_OPTS para optimizar memoria en contenedores (evita OOMKill)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8081

# 5. Comando de entrada optimizado
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]