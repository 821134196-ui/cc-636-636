#!/usr/bin/env bash
# 一条命令启动:构建前端 → 打包后端 → 启动应用( http://localhost:8080 )
set -euo pipefail
cd "$(dirname "$0")"

# ---- 工具链:优先使用系统 java/mvn,否则回退到本机用户目录安装 ----
if ! command -v java >/dev/null 2>&1; then
  if [ -d /home/node/tools/jdk-17.0.20.1+1 ]; then
    export JAVA_HOME=/home/node/tools/jdk-17.0.20.1+1
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    echo "错误: 未找到 Java 17+,请先安装 JDK 17" >&2
    exit 1
  fi
fi
if ! command -v mvn >/dev/null 2>&1; then
  if [ -x /home/node/tools/apache-maven-3.9.9/bin/mvn ]; then
    export PATH="/home/node/tools/apache-maven-3.9.9/bin:$PATH"
  else
    echo "错误: 未找到 Maven,请先安装 Maven 3.9+" >&2
    exit 1
  fi
fi

# ---- 1. 构建 Svelte 前端并放入 Quarkus 静态资源目录 ----
if command -v npm >/dev/null 2>&1; then
  echo "==> 构建前端"
  (cd frontend && { [ -f package-lock.json ] && npm ci --no-fund --no-audit || npm install --no-fund --no-audit; } && npm run build)
  rm -rf src/main/resources/META-INF/resources
  mkdir -p src/main/resources/META-INF/resources
  cp -r frontend/dist/* src/main/resources/META-INF/resources/
else
  echo "==> 未找到 npm,跳过前端构建(仅启动后端 API)"
fi

# ---- 2. 打包并启动 Quarkus ----
echo "==> 打包后端"
mvn -q -DskipTests package

echo "==> 启动: http://localhost:8080"
exec java -jar target/quarkus-app/quarkus-run.jar
