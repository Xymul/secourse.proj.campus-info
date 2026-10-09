#!/usr/bin/env bash
# ============================================================
#  后端接口冒烟测试（Debian 13 等 Linux）—— 对运行中的服务执行
#  等价于 Windows 版 scripts/smoke-test.ps1；实现见 scripts/smoke-test.py
#
#  用法：
#    scripts/smoke-test.sh
#    CAMPUS_API_BASE=http://127.0.0.1:8080/api scripts/smoke-test.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  echo "[错误] 未找到 python3，请先安装：sudo apt install python3" >&2
  exit 1
fi

exec python3 "$SCRIPT_DIR/smoke-test.py"
