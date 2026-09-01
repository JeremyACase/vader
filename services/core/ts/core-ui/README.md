# core-ui

An Angular application that accepts a client prompt (text plus optional file
attachments) and submits it to `core-server`'s
`POST /vader/core-server/client-prompt` endpoint.

Built with the `com.github.node-gradle.node` Gradle plugin, which downloads
Node and runs an Angular production build as part of `./gradlew build`.
Packaged as a two-stage Docker image: a Node build stage produces the static
Angular bundle, and an NGINX stage serves it and reverse-proxies `/vader/` to
`vader-core-server`.

## Local development

```
npm install
npm start
```

## Build

```
./gradlew :services:core:ts:core-ui:build
```
