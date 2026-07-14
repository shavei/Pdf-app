#!/bin/bash
# SessionStart hook: keep the working branch fresh relative to the default branch.
#
# Why this exists: remote (web) sessions clone the repo and check out whatever
# branch the session was created on. When that branch was cut long ago, it can
# be many commits behind the default branch, so files added to the default
# branch since then are simply absent from the working copy — which looks like
# "the file is on GitHub but Claude can't see it."
#
# This hook fetches and, ONLY when the current branch carries no unique commits
# of its own, fast-forwards it onto the latest default branch. A branch with
# real (unpushed/unmerged) work is never touched — it just prints a warning.
#
# Every step is best-effort: any failure exits 0 so a session never fails to
# start because of this hook.
set -uo pipefail

# Only run in Claude Code on the web (remote) sessions. Remove this guard to
# also sync local CLI sessions.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

git fetch --quiet origin 2>/dev/null || exit 0

default_branch="$(git remote show origin 2>/dev/null | sed -n 's/.*HEAD branch: //p')"
default_branch="${default_branch:-main}"
base="origin/${default_branch}"

git rev-parse --verify --quiet "$base" >/dev/null 2>&1 || exit 0

current="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"

# On the default branch itself: just fast-forward to its remote tip.
if [ "$current" = "$default_branch" ] || [ "$current" = "HEAD" ]; then
  git merge --ff-only "$base" >/dev/null 2>&1 || true
  exit 0
fi

ahead="$(git rev-list --count "${base}..HEAD" 2>/dev/null || echo 0)"
behind="$(git rev-list --count "HEAD..${base}" 2>/dev/null || echo 0)"

if [ "$behind" -eq 0 ]; then
  echo "[session-start] '${current}' is up to date with ${base}."
elif [ "$ahead" -eq 0 ]; then
  if git merge --ff-only "$base" >/dev/null 2>&1; then
    echo "[session-start] Fast-forwarded '${current}' onto ${base} (was ${behind} behind)."
  else
    echo "[session-start] '${current}' is ${behind} behind ${base}; fast-forward skipped (working tree not clean)."
  fi
else
  echo "[session-start] '${current}' is ${ahead} ahead / ${behind} behind ${base}. Not auto-syncing: the branch has its own commits. Rebase onto ${base} if you want the latest."
fi

exit 0
