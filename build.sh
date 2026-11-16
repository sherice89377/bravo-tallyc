#!/bin/sh
# Builds tallyc and tvm into build/classes with the JDK alone (no build tool, no downloads).
# Usage: ./build.sh          compile
#        ./build.sh test     compile, then run the test suite in tests/
set -eu
cd "$(dirname "$0")"
rm -rf build/classes build/test-classes
mkdir -p build/classes
find src -name '*.java' | sort > build/sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
if [ "${1:-}" = "test" ]; then
    mkdir -p build/test-classes
    find tests -name '*.java' | sort > build/test-sources.txt
    javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
    java -cp build/classes:build/test-classes tally.test.TestRunner
fi
