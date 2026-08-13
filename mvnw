#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper 启动脚本（跨平台：Linux/macOS）
# ----------------------------------------------------------------------------
set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")/.." && pwd)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# 如果本地有 mvn 则直接用，否则尝试下载 wrapper jar
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi

if [ -f "$MAVEN_WRAPPER_JAR" ]; then
    exec java -classpath "$MAVEN_WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
fi

echo "[ERROR] Maven 不可用：本地未安装 mvn 且 .mvn/wrapper/maven-wrapper.jar 缺失。"
echo "  请安装 Maven 3.9+：brew install maven"
echo "  或运行：mvn wrapper:wrapper 生成完整的 mvnw 脚本与 wrapper jar。"
exit 1
