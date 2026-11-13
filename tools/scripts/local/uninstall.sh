#!/bin/bash
# Purpose: Remove the local Vader release, plus the harness Jobs and sandboxes its operators
#          created, which Helm doesn't track.
#
# Usage:   tools/scripts/local/uninstall.sh [--cluster]
#            --cluster   also delete the KIND cluster itself. The local Ollama is separate: stop it
#                        with ollama-down.sh.
#
# Requires kind, helm and kubectl.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
source "$REPO_ROOT/tools/scripts/lib/vader.sh"

require_commands kind helm kubectl

if kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER_NAME"; then
  uninstall_release
else
  echo "ℹ️  No KIND cluster '${KIND_CLUSTER_NAME}'; nothing to uninstall."
fi

if [[ "${1:-}" == "--cluster" ]]; then
  echo "🗑️  Deleting KIND cluster '${KIND_CLUSTER_NAME}'..."
  kind delete cluster --name "$KIND_CLUSTER_NAME"
fi
