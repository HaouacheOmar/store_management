# Image unique pour tous les modules : docker build --build-arg MODULE=api-gateway backend/
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ${MODULE} -am package -DskipTests && cp ${MODULE}/target/${MODULE}.jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
