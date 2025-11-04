FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/certstream-kotlin-1.0.0-all.jar app.jar

# Expose ports
# HTTP server
EXPOSE 9000
# WebSocket lite stream
EXPOSE 9001
# WebSocket full stream
EXPOSE 9002
# WebSocket domains-only stream
EXPOSE 9003

# Run the application
CMD ["java", "-jar", "app.jar"]
