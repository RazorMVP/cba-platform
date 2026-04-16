#!/usr/bin/env bash
# CBA pre-push version guard
# Reads the tool input from stdin (JSON) and checks whether the command
# about to be executed is a git push. If it is, verifies that both
# cba-log.md and CLAUDE.md contain a "Confirmed Platform Versions" section.
# Exits 2 (blocking) with an error message if the check fails.
# Exits 0 to allow the command through for all other Bash calls.

set -euo pipefail

REPO_ROOT="/Users/razormvp/CoreBanking"
LOG_FILE="$REPO_ROOT/cba-log.md"
CLAUDE_FILE="$REPO_ROOT/CLAUDE.md"
REQUIRED_STRING="Confirmed Platform Versions"

# Read the full JSON input from stdin
INPUT="$(cat)"

# Extract the command field. We use basic grep/sed to avoid requiring jq.
COMMAND="$(echo "$INPUT" | grep -o '"command":"[^"]*"' | head -1 | sed 's/"command":"//;s/"//')"

# Only gate on git push commands (git push, git push origin main, vercel deploy --prod, etc.)
if echo "$COMMAND" | grep -qE '^git push'; then

  MISSING=()

  if ! grep -q "$REQUIRED_STRING" "$LOG_FILE" 2>/dev/null; then
    MISSING+=("cba-log.md")
  fi

  if ! grep -q "$REQUIRED_STRING" "$CLAUDE_FILE" 2>/dev/null; then
    MISSING+=("CLAUDE.md")
  fi

  if [ ${#MISSING[@]} -gt 0 ]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║           CBA VERSION RECORD GATE — PUSH BLOCKED            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "  git push is not allowed until platform versions are recorded."
    echo ""
    echo "  Missing 'Confirmed Platform Versions' section in:"
    for f in "${MISSING[@]}"; do
      echo "    ✗  $f"
    done
    echo ""
    echo "  Steps to unblock:"
    echo "    1. Run: git log --oneline -1 -- backend/"
    echo "    2. Run: git log --oneline -1 -- web/"
    echo "    3. Read versions from backend/pom.xml and web/package.json"
    echo "    4. Add a 'Confirmed Platform Versions' table to the current"
    echo "       session entry in cba-log.md"
    echo "    5. Update the '## Confirmed Platform Versions' section"
    echo "       near the top of CLAUDE.md"
    echo "    6. Commit both files, then re-run git push"
    echo ""
    echo "  See /cba skill Step 4 for the full version table template."
    echo ""
    # Exit code 2 = block the tool call
    exit 2
  fi

fi

# All other commands (or push checks passed) — allow through
exit 0
