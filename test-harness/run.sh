#!/bin/sh
# Headless physics test: runs the REAL Saro BoneRagdollPhysics class with and without this addon's launch.
# Usage: ./test-harness/run.sh   (from the repo root, Saro jar in ./libs, JDK 17+)
set -e
cd "$(dirname "$0")/.."
JAR=$(ls libs/*.jar | head -n1)
rm -rf /tmp/saros-fix-test && mkdir -p /tmp/saros-fix-test
javac -nowarn -d /tmp/saros-fix-test -cp "$JAR" \
  $(find test-harness -name '*.java') src/main/java/de/sarocesch/sarosragdollfix/RagdollLaunch.java
java -cp "/tmp/saros-fix-test:$JAR" Test
