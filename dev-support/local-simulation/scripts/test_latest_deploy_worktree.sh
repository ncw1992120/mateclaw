#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
HELPER="$SCRIPT_DIR/lib/latest-deploy-worktree.sh"
source "$HELPER"

TEMP_ROOT="$(mktemp -d)"
first_worktree=""
second_worktree=""
cleanup() {
  [[ -z "$first_worktree" ]] || git -C "$REPO" worktree remove "$first_worktree" >/dev/null 2>&1 || true
  [[ -z "$second_worktree" ]] || git -C "$REPO" worktree remove "$second_worktree" >/dev/null 2>&1 || true
  rm -rf "$TEMP_ROOT"
}
trap cleanup EXIT

REMOTE="$TEMP_ROOT/remote.git"
REPO="$TEMP_ROOT/repo"
DEPLOY_PARENT="$TEMP_ROOT/deploy"
mkdir -p "$DEPLOY_PARENT"
git init --bare -q "$REMOTE"
git clone -q "$REMOTE" "$REPO" 2>/dev/null
git -C "$REPO" config user.name "Test Runner"
git -C "$REPO" config user.email "test@example.invalid"
git -C "$REPO" switch -q -c feature/dev_fu

printf 'first\n' > "$REPO/source.txt"
git -C "$REPO" add source.txt
git -C "$REPO" commit -q -m first
git -C "$REPO" push -q -u origin feature/dev_fu
git --git-dir="$REMOTE" symbolic-ref HEAD refs/heads/feature/dev_fu

first_worktree="$(mateclaw_create_latest_worktree "$REPO" feature/dev_fu "$DEPLOY_PARENT")"
first_commit="$(git -C "$REPO" rev-parse refs/heads/feature/dev_fu)"
if [[ "$(git -C "$first_worktree" rev-parse HEAD)" != "$first_commit" ]]; then
  echo "首次部署工作树未使用远端最新提交。" >&2
  exit 1
fi
if git -C "$first_worktree" symbolic-ref -q HEAD >/dev/null; then
  echo "部署工作树应使用 detached HEAD，不能占用用户分支。" >&2
  exit 1
fi

# A local-only commit must be used even when origin/feature/dev_fu has not moved.
printf 'local-only\n' > "$REPO/source.txt"
git -C "$REPO" add source.txt
git -C "$REPO" commit -q -m local-only
local_commit="$(git -C "$REPO" rev-parse refs/heads/feature/dev_fu)"

second_worktree="$(mateclaw_create_latest_worktree "$REPO" feature/dev_fu "$DEPLOY_PARENT")"
if [[ "$(git -C "$second_worktree" rev-parse HEAD)" != "$local_commit" ]]; then
  echo "部署工作树必须使用本地 feature/dev_fu 分支 HEAD，而不是远端提交。" >&2
  exit 1
fi
if [[ "$(<"$second_worktree/source.txt")" != "local-only" ]]; then
  echo "部署工作树没有包含本地 feature/dev_fu 分支的未推送提交。" >&2
  exit 1
fi
if git -C "$second_worktree" symbolic-ref -q HEAD >/dev/null; then
  echo "部署工作树应为 detached HEAD，不应占用本地 feature/dev_fu 分支。" >&2
  exit 1
fi
if ! mateclaw_same_git_repository "$REPO" "$second_worktree"; then
  echo "部署工作树应被识别为同一 Git 仓库。" >&2
  exit 1
fi

# The caller's current branch remains unchanged and the source branch is not checked out twice.
if [[ "$(git -C "$REPO" symbolic-ref --short HEAD)" != "feature/dev_fu" ]]; then
  echo "创建部署工作树不应切换调用方的当前分支。" >&2
  exit 1
fi

git -C "$REPO" worktree remove "$first_worktree"
first_worktree=""
git -C "$REPO" worktree remove "$second_worktree"
second_worktree=""
OTHER_CLONE="$TEMP_ROOT/other-clone"
git clone -q "$REMOTE" "$OTHER_CLONE"
if mateclaw_same_git_repository "$REPO" "$OTHER_CLONE"; then
  echo "独立 clone 不得被识别为同一 Git 仓库进程。" >&2
  exit 1
fi

if mateclaw_create_latest_worktree "$REPO" missing-branch "$DEPLOY_PARENT" >/dev/null 2>&1; then
  echo "本地部署分支不存在时必须明确失败，不能回退到远端分支。" >&2
  exit 1
fi

echo "部署工作树使用本地 feature/dev_fu 分支 HEAD（含未推送提交），且不切换调用方分支。"
