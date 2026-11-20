#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 1 ]]; then
    echo "Usage: tools/forms/check-printed-sheets.sh EMPTY_OUTPUT_DIRECTORY [COUNTS ...]" >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/../.." && pwd)
src="$root/app/src/main/java/com/zipgrade/omrscanner"
android_jar="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}/platforms/android-36/android.jar"
classes=$(mktemp -d)
trap 'rm -rf -- "$classes"' EXIT
javac -cp "$android_jar" -sourcepath "$root/app/src/main/java" -d "$classes" \
  "$root/tools/forms/CheckPrintedSheets.java" "$src/CustomSheetPdf.java" "$src/OmrNativeCvEngine.java"
java -Djava.awt.headless=true -Xmx512m -cp "$classes:$android_jar:$root/app/src/main/resources" com.zipgrade.omrscanner.CheckPrintedSheets "$@"
