#!/bin/sh
set -eu

REPORT_DIR="scan-output/reports"
ARTIFACT_DIR="scan-output/artifacts"

set -- "$REPORT_DIR"/*_OWASPTop10.pdf
if [ "$#" -ne 1 ] || [ ! -s "$1" ]; then
    echo "ERROR: Expected one non-empty OWASP Top 10 PDF in $REPORT_DIR" >&2
    exit 1
fi
REPORT_PREFIX="${1%_OWASPTop10.pdf}"
for suffix in OWASPTop10.pdf OWASPTop10.html DeveloperWorkbook.pdf DeveloperWorkbook.html; do
    if [ ! -s "${REPORT_PREFIX}_$suffix" ]; then
        echo "ERROR: Missing or empty report: ${REPORT_PREFIX}_$suffix" >&2
        exit 1
    fi
done

rm -rf "$ARTIFACT_DIR"
mkdir -p "$ARTIFACT_DIR"
# Preserve module paths so equally named outputs cannot overwrite each other.
find . -path './scan-output' -prune -o -type f \
    \( -path '*/build/libs/*.jar' -o -path '*/build/libs/*.war' \) \
    -exec sh -c '
        for source do
            if [ ! -s "$source" ]; then
                echo "ERROR: Empty build artifact: $source" >&2
                exit 1
            fi
            target="scan-output/artifacts/${source#./}"
            mkdir -p "${target%/*}" && cp "$source" "$target" || exit 1
        done
    ' sh {} +

if [ -z "$(find "$ARTIFACT_DIR" -type f \( -name '*.jar' -o -name '*.war' \) -print -quit)" ]; then
    echo "ERROR: No jar/war found in module build/libs directories" >&2
    exit 1
fi
