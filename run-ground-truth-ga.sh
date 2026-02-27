#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ $# -eq 0 ]]; then
  cat <<'USAGE'
Usage:
  ./run-ground-truth-ga.sh \
    --video=<videoPath> \
    --truth-events=<eventsCsvPath> \
    --tracking-config=<config.properties> \
    --generations=<int> \
    --population=<int> \
    --mutation-rate=<double> \
    --max-timeout-sec=<double> \
    [options]

Example:
  ./run-ground-truth-ga.sh \
    --video=/Users/mthomas/eclipse-workspace/CellCounter/simcell.avi \
    --truth-events=/Users/mthomas/eclipse-workspace/CellCounter/simcell_events.csv \
    --tracking-config=/Users/mthomas/eclipse-workspace/CellCounter/tracking-config.example.properties \
    --generations=1 \
    --population=2 \
    --mutation-rate=0.10 \
    --max-timeout-sec=30 \
    --max-frames=600 \
    --output-dir=/tmp/ga_run

Environment:
  OPENCV_LIB_PATH  Native OpenCV library directory (default: /usr/local/opencv/share/java/opencv4)
Notes:
  --max-timeout-sec is a script-level alias that maps to
  --candidate-timeout-sec for the Java CLI.
  If neither is provided, default timeout is 45 seconds.
USAGE
  exit 1
fi

OPENCV_LIB_PATH="${OPENCV_LIB_PATH:-/usr/local/opencv/share/java/opencv4}"
CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT

echo "Compiling project and building classpath..."
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile="$CP_FILE"

RUNTIME_CP="target/classes:$(cat "$CP_FILE")"

processed_args=()
timeout_set=false
while [[ $# -gt 0 ]]; do
  # Ignore accidental blank/whitespace-only arguments (common with copy/paste).
  if [[ -z "${1//[[:space:]]/}" ]]; then
    shift
    continue
  fi
  case "$1" in
    --max-timeout-sec=*)
      timeout_set=true
      processed_args+=( "--candidate-timeout-sec=${1#*=}" )
      shift
      ;;
    --max-timeout-sec)
      timeout_set=true
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --max-timeout-sec" >&2
        exit 2
      fi
      processed_args+=( "--candidate-timeout-sec=$2" )
      shift 2
      ;;
    --candidate-timeout-sec=*)
      timeout_set=true
      processed_args+=( "$1" )
      shift
      ;;
    --candidate-timeout-sec)
      timeout_set=true
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --candidate-timeout-sec" >&2
        exit 2
      fi
      processed_args+=( "$1" "$2" )
      shift 2
      ;;
    *)
      processed_args+=( "$1" )
      shift
      ;;
  esac
done

if [[ "$timeout_set" == "false" ]]; then
  processed_args+=( "--candidate-timeout-sec=45" )
fi

echo "Running GroundTruthGeneticTunerCli..."
java \
  -Djava.library.path="$OPENCV_LIB_PATH" \
  -Dfile.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -cp "$RUNTIME_CP" \
  com.prolymphname.cellcounter.evaluation.GroundTruthGeneticTunerCli \
  "${processed_args[@]}"
