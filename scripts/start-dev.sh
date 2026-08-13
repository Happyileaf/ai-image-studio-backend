#!/bin/bash
# =====================================================================
# start-dev.sh：后端本地开发启动
#
# 作用：
#   1. 从兄弟目录 ai-image-studio-infra 加载 .env（对齐 docker-compose.dev.yml 的密码）
#   2. 执行 mvn spring-boot:run（dev profile）
#
# 用法：
#   cd ai-image-studio-backend
#   chmod +x scripts/start-dev.sh
#   ./scripts/start-dev.sh
#
# 前置：
#   先在 ai-image-studio-infra 执行 ./scripts/start-dev.sh 启动 MySQL/Redis/RabbitMQ/MinIO
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${SCRIPT_DIR}"

# 尝试加载 infra 的 .env（兄弟目录）—— 逐行解析，避免含空格的变量被 shell 切分
INFRA_DIR="${SCRIPT_DIR}/../ai-image-studio-infra"
if [ -f "${INFRA_DIR}/.env" ]; then
  echo "[INFO] 加载基础设施环境变量：${INFRA_DIR}/.env"
  while IFS= read -r line || [ -n "$line" ]; do
    # 跳过空行与注释
    case "$line" in
      ''|\#*) continue ;;
    esac
    # 去掉 export 前缀
    line="${line#export }"
    # 切分 key=value（value 含 = 也 OK，只切第一个）
    key="${line%%=*}"
    value="${line#*=}"
    # 去掉首尾引号（双引号 / 单引号）
    value="${value%\"}"
    value="${value#\"}"
    value="${value%\'}"
    value="${value#\'}"
    # 只导出看起来合理的变量
    if [ -n "$key" ] && [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      export "${key}=${value}"
    fi
  done < "${INFRA_DIR}/.env"
else
  echo "[WARN] 找不到 ${INFRA_DIR}/.env，将使用 application-dev.yml 中的默认密码"
fi

echo ""
echo "=== 启动后端（Spring Boot dev profile）==="
echo "  Java: $(java -version 2>&1 | head -1)"
echo "  Maven: $(mvn -version 2>&1 | head -1)"
echo ""

exec mvn spring-boot:run -Dspring-boot.run.profiles=dev
