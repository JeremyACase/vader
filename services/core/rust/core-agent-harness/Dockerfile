FROM rust:1-bookworm AS build
WORKDIR /app

COPY . .
RUN cargo build --release

# rustls (not OpenSSL) is used for TLS, so only glibc/libgcc are needed at runtime.
FROM gcr.io/distroless/cc-debian12

COPY --from=build /app/target/release/core-agent-harness /usr/local/bin/core-agent-harness

ENTRYPOINT ["/usr/local/bin/core-agent-harness"]
