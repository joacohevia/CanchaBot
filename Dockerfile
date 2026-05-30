# =================================================================
# Stage 1: Build the application with Maven
# =================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .

# Make wrapper executable and download dependencies (cached if pom.xml unchanged)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build the application
COPY src ./src

# Package the application (skip tests for faster builds in CI/CD)
RUN ./mvnw clean package -DskipTests -B

# =================================================================
# Stage 2: Create the lightweight runtime image
# =================================================================
FROM eclipse-temurin:17-jre-alpine

# Install necessary packages for Telegram bot (native libraries)
# openssl is needed for SSL/TLS connections
RUN apk add --no-cache openssl

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port (Render will override with $PORT env var)
ENV PORT=8081
EXPOSE $PORT

# Health check endpoint (if you have spring-boot-actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:$PORT/actuator/health || exit 1

# Run the application
# -XX:+UseContainerResource: Enable JVM to respect container memory limits
# -XX:MaxRAMPercentage=75.0: Use 75% of available memory for heap (important for 512MB free tier)
ENTRYPOINT ["java", \
  "-XX:+UseContainerResource", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]