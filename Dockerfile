FROM gradle:8.6-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/install/seizureanalyzer /app
ENV CREDENTIALS_DIR=/data/tokens \
    CSV_OUT=/data/daily.csv \
    REPORT_HTML=/data/report.html \
    SUMMARY_JSON=/data/summary.json \
    EVENTS_OUT=/data/seizure_events.csv \
    EVENTS_JSON_OUT=/data/events-{runId}.json
ENTRYPOINT ["/app/bin/seizureanalyzer"]
