FROM rust:1-bookworm AS build
WORKDIR /app

COPY . .
RUN cargo build --release

# Stage libgcc_s (needed for Rust's unwinder) under its multiarch path, so the runtime image can
# take it without hard-coding the architecture.
RUN mkdir -p "/runtime/lib/$(gcc -print-multiarch)" \
    && cp "$(gcc -print-file-name=libgcc_s.so.1)" "/runtime/lib/$(gcc -print-multiarch)/"

# rustls (not OpenSSL) is used for TLS, so the runtime image carries no OpenSSL: glibc from the
# nossl base plus libgcc_s is everything the binary links against.
FROM gcr.io/distroless/base-nossl-debian12

COPY --from=build /runtime/ /
COPY --from=build /app/target/release/core-agent-harness /usr/local/bin/core-agent-harness

# Matches the runAsUser the harness Job's pod security context sets.
USER 1000

ENTRYPOINT ["/usr/local/bin/core-agent-harness"]
