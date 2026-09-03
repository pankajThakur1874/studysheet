# Stage 1: Build Java 21 Spring Boot JAR
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src
COPY files ./files

# Build executable JAR without tests
RUN mvn clean package -DskipTests

# Stage 2: Lightweight Alpine JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built JAR and Markdown files from build stage
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/files ./files

# Create storage directory for H2 persistent database & objectstore
RUN mkdir -p /app/data

# Default port
EXPOSE 9990
ENV PORT=9990
ENV CLEAN_ON_STARTUP=true

# Launch Spring Boot app
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
