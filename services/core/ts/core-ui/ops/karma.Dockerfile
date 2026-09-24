# Test image for the Gradle `ngTest` task: Node plus Chromium, with the project's npm
# dependencies installed from the lockfile. Only package.json/package-lock.json are in the build
# context, so this layer is rebuilt only when dependencies change; the sources themselves are
# mounted in at run time.
FROM node:22-bookworm-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends chromium \
 && rm -rf /var/lib/apt/lists/*
ENV CHROME_BIN=/usr/bin/chromium

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
