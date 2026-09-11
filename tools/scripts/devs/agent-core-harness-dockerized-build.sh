#!/bin/bash

# Purpose: Formats, lints, tests, and builds the core-agent-harness Rust crate entirely inside
#          Docker -- no local Rust toolchain required. Useful on machines where a local `cargo`
#          install isn't practical (e.g. Windows with Smart App Control blocking execution of
#          freshly-compiled, unsigned build-script/test binaries).
#
# Runs `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, and `cargo test` inside
# the same `rust:1-bookworm` image the crate's own Dockerfile builds with, then builds that
# Dockerfile's final image so a local run matches what CI produces.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "$SCRIPT_DIR/../../../services/core/rust/core-agent-harness" && pwd)"
RUST_IMAGE="rust:1-bookworm"
IMAGE_TAG="vader-core-agent-harness:local"
CARGO_REGISTRY_VOLUME="vader-agent-harness-cargo-registry"

if ! command -v docker &> /dev/null; then
    echo "ERROR: 'docker' command not found. Please install Docker and ensure it's available in your PATH."
    exit 1
fi

# Persists downloaded crate sources across runs; target/ already persists via the bind mount
# below since it lives directly under HARNESS_DIR on the host.
docker volume create "$CARGO_REGISTRY_VOLUME" > /dev/null

echo "== Formatting, linting, and testing core-agent-harness in $RUST_IMAGE =="
# MSYS_NO_PATHCONV is scoped to just this command: on Windows/git-bash it stops "/app" (a
# container-side path, not a host path) from being mangled into a bogus Windows path. It must
# NOT apply to the `docker build` call below, which needs its host path converted normally.
MSYS_NO_PATHCONV=1 docker run --rm \
    -v "$HARNESS_DIR":/app \
    -v "$CARGO_REGISTRY_VOLUME":/usr/local/cargo/registry \
    -w /app \
    "$RUST_IMAGE" \
    sh -c "rustup component add clippy rustfmt && \
           cargo fmt -- --check && \
           cargo clippy --all-targets -- -D warnings && \
           cargo test"

echo "== Building the core-agent-harness Docker image =="
docker build -t "$IMAGE_TAG" "$HARNESS_DIR"

echo "core-agent-harness: fmt/clippy/test passed, image built as $IMAGE_TAG"
