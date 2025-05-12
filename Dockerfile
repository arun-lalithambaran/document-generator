#LABEL authors="arun.lalithambaran"

# Use a base image with Java and Maven to build the application
FROM maven:3.8.6-openjdk-18 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests=true

# Use a lightweight base image for the application runtime
FROM openjdk:18-alpine
WORKDIR /app
COPY --from=build /app/target/afo-0.0.1-SNAPSHOT.jar ./afo-neptune.jar
EXPOSE 8080
CMD ["java", "-jar", "afo-neptune.jar"]