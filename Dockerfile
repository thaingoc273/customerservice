FROM maven:3.9.6-eclipse-temurin-21-alpine as builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine

RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# ENV JAVA_OPTS="-Xms1024m -Xmx1024m"
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
