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
- `POST /vader/core-server/client-prompt` — accepts a `ClientPrompt` (multipart form: `text`
  plus optional `files`), logs it. Persistence is not yet wired up.
