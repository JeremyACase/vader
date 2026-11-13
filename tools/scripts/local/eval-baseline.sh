#!/bin/bash
# Purpose: Measure the baseline the system's prompts are compared against: run every benchmark
#          case under services/eval/python/eval-runner/cases once per seed against a fresh EVAL
#          install, grade each run with its case's checker, and write one result per run plus a
#          summary.
#
#          Each seed gets its own core-server restart, so its runs start from an empty database;
#          a run's deliverable and its result file land in <results>/runs/<case>/. Interrupted?
#          Rerun with the same --results and --skip-install: finished runs are skipped, and runs
#          that hit an infrastructure error (core-server unreachable) are retried.
#
#          While a run is going, the runner reports each change in its workflow and attempts (and
#          repeats itself every couple of minutes if nothing changes); each seed ends with a
#          table of how it went. Everything printed is also appended to <results>/eval.log.
#
#          An experiment is a change to deploy/helm/configurations/eval/eval.yaml (a setting, or
#          vader.orchestrator.local.model) followed by a baseline. A copy of the file is kept as
#          <results>/eval.yaml, and resuming refuses to run if the file has changed since.
#
# Usage:   tools/scripts/local/eval-baseline.sh [options]
#            --results DIR             default build/eval/baseline-<timestamp>
#            --seeds "N N ..."         default "42 1337 2024 7 99"
#            --cases "ID ..."          default every case
#            --run-timeout-minutes N   per run, default 60; a run still going is recorded as
#                                      timed out and never counts as a pass
#            --skip-install            reuse the EVAL release already installed
#
# Settings: EVAL_PORT (default 18080) is the local port core-server is forwarded to, plus every
#           setting install-eval.sh takes.
#
# Requires kind, helm, kubectl, curl and Docker. Run setup-helm-repos.sh once beforehand.

source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
source "$REPO_ROOT/tools/scripts/lib/vader.sh"
source "$REPO_ROOT/tools/scripts/lib/eval.sh"

RESULTS="build/eval/baseline-$(date +%Y%m%d-%H%M%S)"
SEEDS="42 1337 2024 7 99"
CASES=""
RUN_TIMEOUT_MINUTES=60
INSTALL=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --results) RESULTS="$2"; shift 2 ;;
    --seeds) SEEDS="$2"; shift 2 ;;
    --cases) CASES="$2"; shift 2 ;;
    --run-timeout-minutes) RUN_TIMEOUT_MINUTES="$2"; shift 2 ;;
    --skip-install) INSTALL=false; shift ;;
    *) die "unknown option '$1' (see the usage at the top of this script)" ;;
  esac
done

require_commands kind helm kubectl curl docker
require_docker_running
mkdir -p "$RESULTS"
RESULTS="$(cd "$RESULTS" && pwd)"
log_to_results "$RESULTS"
record_eval_configuration "$RESULTS"
echo "📁 Results: ${RESULTS} (follow along with: tail -f ${RESULTS}/eval.log)"

if [[ "$INSTALL" == true ]]; then
  bash tools/scripts/local/install-eval.sh
fi

trap stop_port_forward EXIT
for seed in $SEEDS; do
  pin_seed "$seed"
  start_port_forward
  await_core_server_reachable
  # shellcheck disable=SC2086 # the case ids are deliberately split into words
  run_eval_runner "$RESULTS" run --server "$(runner_server_url)" --seed "$seed" \
    --results /results --run-timeout-minutes "$RUN_TIMEOUT_MINUTES" --cases $CASES
  stop_port_forward
done

run_eval_runner "$RESULTS" summarize --results /results
echo "✅ Baseline measured. Resume or extend it with: $0 --skip-install --results ${RESULTS}"
