#!/bin/bash
# Installs the Vader Helm release, always pointed at the local Ollama ollama-up.sh runs. Sourced
# after common.sh, never run.

# Usage: helm_release_command install|upgrade <configuration> [extra helm arguments...]
helm_release_command() {
  local verb="$1" configuration="$2"
  helm "$verb" "$VADER_RELEASE" deploy/helm/ \
    --values "deploy/helm/configurations/${configuration}/${configuration}.yaml" \
    --set vader.orchestrator.local.externalBaseUrl="$LOCAL_OLLAMA_URL" \
    -n "$VADER_NAMESPACE" "${@:3}"
}

release_exists() {
  helm status "$VADER_RELEASE" -n "$VADER_NAMESPACE" >/dev/null 2>&1
}

# Workloads core-server's operators created -- harness Jobs, sandbox pods and their Services --
# which Helm never knew about, so uninstalling the release leaves them behind.
delete_operator_workloads() {
  kubectl delete jobs,pods,services -n "$VADER_NAMESPACE" \
    -l operators.vader.org/managed-by=vader --ignore-not-found
}

uninstall_release() {
  if release_exists; then
    echo "🗑️  Uninstalling Helm release '${VADER_RELEASE}'..."
    helm uninstall "$VADER_RELEASE" -n "$VADER_NAMESPACE" --wait
  else
    echo "ℹ️  No Helm release '${VADER_RELEASE}' to uninstall."
  fi
  delete_operator_workloads
}

# Upgrades in place when a release exists, keeping whatever it was running.
helm_install_or_upgrade() {
  local configuration="$1"
  helm dependency update deploy/helm/
  if release_exists; then
    echo "Helm release '${VADER_RELEASE}' exists -- upgrading with '${configuration}'..."
    helm_release_command upgrade "$configuration"
  else
    echo "Installing Helm release '${VADER_RELEASE}' with '${configuration}'..."
    helm_release_command install "$configuration"
  fi
}

# Replaces any existing release outright, so nothing from an earlier run -- its in-memory database,
# a lingering harness Job or sandbox -- survives into this one.
helm_install_fresh() {
  local configuration="$1"
  helm dependency update deploy/helm/
  uninstall_release
  echo "Installing a fresh Helm release '${VADER_RELEASE}' with '${configuration}'..."
  helm_release_command install "$configuration"
}

wait_for_core_server() {
  echo "⏳ Waiting for core-server to be ready..."
  kubectl rollout status deployment/vader-core-server -n "$VADER_NAMESPACE" --timeout=10m
}
