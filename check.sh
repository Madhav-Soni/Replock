#!/bin/bash
cd "$(dirname "$0")/android" || exit 1
echo "== misplaced tests in src/main:"; find app/src/main -name "*Test*.kt"
echo "== folder/package mismatches:"
(cd app/src && for f in $(find main test -name "*.kt"); do dir=$(dirname "$f" | sed -E 's#^(main|test)/java/##; s#/#.#g'); pkg=$(grep -m1 '^package ' "$f" | awk '{print $2}'); [ "$dir" != "$pkg" ] && echo "MISMATCH: $f declares $pkg"; done)
echo "== INTERNET lines: $(grep -c INTERNET app/src/main/AndroidManifest.xml)"
echo "== build and tests:"
./gradlew cleanTestDebugUnitTest testDebugUnitTest 2>&1 | grep -E "^e: |FAILED|BUILD"
grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | sed 's/testsuite name="com.fitness.squatcounter.//'
