# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0]
### Added
- Initial module: an Angular app that submits a client prompt (text plus optional file
  attachments) to `core-server`, via a `ClientPrompt` model/service mirroring the Java
  `ClientPrompt` DTO.
- Built via the Gradle `node-gradle` plugin, packaged as a multi-stage Docker image (Node
  build, then NGINX serving the static bundle and reverse-proxying `/vader/` to
  `vader-core-server`), and wired into CI alongside the Java services.
- A mascot image slot in the header (`public/darth-vader.png`, not committed).
### Fixed
- NGINX crash-looped under its non-root container user because it defaulted to writing
  `/run/nginx.pid`. Pinned the pid file to the already-writable `/tmp/nginx` directory.
