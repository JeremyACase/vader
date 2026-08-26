ARG OPENJDK_VERSION=21
FROM eclipse-temurin:${OPENJDK_VERSION}

ENV PORT=8080
ENV JAR_FILE=vader-core-server.jar
ENV TARGET_DIR=/app

COPY build/libs/vader-core-server.jar ${TARGET_DIR}/${JAR_FILE}

EXPOSE ${PORT}

CMD ["java", "-Dspring.config.location=file:/app/etc/application.yaml", "-jar", "/app/vader-core-server.jar"]
