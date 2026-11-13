#!/bin/bash
# Shared by every script under tools/scripts: strict mode, the repo root as the working
# directory, the names every local install agrees on, and small helpers. Sourced, never run.
#
# The names below are read by the other lib/ files and the scripts sourcing this one, which
# the linter can't see from here.
# shellcheck disable=SC2034

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-vader-agent-0}"
VADER_NAMESPACE="vader"
VADER_RELEASE="vader"
DOCKER_REPO="${DOCKER_REPO:-jeremyacase}"
# ollama-up.sh names its container this; pods reach it by name on the 'kind' Docker network.
LOCAL_OLLAMA_URL="http://vader-ollama-local:11434"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null || die "'$command_name' not found in PATH."
  done
}

require_docker_running() {
  docker info >/dev/null 2>&1 || die "Docker is not running."
}
