#!/usr/bin/env sh
set -eu
REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/aura-dsp-test.XXXXXX")
trap 'rm -rf "$TEST_DIR"' EXIT HUP INT TERM
javac -d "$TEST_DIR" "$REPO_DIR/app/src/main/java/org/aura/listen/audio/AudioProcessor.java" "$REPO_DIR/tests/audio/AudioProcessorTest.java" "$REPO_DIR/app/src/main/java/org/aura/listen/audio/AudioResampler.java" "$REPO_DIR/app/src/main/java/org/aura/listen/audio/SoundCategories.java" "$REPO_DIR/tests/audio/CategoryTest.java"
java -cp "$TEST_DIR" AudioProcessorTest
java -cp "$TEST_DIR" CategoryTest
