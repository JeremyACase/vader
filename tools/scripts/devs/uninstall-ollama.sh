#!/bin/bash
# Purpose: Stop and remove the Ollama container started by install-ollama.sh.
#
# Usage:   tools/scripts/devs/uninstall-ollama.sh [--purge-models]
#            --purge-models   also delete the downloaded models (several GB; re-downloaded on the
#                             next install-ollama.sh)
#
# The KIND install scripts point Vader at this Ollama, so a running Vader loses its LLM until a
# KIND install script is re-run (which starts it again).

set -euo pipefail

CONTAINER="vader-ollama-local"

command -v docker >/dev/null || { echo "ERROR: 'docker' not found in PATH."; exit 1; }

if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  docker rm -f "$CONTAINER" >/dev/null
  echo "🗑️  Removed '${CONTAINER}'."
else
  echo "ℹ️  No '${CONTAINER}' container to remove."
fi

if [[ "${1:-}" == "--purge-models" ]] && docker volume inspect vader-ollama-models >/dev/null 2>&1; then
  docker volume rm vader-ollama-models >/dev/null
  echo "🗑️  Deleted the downloaded models."
fi
