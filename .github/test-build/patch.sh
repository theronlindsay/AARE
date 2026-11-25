#!/usr/bin/env bash
# Turns this checkout into the side-by-side test build (CI only, never committed):
#   - its own package name, so it installs next to the regular app;
#   - its own car-tools provider authority (two installs can't share one);
#   - its own label, so the two launcher icons can be told apart.
set -euo pipefail
SUFFIX="${1:-.test}"
LABEL="${2:-AARE Test}"

sed -i "s/^\( *applicationId = \)\"net.prototux.aare\"$/\1\"net.prototux.aare${SUFFIX}\"/" app/build.gradle.kts
grep -q "applicationId = \"net.prototux.aare${SUFFIX}\"" app/build.gradle.kts

sed -i 's/android:authorities="net.prototux.aare.car"/android:authorities="${applicationId}.car"/' app/src/main/AndroidManifest.xml
grep -q 'android:authorities="${applicationId}.car"' app/src/main/AndroidManifest.xml

for f in app/src/main/res/values*/strings.xml; do
  sed -i "s|<string name=\"app_name\">[^<]*</string>|<string name=\"app_name\">${LABEL}</string>|" "$f"
done
grep -q "<string name=\"app_name\">${LABEL}</string>" app/src/main/res/values/strings.xml
echo "patched: net.prototux.aare${SUFFIX} \"${LABEL}\""
