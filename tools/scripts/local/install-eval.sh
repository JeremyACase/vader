#!/bin/bash
# Purpose: Stand up a fresh Vader in EVAL mode for benchmarking its prompts: build every service
#          image from the working tree, load them into a local KIND cluster, and install with the
#          eval configuration (pinned sampling) against the local GPU Ollama.
#
#          Always builds from source -- a benchmark measures the prompts in this checkout, never a
#          published image. Always starts clean: any existing release is uninstalled first, with
#          its in-memory database and every harness Job and sandbox it left behind, so no earlier
#          run can leak into this one.
#
# Usage:   tools/scripts/local/install-eval.sh
#
# Settings (environment variables, all optional):
#   KIND_CLUSTER_NAME   default vader-agent-0
#   DOCKER_REPO         image namespace the chart pulls from, default jeremyacase
#   OPENJDK_VERSION     default 21
#
# Requires kind, helm, kubectl and Docker. Run setup-helm-repos.sh once beforehand.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
source "$REPO_ROOT/tools/scripts/lib/kind.sh"
source "$REPO_ROOT/tools/scripts/lib/images.sh"
source "$REPO_ROOT/tools/scripts/lib/vader.sh"

require_commands kind helm kubectl docker
require_docker_running

prepare_cluster
build_and_load_images
VADER_CONFIGURATION="eval" bash tools/scripts/local/ollama-up.sh
helm_install_fresh eval
wait_for_core_server

cat <<EOF

✅ Vader is running in EVAL mode. To reach it from this machine:
     kubectl -n ${VADER_NAMESPACE} port-forward svc/vader-core-server 8080:8080
   then confirm the mode, model, seed and prompt fingerprints in effect:
     curl -s localhost:8080/vader/core-server/llm/configuration
EOF
