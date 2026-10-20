#!/bin/sh
# Headless physics test, runs the real Saro BoneRagdollPhysics with and without this addon's launch / sleep pose.
# From the repo root, Saro jar in ./libs, JDK 17+:  ./test-harness/run.sh
set -e
cd "$(dirname "$0")/.."
JAR=$(ls libs/*.jar | head -n1)
rm -rf /tmp/saros-fix-test && mkdir -p /tmp/saros-fix-test
javac -nowarn -d /tmp/saros-fix-test -cp "$JAR" \
  $(find test-harness -name '*.java') \
  src/main/java/de/sarocesch/sarosragdollfix/RagdollLaunch.java \
  src/main/java/de/sarocesch/sarosragdollfix/SleepPose.java
java -cp "/tmp/saros-fix-test:$JAR" Test
