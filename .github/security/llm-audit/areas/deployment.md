# Kubernetes deployment

The attacker is any pod in the cluster (including a sandbox or harness pod), or a network client of
an exposed Service.

- RBAC (`deploy/helm/templates/rbac.yaml`, `serviceaccount.yaml`): what can core-server's
  ServiceAccount do? Its operators create Jobs and pods; could a bug or injected spec let it create
  a privileged pod, mount host paths or read Secrets?
- Do harness and sandbox pods get a ServiceAccount token they don't need?
- Pod security across `deploy/helm/templates` and the manifests the operators build: root users,
  privilege escalation, capabilities, host namespaces, writable root filesystems.
- Network exposure: which Services are reachable from where, and is anything that should be
  internal exposed outside the cluster?
- Dockerfiles: base images, secrets in layers, running as root.
