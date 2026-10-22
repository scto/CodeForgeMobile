#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Compiling TargetApp.java and JdiAttachTest.java..."
javac -g TargetApp.java JdiAttachTest.java

PORT=5005
echo "Starting TargetApp with JDWP suspend=y on port $PORT..."
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:$PORT TargetApp > target.out 2>&1 &
TARGET_PID=$!

sleep 1

echo "Running JdiAttachTest..."
java JdiAttachTest $PORT

wait "$TARGET_PID" || true

echo "--- Target output ---"
cat target.out
echo "---------------------"
