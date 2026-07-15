FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
