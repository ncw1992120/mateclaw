#!/usr/bin/env bash

mateclaw_create_latest_worktree() {
  local repo_root="$1" branch="$2" worktree_parent="$3"
  local deploy_ref commit worktree

  if ! git check-ref-format "refs/heads/$branch"; then
    echo "错误：无效的部署分支名：$branch" >&2
    return 1
  fi
  if [[ ! -d "$worktree_parent" ]]; then
    echo "错误：部署临时目录不存在：$worktree_parent" >&2
    return 1
  fi

  deploy_ref="refs/codex-deploy/$branch"
  if ! git -C "$repo_root" fetch --quiet --no-tags origin "+refs/heads/$branch:$deploy_ref"; then
    echo "错误：无法获取 origin/$branch；为避免使用旧代码，已取消构建。" >&2
    return 1
  fi
  commit="$(git -C "$repo_root" rev-parse --verify "$deploy_ref^{commit}")"
  worktree="$(mktemp -d "${worktree_parent%/}/mateclaw-dataagent.XXXXXX")"
  if ! git -C "$repo_root" worktree add --quiet --detach "$worktree" "$commit"; then
    rmdir "$worktree" 2>/dev/null || true
    echo "错误：无法为 $commit 创建隔离部署工作树。" >&2
    return 1
  fi

  printf '%s' "$worktree"
}

mateclaw_same_git_repository() {
  local first_common second_common
  first_common="$(git -C "$1" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || return 1
  second_common="$(git -C "$2" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || return 1
  [[ -n "$first_common" && "$first_common" == "$second_common" ]]
}
