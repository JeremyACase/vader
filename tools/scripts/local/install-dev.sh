#!/bin/bash
# Purpose: Install (or upgrade) Vader into a local KIND cluster from the published images, with
#          the dev configuration and the local GPU Ollama. To run your own checkout instead, use
#          install-dev-from-source.sh.
#
# Usage:   tools/scripts/local/install-dev.sh
#
# Requires kind, helm, kubectl and Docker. Run setup-helm-repos.sh once beforehand.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
source "$REPO_ROOT/tools/scripts/lib/kind.sh"
source "$REPO_ROOT/tools/scripts/lib/vader.sh"

require_commands kind helm kubectl docker
require_docker_running

prepare_cluster
VADER_CONFIGURATION="dev" bash tools/scripts/local/ollama-up.sh
helm_install_or_upgrade dev
