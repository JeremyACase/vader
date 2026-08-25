#!/bin/bash

# Purpose: This script will set up devs with the requisite Helm repos to do local development of Vader.


echo Running one-time setup for Vader installation...
echo ...adding Helm repos...

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add yugabyte https://charts.yugabyte.com
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts

echo ...updating repos...
helm repo update

echo ...done.
