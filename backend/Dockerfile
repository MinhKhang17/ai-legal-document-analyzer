# Stage 1: Build stage (Dùng Alpine cho nhẹ và tận dụng cache dependency)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build source code
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run stage (Bảo mật bằng Non-root user)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo non-root user để bảo mật container
RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]