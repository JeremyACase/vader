#!/bin/bash
# Purpose: Run Ollama locally in Docker -- on this machine's NVIDIA GPU when Docker can reach one
#          -- and pull the model Vader is configured to use. KIND nodes can't reach the host's
#          GPU; a plain Docker container can. Called by both KIND install scripts, which point
#          Vader at it; safe to re-run (an existing container is reused, the model is only
#          downloaded once).
#
#          The container joins the 'kind' Docker network, where pods reach it by name, so run
#          this after the KIND cluster exists.
#
# Usage:   tools/scripts/devs/install-ollama.sh [--recreate]
#            --recreate   remove and recreate the container (e.g. after changing OLLAMA_GPU).
#                         Downloaded models live in a Docker volume and are kept.
#
# Settings (environment variables, all optional):
#   OLLAMA_GPU             auto (default) | on | off. auto uses the GPU if Docker can see one and
#                          falls back to CPU otherwise; on fails if it can't.
#   OLLAMA_MODEL           default: vader.orchestrator.local.model from deploy/helm/values.yaml
#   OLLAMA_CONTEXT_LENGTH  default: vader.orchestrator.local.contextTokens from the same file
#   OLLAMA_PORT            host port to publish on 127.0.0.1 (default 11434)
#   OLLAMA_IMAGE           default ollama/ollama:latest
#
# Requires Docker. For the GPU: an NVIDIA driver on the host, plus Docker Desktop's WSL2 backend
# (Windows) or the NVIDIA Container Toolkit (Linux).

set -euo pipefail
# Git Bash on Windows would otherwise rewrite container paths such as /root/.ollama.
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/../../.."

# Vader reaches this container at http://vader-ollama-local:11434 -- the install scripts hard-code
# that URL, so keep the name in step with them.
CONTAINER="vader-ollama-local"
VOLUME="vader-ollama-models"
VALUES_FILE="deploy/helm/values.yaml"

chart_value() {
  grep -m1 -E "^[[:space:]]+$1:" "$VALUES_FILE" | sed -E "s/^[[:space:]]+$1:[[:space:]]*//; s/[\"']//g"
}

OLLAMA_GPU="${OLLAMA_GPU:-auto}"
OLLAMA_MODEL="${OLLAMA_MODEL:-$(chart_value model)}"
OLLAMA_CONTEXT_LENGTH="${OLLAMA_CONTEXT_LENGTH:-$(chart_value contextTokens)}"
OLLAMA_PORT="${OLLAMA_PORT:-11434}"
OLLAMA_IMAGE="${OLLAMA_IMAGE:-ollama/ollama:latest}"
RECREATE=false
[[ "${1:-}" == "--recreate" ]] && RECREATE=true

command -v docker >/dev/null || { echo "ERROR: 'docker' not found in PATH."; exit 1; }
docker info >/dev/null 2>&1 || { echo "ERROR: Docker is not running."; exit 1; }

echo "Using OLLAMA_MODEL=${OLLAMA_MODEL}"
echo "Using OLLAMA_CONTEXT_LENGTH=${OLLAMA_CONTEXT_LENGTH}"
echo "Using OLLAMA_GPU=${OLLAMA_GPU}"

# ----------- GPU ------------------------
# nvidia-smi is injected into the container by NVIDIA's runtime only when a GPU is really passed
# through, so running it is an honest end-to-end check, not just "a driver exists on the host".
gpu_flags() {
  if [[ "$OLLAMA_GPU" == "off" ]]; then
    return
  fi
  echo "🔎 Checking whether Docker can reach an NVIDIA GPU..." >&2
  if docker run --rm --gpus all --entrypoint nvidia-smi "$OLLAMA_IMAGE" -L >&2; then
    echo "--gpus all"
  elif [[ "$OLLAMA_GPU" == "on" ]]; then
    echo "ERROR: OLLAMA_GPU=on, but Docker can't reach a GPU (see above)." >&2
    exit 1
  else
    echo "⚠️  No GPU reachable from Docker; Ollama will run on the CPU." >&2
  fi
}

# ----------- CONTAINER ------------------
if [[ "$RECREATE" == true ]] && docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "♻️  Removing existing '${CONTAINER}' (models are kept)..."
  docker rm -f "$CONTAINER" >/dev/null
fi

if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "✅ Container '${CONTAINER}' already exists; starting it if needed."
  docker start "$CONTAINER" >/dev/null
else
  GPU_FLAGS="$(gpu_flags)"
  echo "🚀 Starting '${CONTAINER}' (${GPU_FLAGS:-CPU only})..."
  # Published on 127.0.0.1 only: reachable from this machine for debugging, not from the network.
  # A failed start (typically the port already in use) still leaves a created-but-dead container
  # behind, which the next run would otherwise find and try to reuse -- so remove it.
  # shellcheck disable=SC2086 # GPU_FLAGS is deliberately word-split into separate arguments
  if ! docker run -d \
    --name "$CONTAINER" \
    --restart unless-stopped \
    $GPU_FLAGS \
    -p "127.0.0.1:${OLLAMA_PORT}:11434" \
    -v "${VOLUME}:/root/.ollama" \
    -e OLLAMA_CONTEXT_LENGTH="$OLLAMA_CONTEXT_LENGTH" \
    "$OLLAMA_IMAGE" >/dev/null; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    echo "ERROR: could not start '${CONTAINER}' (see above). If port ${OLLAMA_PORT} is taken -- e.g."
    echo "       by a native Ollama install -- stop that, or re-run with OLLAMA_PORT=<free port>."
    exit 1
  fi
fi

# Pods in KIND resolve containers on the 'kind' Docker network by name.
if ! docker network inspect kind >/dev/null 2>&1; then
  echo "⚠️  No 'kind' Docker network yet: create the KIND cluster, then re-run this script."
elif docker network inspect kind -f '{{range .Containers}}{{.Name}} {{end}}' | grep -qw "$CONTAINER"; then
  echo "✅ '${CONTAINER}' is already on the 'kind' network."
else
  docker network connect kind "$CONTAINER"
  echo "🔗 Connected '${CONTAINER}' to the 'kind' network."
fi

# ----------- MODEL ----------------------
echo "⏳ Waiting for Ollama to answer..."
for _ in $(seq 1 60); do
  docker exec "$CONTAINER" ollama list >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER" ollama list >/dev/null \
  || { echo "ERROR: Ollama did not come up; see 'docker logs ${CONTAINER}'."; exit 1; }

echo "📥 Pulling ${OLLAMA_MODEL} (skipped if already downloaded)..."
docker exec "$CONTAINER" ollama pull "$OLLAMA_MODEL"

echo "🔥 Loading the model to check where it runs..."
docker exec "$CONTAINER" ollama run "$OLLAMA_MODEL" "" >/dev/null 2>&1 || true
docker exec "$CONTAINER" ollama ps

echo
echo "✅ Ollama is running. The PROCESSOR column above should read '100% GPU' on a GPU machine;"
echo "   'CPU' or a CPU/GPU split means the model doesn't fit in GPU memory or no GPU was found."
