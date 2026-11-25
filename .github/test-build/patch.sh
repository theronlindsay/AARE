#!/usr/bin/env bash
# Turns this checkout into the test build (CI only, never committed):
#   - its own package name, so Obtainium tracks it apart from the regular app;
#   - its own label, so it's clear which one is installed.
# It can't install next to the regular app: both declare the car-host permission and the
# androidx.car.app.connection provider. Uninstall the regular app while testing.
set -euo pipefail
SUFFIX="${1:-.test}"
LABEL="${2:-AARE Test}"

sed -i "s/^\( *applicationId = \)\"net.prototux.aare\"$/\1\"net.prototux.aare${SUFFIX}\"/" app/build.gradle.kts
grep -q "applicationId = \"net.prototux.aare${SUFFIX}\"" app/build.gradle.kts

for f in app/src/main/res/values*/strings.xml; do
  sed -i "s|<string name=\"app_name\">[^<]*</string>|<string name=\"app_name\">${LABEL}</string>|" "$f"
done
grep -q "<string name=\"app_name\">${LABEL}</string>" app/src/main/res/values/strings.xml
echo "patched: net.prototux.aare${SUFFIX} \"${LABEL}\""
