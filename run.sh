#!/usr/bin/env bash
#
# 一条命令启动：构建 Svelte 前端 -> 打包 Quarkus 应用 -> 启动并打开管理页面。
# 用法：
#   ./run.sh          构建（跳过测试）并以前台方式启动，Ctrl+C 停止
#   ./run.sh test     构建并运行全部集成测试后启动
#   ./run.sh dev      热重载开发模式（Quarkus dev + Vite，需要两个终端时可自行拆分）
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# ---- 0. 工具链 ----------------------------------------------------------------
if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  echo "[run.sh] 未在 PATH 找到 java/mvn。"
  echo "         需要 JDK 21+ 与 Maven 3.9+（例如 Temurin 21）。"
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "[run.sh] 未找到 node（需要 Node 20+）。"; exit 1
fi

# 允许在没有全局 mvn 时通过环境变量指定
export JAVA_HOME="${JAVA_HOME:-}"
MVN="mvn"

mkdir -p data

MODE="${1:-run}"

# ---- 1. 前端 ------------------------------------------------------------------
echo "[run.sh] (1/3) 构建 Svelte 管理页面 …"
cd "$ROOT/web"
if [ ! -d node_modules ]; then
  npm install
fi
npm run build

# ---- 2. 后端 ------------------------------------------------------------------
cd "$ROOT"
echo "[run.sh] (2/3) 打包 Quarkus 应用（Hibernate + SQLite 方言）…"
if [ "$MODE" = "test" ]; then
  $MVN package
else
  $MVN package -DskipTests
fi

# ---- 3. 启动 ------------------------------------------------------------------
JAR="$ROOT/target/quarkus-app/quarkus-run.jar"
PORT="${PORT:-8080}"
echo "[run.sh] (3/3) 启动应用：http://localhost:$PORT （Ctrl+C 停止）"

# 尝试提示打开浏览器（失败不影响启动）
( sleep 3; command -v xdg-open >/dev/null && xdg-open "http://localhost:$PORT" >/dev/null 2>&1 || true ) &

exec java -jar "$JAR"
