#!/usr/bin/env bash
# 渲染 specs 下所有 .puml 到 docs/uml
# 依赖：JDK 17 + Graphviz（类图/包图必需）+ tools/plantuml.jar
set -e
cd "$(dirname "$0")/.."
mkdir -p docs/uml
export GRAPHVIZ_DOT="C://Program Files//Graphviz//bin//dot.exe"
JAVA="${JAVA:-C:/Users/12497/.jdks/ms-17.0.20.1/bin/java.exe}"
"$JAVA" -DPLANTUML_LIMIT_SIZE=16384 -jar tools/plantuml.jar \
  -charset UTF-8 -o "$(pwd)/docs/uml" specs/*.puml
echo "渲染完成，输出目录：docs/uml"
