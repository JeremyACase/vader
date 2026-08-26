# core-server

Minimal Spring Boot service for Vader's `core` layer. Establishes the base pattern that other
core services will follow: a `Config` bean that pins the JVM's default timezone to UTC on
startup.

## Running locally

```
../../../../gradlew :services:core:java:core-server:bootRun
```

## Endpoints

- `GET /actuator/health` — Spring Boot Actuator health check.
