#!/bin/bash

# Purpose: This script will allow devs to install a local Vader instance in Kubernetes-IN-Docker KIND.
#          It assumes that KIND is installed and available globally via the command line,
#          and that Helm and Kubectl have been installed.

echo Installing Vader into KIND from scratch...

# Check that 'kind' is available in the path
if ! command -v kind &> /dev/null; then
    echo "ERROR: 'kind' command not found. Please install KIND and ensure it's available in your PATH."
    exit 1
fi

# Check Helm and kubectl are also available
for cmd in helm kubectl; do
    if ! command -v "$cmd" &> /dev/null; then
        echo "ERROR: '$cmd' command not found. Please install $cmd and ensure it's available in your PATH."
        exit 1
    fi
done

helm dependency update deploy/helm/

if kind get clusters | grep -q "^vader-agent-0$"; then
    echo "KIND cluster 'vader-agent-0' already exists."
else
    kind create cluster --name vader-agent-0
fi

# Check if the namespace 'vader' already exists
if kubectl get namespace vader &> /dev/null; then
    echo "Namespace 'vader' already exists."
else
    echo "Creating namespace 'vader'..."
    kubectl create namespace vader
fi

# Create a shared data mount inside the KIND node
docker exec vader-agent-0-control-plane mkdir -p /mnt/data/vader

# This is to ensure core-server can manipulate the Kubernetes cluster; it will likely be a different service account in prod
kubectl apply -f deploy/compose/config/dev/vader_dev_service_account.yaml -n vader

# This is to ensure services have a shared mount path for anything they deploy in KIND
kubectl apply -f deploy/compose/config/dev/vader_dev_kind_pv.yaml -n vader

if helm status vader -n vader > /dev/null 2>&1; then
    echo "Helm release 'vader' exists — upgrading..."
    helm upgrade vader deploy/helm/ --values deploy/helm/configurations/dev/dev.yaml -n vader
else
    echo "Installing Helm release 'vader'..."
    helm install vader deploy/helm/ --values deploy/helm/configurations/dev/dev.yaml -n vader
fi
