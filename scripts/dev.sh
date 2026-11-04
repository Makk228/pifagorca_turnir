#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ]; then
  for task_jdk in .local/jdk-21*/Contents/Home; do
    if [ -x "$task_jdk/bin/java" ]; then export JAVA_HOME="$PWD/$task_jdk"; export PATH="$JAVA_HOME/bin:$PATH"; break; fi
  done
fi
mvn -B -Dmaven.repo.local="$PWD/.local/m2" -DskipTests package
DEMO_DATA=true exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar target/schitay-1.0.0.jar
