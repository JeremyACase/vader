# Mode gating and configuration

The attacker is anyone who can influence configuration (Helm values, environment, request
parameters) or who reaches a DEV/TEST-only surface in production.

- `vader.mode`: can the scripted model, canned results or other TEST-only behavior run outside
  `TEST`? Check both the startup guards in core-server and the Helm chart's render-time checks.
- DEV-only surfaces (e.g. the H2 console): are they unreachable in `PROD` on every path?
- Defaults: is any security-relevant property (MCP allowed hosts/origins, exposed tool groups,
  operators) unsafe when left at its default, or can it be set empty?
- Secrets: credentials or tokens committed in `application.properties`, Helm values or
  configmaps rather than injected as Kubernetes Secrets.
