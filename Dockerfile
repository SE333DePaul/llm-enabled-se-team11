# Multi-stage build: build with Maven, run with Eclipse Temurin JRE

# Build stage
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml mvnw* ./
COPY .mvn .mvn
COPY src ./src
RUN mvn -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the built jar from the build stage (artifact name produced by Maven)
COPY --from=build /workspace/target/chatbot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
