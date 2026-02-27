#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ $# -eq 0 ]]; then
  cat <<'USAGE'
Usage:
  ./run-ground-truth-eval.sh --video=<videoPath> --truth-events=<eventsCsvPath> --output-prefix=<outputPrefix> [options]

Example:
  ./run-ground-truth-eval.sh \
    --video=/Users/mthomas/eclipse-workspace/CellCounter/simcell.avi \
    --truth-events=/Users/mthomas/eclipse-workspace/CellCounter/simcell_events.csv \
    --output-prefix=/tmp/eval_run \
    --tracking-config=/Users/mthomas/eclipse-workspace/CellCounter/tracking-config.example.properties \
    --score-baseline-config=/Users/mthomas/eclipse-workspace/CellCounter/tracking-config.example.properties

Environment:
  OPENCV_LIB_PATH  Native OpenCV library directory (default: /usr/local/opencv/share/java/opencv4)
Notes:
  To compute GA-compatible score (same formula as bestScore), provide:
  --score-baseline-config=<path to the baseline config used in GA>
USAGE
  exit 1
fi

OPENCV_LIB_PATH="${OPENCV_LIB_PATH:-/usr/local/opencv/share/java/opencv4}"
CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT

echo "Compiling project and building classpath..."
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile="$CP_FILE"

RUNTIME_CP="target/classes:$(cat "$CP_FILE")"

echo "Running GroundTruthEvaluationCli..."
java \
  -Djava.library.path="$OPENCV_LIB_PATH" \
  -Dfile.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -cp "$RUNTIME_CP" \
  com.prolymphname.cellcounter.evaluation.GroundTruthEvaluationCli \
  "$@"
