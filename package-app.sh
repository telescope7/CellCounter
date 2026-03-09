#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

APP_NAME="${APP_NAME:-CellCounter}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
DEST_DIR="${DEST_DIR:-packaging/dist}"
INPUT_DIR="${INPUT_DIR:-packaging/input}"
ICON_PATH="${ICON_PATH:-}"
DEFAULT_MAC_ICON="$ROOT_DIR/assets/CellCounter.icns"
DEFAULT_PNG_ICON="$ROOT_DIR/assets/cellcounter-icon-512.png"

usage() {
  cat <<'USAGE'
Usage:
  ./package-app.sh [options]

Options:
  --type=<app-image|dmg|pkg|exe|msi|deb|rpm>  Package type (default: app-image)
  --name=<AppName>                             App name (default: CellCounter)
  --dest=<outputDir>                           Output directory (default: packaging/dist)
  --input-dir=<stagingDir>                     Staging input directory (default: packaging/input)
  --icon=<path>                                Optional icon file for jpackage (overrides default)
  --help                                       Show this help

Environment overrides:
  APP_NAME, PACKAGE_TYPE, DEST_DIR, INPUT_DIR, ICON_PATH

Default icon behavior:
  - macOS: assets/CellCounter.icns (if present)
  - other platforms: assets/cellcounter-icon-512.png (if present)

Examples:
  ./package-app.sh
  ./package-app.sh --type=dmg
  ./package-app.sh --type=app-image --name="CellCounter"
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type=*)
      PACKAGE_TYPE="${1#*=}"
      shift
      ;;
    --type)
      PACKAGE_TYPE="$2"
      shift 2
      ;;
    --name=*)
      APP_NAME="${1#*=}"
      shift
      ;;
    --name)
      APP_NAME="$2"
      shift 2
      ;;
    --dest=*)
      DEST_DIR="${1#*=}"
      shift
      ;;
    --dest)
      DEST_DIR="$2"
      shift 2
      ;;
    --input-dir=*)
      INPUT_DIR="${1#*=}"
      shift
      ;;
    --input-dir)
      INPUT_DIR="$2"
      shift 2
      ;;
    --icon=*)
      ICON_PATH="${1#*=}"
      shift
      ;;
    --icon)
      ICON_PATH="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ -z "$ICON_PATH" ]]; then
  case "$(uname -s)" in
    Darwin)
      if [[ -f "$DEFAULT_MAC_ICON" ]]; then
        ICON_PATH="$DEFAULT_MAC_ICON"
      fi
      ;;
    *)
      if [[ -f "$DEFAULT_PNG_ICON" ]]; then
        ICON_PATH="$DEFAULT_PNG_ICON"
      fi
      ;;
  esac
fi

if [[ -n "$ICON_PATH" && ! -f "$ICON_PATH" ]]; then
  echo "Error: Icon path does not exist: $ICON_PATH" >&2
  exit 2
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: Maven (mvn) is required but not found on PATH." >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "Error: jpackage is required but not found on PATH." >&2
  echo "Install/use a JDK that includes jpackage (JDK 14+)." >&2
  exit 1
fi

if [[ "$PACKAGE_TYPE" != "app-image" && "$PACKAGE_TYPE" != "dmg" && "$PACKAGE_TYPE" != "pkg" && \
      "$PACKAGE_TYPE" != "exe" && "$PACKAGE_TYPE" != "msi" && "$PACKAGE_TYPE" != "deb" && \
      "$PACKAGE_TYPE" != "rpm" ]]; then
  echo "Error: Unsupported package type '$PACKAGE_TYPE'." >&2
  usage
  exit 2
fi

echo "Building application jars with Maven..."
mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

echo "Preparing packaging workspace..."
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR" "$DEST_DIR"

cp target/cellcounter.jar "$INPUT_DIR/"
cp target/dependency/*.jar "$INPUT_DIR/"

if [[ -f CellCounter.properties ]]; then
  cp CellCounter.properties "$INPUT_DIR/"
fi
if [[ -f tracking-config.example.properties ]]; then
  cp tracking-config.example.properties "$INPUT_DIR/"
fi
if [[ -f SIMULATION_GROUND_TRUTH.md ]]; then
  cp SIMULATION_GROUND_TRUTH.md "$INPUT_DIR/"
fi
if [[ -d docs/help ]]; then
  cp -R docs/help "$INPUT_DIR/help"
fi
if [[ -d assets ]]; then
  mkdir -p "$INPUT_DIR/assets"
  if [[ -f assets/cellcounter-icon-1024.png ]]; then
    cp assets/cellcounter-icon-1024.png "$INPUT_DIR/assets/"
  fi
  if [[ -f assets/cellcounter-icon-512.png ]]; then
    cp assets/cellcounter-icon-512.png "$INPUT_DIR/assets/"
  fi
fi

case "$PACKAGE_TYPE" in
  app-image)
    rm -rf "$DEST_DIR/$APP_NAME.app" "$DEST_DIR/$APP_NAME"
    ;;
  dmg)
    rm -f "$DEST_DIR/$APP_NAME.dmg"
    ;;
  pkg)
    rm -f "$DEST_DIR/$APP_NAME.pkg"
    ;;
  exe)
    rm -f "$DEST_DIR/$APP_NAME.exe"
    ;;
  msi)
    rm -f "$DEST_DIR/$APP_NAME.msi"
    ;;
  deb)
    rm -f "$DEST_DIR/$APP_NAME.deb"
    ;;
  rpm)
    rm -f "$DEST_DIR/$APP_NAME.rpm"
    ;;
esac

jpackage_cmd=(
  jpackage
  --type "$PACKAGE_TYPE"
  --name "$APP_NAME"
  --input "$INPUT_DIR"
  --main-jar cellcounter.jar
  --main-class com.prolymphname.cellcounter.CellCounterApp
  --dest "$DEST_DIR"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "-Dstdout.encoding=UTF-8"
  --java-options "-Dstderr.encoding=UTF-8"
)

if [[ -n "$ICON_PATH" ]]; then
  echo "Using app icon: $ICON_PATH"
  jpackage_cmd+=(--icon "$ICON_PATH")
fi

echo "Running jpackage (type=$PACKAGE_TYPE, name=$APP_NAME)..."
"${jpackage_cmd[@]}"

echo "Packaging complete."
echo "Output directory: $DEST_DIR"
