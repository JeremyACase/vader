#!/usr/bin/env bash
# Runs one headless Claude Code pass with read-only tools and writes the model's JSON reply to
# the output file. Fails if the run errored or the reply isn't JSON, so a bad night uploads
# nothing rather than an empty result that would close open alerts.
#
# Usage: run-claude.sh <instructions-file> <output-file> <max-turns>
set -euo pipefail

instructions="$1"
output="$2"
max_turns="$3"
raw="$(mktemp)"

# Claude Code reports failures (auth, billing, turn limit) inside its JSON output, so print
# that output when the run fails rather than exiting silently.
if ! claude -p "Follow the instructions in ${instructions}." \
    --model "$CLAUDE_MODEL" \
    --allowedTools "Read,Grep,Glob" \
    --disallowedTools "Bash,Edit,Write,NotebookEdit,WebFetch,WebSearch" \
    --max-turns "$max_turns" \
    --output-format json > "$raw" \
  || ! jq -e '.is_error | not' "$raw" > /dev/null; then
  echo "::error::Claude Code run failed; its output follows."
  cat "$raw"
  exit 1
fi
jq -r '.result' "$raw" | sed '/^```/d' > "$output"
jq -e . "$output" > /dev/null
