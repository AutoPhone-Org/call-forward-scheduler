#!/usr/bin/env bash
# 创建 GitHub 开源仓库并推送（含 CI 流水线）。
#
# 前置：需先登录 gh
#   gh auth login
#
# 用法：
#   bash scripts/create-repo.sh <repo-name> [--public|--private]
#   例：bash scripts/create-repo.sh call-forward-scheduler --public
set -euo pipefail

REPO_NAME="${1:-call-forward-scheduler}"
VISIBILITY="${2:---public}"

# 校验 gh 已登录
if ! gh auth status >/dev/null 2>&1; then
  echo "❌ 尚未登录 GitHub，请先执行: gh auth login"
  exit 1
fi

echo "🚀 创建仓库 ${REPO_NAME} (${VISIBILITY}) ..."
gh repo create "${REPO_NAME}" "${VISIBILITY}" \
  --description "定时倒班切换自动呼叫转移排班助手（Shizuku 版，无 root）" \
  --source "$(pwd)" \
  --push \
  --remote origin

echo "✅ 仓库创建并推送完成"
echo "   远程地址：https://github.com/$(gh api user --jq .login)/${REPO_NAME}"
echo "   CI 流水线：.github/workflows/build.yml（push 后自动触发）"
