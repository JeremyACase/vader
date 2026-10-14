#!/bin/bash
# Builds every service image from the working tree and loads it into KIND, tagged :latest under
# the names the Helm chart pulls -- so the cluster runs exactly what is checked out. Sourced after
# common.sh, never run.

OPENJDK_VERSION="${OPENJDK_VERSION:-21}"

# core-server's image copies its prebuilt jar; every other image builds itself.
build_jars() {
  echo "🔨 Building the core-server jar..."
  ./gradlew :services:core:java:core-server:bootJar
}

service_dockerfiles() {
  find services \( -name node_modules -o -name build -o -name target \) -prune \
    -o -type f -name Dockerfile -print | sort
}

build_image() {
  local dockerfile="$1"
  local directory image
  directory="$(dirname "$dockerfile")"
  image="${DOCKER_REPO}/vader-$(basename "$directory" | tr '[:upper:]' '[:lower:]'):latest"
  echo "🔨 Building ${image} from ${dockerfile}"
  # Only the Java image declares OPENJDK_VERSION; docker warns about an unused build arg.
  if grep -qE '^[[:space:]]*ARG[[:space:]]+OPENJDK_VERSION' "$dockerfile"; then
    docker build --build-arg OPENJDK_VERSION="$OPENJDK_VERSION" -t "$image" "$directory"
  else
    docker build -t "$image" "$directory"
  fi
  echo "📦 Loading ${image} into KIND"
  kind load docker-image "$image" --name "$KIND_CLUSTER_NAME"
}

build_and_load_images() {
  build_jars
  local dockerfile
  while IFS= read -r dockerfile; do
    build_image "$dockerfile"
  done < <(service_dockerfiles)
  echo "✅ Every image built from the working tree and loaded into KIND."
}
