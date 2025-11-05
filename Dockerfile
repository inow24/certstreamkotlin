FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml
COPY pom.xml ./

# Download dependencies
RUN mvn dependency:go-offline

# Copy source code
COPY src src

# Build the application
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/certstream-kotlin-1.0.0-all.jar app.jar

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
