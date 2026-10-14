#!/bin/bash
# The local KIND cluster Vader installs into. Sourced after common.sh, never run.

ensure_cluster() {
  if kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER_NAME"; then
    echo "✅ KIND cluster '${KIND_CLUSTER_NAME}' already exists."
  else
    echo "🔧 Creating KIND cluster '${KIND_CLUSTER_NAME}'..."
    kind create cluster --name "$KIND_CLUSTER_NAME"
  fi
}

ensure_namespace() {
  kubectl get namespace "$VADER_NAMESPACE" >/dev/null 2>&1 \
    || kubectl create namespace "$VADER_NAMESPACE"
}

# core-server needs a service account that may manage workloads, and services need a shared
# mount path inside the KIND node for anything they deploy.
apply_dev_rbac_and_storage() {
  docker exec "${KIND_CLUSTER_NAME}-control-plane" mkdir -p /mnt/data/vader
  kubectl apply -f deploy/compose/config/dev/vader_dev_service_account.yaml -n "$VADER_NAMESPACE"
  kubectl apply -f deploy/compose/config/dev/vader_dev_kind_pv.yaml -n "$VADER_NAMESPACE"
}

prepare_cluster() {
  ensure_cluster
  ensure_namespace
  apply_dev_rbac_and_storage
}
