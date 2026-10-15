#!/bin/bash
# Running the benchmark cases against an installed EVAL release: pinning its sampling seed,
# reaching core-server through a supervised port-forward, and running the eval-runner
# module in a container. Sourced after common.sh and vader.sh, never run.

EVAL_PORT="${EVAL_PORT:-18080}"
EVAL_RUNNER_MODULE="services/eval/python/eval-runner"
EVAL_PYTHON_IMAGE="python:3.12-slim"
EVAL_PIP_CACHE_VOLUME="vader-eval-pip-cache"

# Re-renders the release with a new seed and restarts core-server to read it. The restart also
# empties core-server's in-memory database, so each seed's runs start from nothing; the operator
# workloads are deleted first so no harness Job from the previous seed outlives it.
pin_seed() {
  local seed="$1"
  echo "🎲 Pinning sampling seed ${seed}..."
  helm_release_command upgrade eval --set vader.orchestrator.local.seed="$seed" >/dev/null
  delete_operator_workloads
  kubectl rollout restart deployment/vader-core-server -n "$VADER_NAMESPACE"
  wait_for_core_server
}

# A port-forward drops whenever its pod goes away and sometimes on its own, which a night-long
# run would otherwise die of, so it runs in a loop that restarts it until told to stop.
start_port_forward() {
  PORT_FORWARD_STATE="$(mktemp -d)"
  (
    until [[ -f "$PORT_FORWARD_STATE/stop" ]]; do
      kubectl -n "$VADER_NAMESPACE" port-forward svc/vader-core-server "${EVAL_PORT}:8080" \
        >/dev/null 2>&1 &
      echo $! >"$PORT_FORWARD_STATE/pid"
      wait "$!" || true
      sleep 1
    done
  ) &
  PORT_FORWARD_LOOP=$!
}

stop_port_forward() {
  if [[ -n "${PORT_FORWARD_STATE:-}" ]]; then
    touch "$PORT_FORWARD_STATE/stop"
    kill "$(cat "$PORT_FORWARD_STATE/pid" 2>/dev/null)" 2>/dev/null || true
    wait "$PORT_FORWARD_LOOP" 2>/dev/null || true
    rm -rf "$PORT_FORWARD_STATE"
    PORT_FORWARD_STATE=""
  fi
}

await_core_server_reachable() {
  local deadline=$((SECONDS + 300))
  until curl -sf --max-time 5 "http://localhost:${EVAL_PORT}/vader/core-server/llm/configuration" \
    >/dev/null; do
    ((SECONDS < deadline)) || die "core-server never answered on localhost:${EVAL_PORT}."
    sleep 3
  done
}

# Docker on Windows wants a Windows path for a bind mount; elsewhere a path is a path.
host_path() {
  if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi
}

# Docker Desktop routes host.docker.internal to the host's localhost, where the port-forward
# listens; Linux Docker has no such route, so the container shares the host's network instead.
runner_network_arguments() {
  if [[ "$(uname -s)" == "Linux" ]]; then
    echo "--network host"
  else
    echo ""
  fi
}

runner_server_url() {
  if [[ "$(uname -s)" == "Linux" ]]; then
    echo "http://localhost:${EVAL_PORT}"
  else
    echo "http://host.docker.internal:${EVAL_PORT}"
  fi
}

# Usage: run_eval_runner <results dir> <runner arguments...>
# The results directory is mounted at /results; pass it to the runner as --results /results.
run_eval_runner() {
  local results="$1"
  shift
  # shellcheck disable=SC2046 # the network arguments are deliberately split into words
  MSYS_NO_PATHCONV=1 docker run --rm $(runner_network_arguments) \
    -e PYTHONDONTWRITEBYTECODE=1 \
    -v "$(host_path "$REPO_ROOT/$EVAL_RUNNER_MODULE"):/eval:ro" \
    -v "$(host_path "$results"):/results" \
    -v "${EVAL_PIP_CACHE_VOLUME}:/root/.cache/pip" \
    -w /eval "$EVAL_PYTHON_IMAGE" \
    sh -c 'pip install --quiet --root-user-action=ignore -r requirements.txt && python -m eval_runner "$@"' \
    eval_runner "$@"
}
