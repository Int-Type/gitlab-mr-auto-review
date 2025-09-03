# ---- build stage ----
FROM gradle:8.8-jdk17-alpine AS builder
WORKDIR /home/gradle/src
COPY . .
RUN gradle clean bootJar --no-daemon

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
