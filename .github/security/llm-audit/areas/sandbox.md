# Python sandbox boundary

The attacker is code an agent submits to `run_python_code`, or anything able to reach a sandbox pod.

- `core-python-sandbox-server`: can submitted code escape its subprocess or per-pod workspace, read
  the server's own files or environment, or outlive its exec timeout?
- Is the sandbox server reachable only from core-server? Check the Services and pods the
  `PythonSandboxManifestBuilder` creates, and whether anything in `deploy/helm` restricts traffic to
  them.
- Can one task attempt reach another attempt's sandbox or workspace (`sandbox` package naming and
  lookup)?
- Resource exhaustion: CPU, memory, disk, process count and output size limits on the pod and the
  subprocess.
- File staging: can a staged or uploaded file name write outside the workspace?
