#!/bin/bash
# 智能代码审查系统 - 一键启动脚本
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JDK=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

echo "========================================"
echo "  CodeInspector 智能代码审查系统"
echo "========================================"

# 1. 释放端口
echo "[1/4] 释放端口..."
lsof -ti:8088 | xargs kill -9 2>/dev/null || true
lsof -ti:3000 | xargs kill -9 2>/dev/null || true
lsof -ti:3001 | xargs kill -9 2>/dev/null || true

# 2. 编译后端
echo "[2/4] 编译后端..."
cd "$DIR/backend"
JAVA_HOME="$JDK" mvn clean compile -q

# 3. 启动后端
echo "[3/4] 启动后端 (http://localhost:8088)..."
JAVA_HOME="$JDK" mvn spring-boot:run &
BACKEND_PID=$!

# 4. 启动前端
echo "[4/4] 启动前端 (http://localhost:3000)..."
cd "$DIR/frontend"
rm -rf node_modules/.vite 2>/dev/null || true
npm run dev &
FRONTEND_PID=$!

echo ""
echo "========================================"
echo "  后端: http://localhost:8088"
echo "  前端: http://localhost:3000"
echo "========================================"
echo "按 Ctrl+C 停止所有服务"
echo ""

# 等待退出信号
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo '服务已停止'" EXIT
wait
