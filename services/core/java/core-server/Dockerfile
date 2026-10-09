ARG OPENJDK_VERSION=21
FROM eclipse-temurin:${OPENJDK_VERSION}

ENV PORT=8080
ENV JAR_FILE=vader-core-server.jar
ENV TARGET_DIR=/app

COPY build/libs/vader-core-server.jar ${TARGET_DIR}/${JAR_FILE}

# The Ubuntu base ships Canonical's pebble service manager outside dpkg, so apt never patches
# it; nothing here runs it, so drop it rather than carry its CVEs.
RUN rm -f /usr/bin/pebble

# The image's built-in non-root user; the server only reads its jar and mounted config.
USER 1000

EXPOSE ${PORT}

CMD ["java", "-Dspring.config.location=file:/app/etc/application.yaml", "-jar", "/app/vader-core-server.jar"]
