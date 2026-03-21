FROM gradle:8.6-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/install/seizureanalyzer /app
COPY src/main/resources /app/resources
ENTRYPOINT ["/app/bin/seizureanalyzer"]
