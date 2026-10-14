#!/bin/bash
# Purpose: Build every service image from the working tree, load them into a local KIND cluster,
#          then install (or upgrade) Vader with the dev configuration and the local GPU Ollama.
#
# Usage:   tools/scripts/local/install-dev-from-source.sh
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
bash tools/scripts/local/ollama-up.sh
helm_install_or_upgrade dev
