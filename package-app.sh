#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

APP_NAME="${APP_NAME:-CellCounter}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
DEST_DIR="${DEST_DIR:-packaging/dist}"
INPUT_DIR="${INPUT_DIR:-packaging/input}"
ICON_PATH="${ICON_PATH:-}"
MVN_CMD="${MVN_CMD:-}"
JPACKAGE_CMD="${JPACKAGE_CMD:-}"
MAC_SIGN="${MAC_SIGN:-0}"
MAC_SIGN_IDENTITY="${MAC_SIGN_IDENTITY:-}"
MAC_SIGN_KEYCHAIN="${MAC_SIGN_KEYCHAIN:-}"
MAC_PACKAGE_IDENTIFIER="${MAC_PACKAGE_IDENTIFIER:-com.prolymphname.cellcounter}"
MAC_NOTARIZE="${MAC_NOTARIZE:-0}"
NOTARYTOOL_PROFILE="${NOTARYTOOL_PROFILE:-}"
APPLE_ID="${APPLE_ID:-}"
APPLE_TEAM_ID="${APPLE_TEAM_ID:-}"
APP_PASSWORD_ENV="${APP_PASSWORD_ENV:-APPLE_APP_SPECIFIC_PASSWORD}"
DEFAULT_MAC_ICON="$ROOT_DIR/assets/CellCounter.icns"
DEFAULT_PNG_ICON="$ROOT_DIR/assets/cellcounter-icon-512.png"
OS_NAME="$(uname -s)"

is_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

resolve_executable() {
  local override="${1:-}"
  shift
  if [[ -n "$override" ]]; then
    if [[ -x "$override" ]]; then
      printf '%s\n' "$override"
      return 0
    fi
    if command -v "$override" >/dev/null 2>&1; then
      command -v "$override"
      return 0
    fi
    return 1
  fi

  local candidate
  for candidate in "$@"; do
    if [[ "$candidate" == */* ]]; then
      if [[ -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    elif command -v "$candidate" >/dev/null 2>&1; then
      command -v "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_artifact_path() {
  case "$PACKAGE_TYPE" in
    app-image)
      if [[ "$OS_NAME" == "Darwin" && -d "$DEST_DIR/$APP_NAME.app" ]]; then
        printf '%s\n' "$DEST_DIR/$APP_NAME.app"
        return 0
      fi
      if [[ -e "$DEST_DIR/$APP_NAME" ]]; then
        printf '%s\n' "$DEST_DIR/$APP_NAME"
        return 0
      fi
      ;;
    dmg|pkg|exe|msi|deb|rpm)
      local extension=".$PACKAGE_TYPE"
      local artifact
      artifact="$(find "$DEST_DIR" -maxdepth 1 -type f -name "*$extension" | sort | tail -n 1 || true)"
      if [[ -n "$artifact" ]]; then
        printf '%s\n' "$artifact"
        return 0
      fi
      ;;
  esac
  return 1
}

notarize_artifact() {
  local artifact_path="$1"
  local submit_target="$artifact_path"
  local staple_target="$artifact_path"

  if [[ "$artifact_path" == *.app ]]; then
    submit_target="${artifact_path%/}-notarize.zip"
    rm -f "$submit_target"
    echo "Creating notarization archive: $submit_target"
    ditto -c -k --keepParent "$artifact_path" "$submit_target"
  fi

  local notary_cmd=(xcrun notarytool submit "$submit_target" --wait)

  if [[ -n "$NOTARYTOOL_PROFILE" ]]; then
    notary_cmd+=(--keychain-profile "$NOTARYTOOL_PROFILE")
  else
    local app_password="${!APP_PASSWORD_ENV:-}"
    if [[ -z "$APPLE_ID" || -z "$APPLE_TEAM_ID" || -z "$app_password" ]]; then
      echo "Error: notarization requires either --notary-profile or all of --apple-id, --team-id, and env $APP_PASSWORD_ENV." >&2
      exit 2
    fi
    notary_cmd+=(--apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$app_password")
  fi

  echo "Submitting artifact for notarization: $submit_target"
  "${notary_cmd[@]}"

  echo "Stapling notarization ticket..."
  xcrun stapler staple "$staple_target"
}

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
  --mac-sign                                   Enable macOS code signing via jpackage
  --mac-sign-identity=<name>                   Signing identity for jpackage (--mac-signing-key-user-name)
  --mac-sign-keychain=<path>                   Optional keychain passed to jpackage
  --mac-package-identifier=<id>                macOS package identifier (default: com.prolymphname.cellcounter)
  --notarize                                   Submit the built macOS artifact to Apple notarization and staple it
  --notary-profile=<profile>                   notarytool keychain profile name (recommended)
  --apple-id=<id>                              Apple ID for notarization when not using a keychain profile
  --team-id=<id>                               Apple Developer team ID for notarization
  --app-password-env=<ENV_NAME>                Env var holding the app-specific password (default: APPLE_APP_SPECIFIC_PASSWORD)
  --help                                       Show this help

Environment overrides:
  APP_NAME, PACKAGE_TYPE, DEST_DIR, INPUT_DIR, ICON_PATH, MVN_CMD, JPACKAGE_CMD,
  MAC_SIGN, MAC_SIGN_IDENTITY, MAC_SIGN_KEYCHAIN, MAC_PACKAGE_IDENTIFIER,
  MAC_NOTARIZE, NOTARYTOOL_PROFILE, APPLE_ID, APPLE_TEAM_ID, APP_PASSWORD_ENV

Default icon behavior:
  - macOS: assets/CellCounter.icns (if present)
  - other platforms: assets/cellcounter-icon-512.png (if present)

Notes:
  - On macOS, Maven is auto-discovered from PATH, /opt/homebrew/bin/mvn, or /usr/local/bin/mvn.
  - Notarization requires a signed macOS artifact. If --notarize is set, --mac-sign is enabled automatically.
  - Recommended notarization setup:
      xcrun notarytool store-credentials <profile> --apple-id <id> --team-id <team> --password <app-specific-password>

Examples:
  ./package-app.sh
  ./package-app.sh --type=dmg
  ./package-app.sh --type=dmg --mac-sign --mac-sign-identity "Developer ID Application: Example, Inc. (TEAMID)"
  ./package-app.sh --type=dmg --mac-sign --notarize --notary-profile cellcounter-notary
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
    --mac-sign)
      MAC_SIGN=1
      shift
      ;;
    --mac-sign-identity=*)
      MAC_SIGN_IDENTITY="${1#*=}"
      shift
      ;;
    --mac-sign-identity)
      MAC_SIGN_IDENTITY="$2"
      shift 2
      ;;
    --mac-sign-keychain=*)
      MAC_SIGN_KEYCHAIN="${1#*=}"
      shift
      ;;
    --mac-sign-keychain)
      MAC_SIGN_KEYCHAIN="$2"
      shift 2
      ;;
    --mac-package-identifier=*)
      MAC_PACKAGE_IDENTIFIER="${1#*=}"
      shift
      ;;
    --mac-package-identifier)
      MAC_PACKAGE_IDENTIFIER="$2"
      shift 2
      ;;
    --notarize|--mac-notarize)
      MAC_NOTARIZE=1
      shift
      ;;
    --notary-profile=*)
      NOTARYTOOL_PROFILE="${1#*=}"
      shift
      ;;
    --notary-profile)
      NOTARYTOOL_PROFILE="$2"
      shift 2
      ;;
    --apple-id=*)
      APPLE_ID="${1#*=}"
      shift
      ;;
    --apple-id)
      APPLE_ID="$2"
      shift 2
      ;;
    --team-id=*)
      APPLE_TEAM_ID="${1#*=}"
      shift
      ;;
    --team-id)
      APPLE_TEAM_ID="$2"
      shift 2
      ;;
    --app-password-env=*)
      APP_PASSWORD_ENV="${1#*=}"
      shift
      ;;
    --app-password-env)
      APP_PASSWORD_ENV="$2"
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

if is_truthy "$MAC_NOTARIZE"; then
  MAC_SIGN=1
fi

if [[ -z "$ICON_PATH" ]]; then
  case "$OS_NAME" in
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

if ! MVN_BIN="$(resolve_executable "$MVN_CMD" mvn /opt/homebrew/bin/mvn /usr/local/bin/mvn)"; then
  echo "Error: Maven is required but was not found. Checked: PATH, /opt/homebrew/bin/mvn, /usr/local/bin/mvn." >&2
  exit 1
fi

if ! JPACKAGE_BIN="$(resolve_executable "$JPACKAGE_CMD" jpackage /usr/bin/jpackage)"; then
  echo "Error: jpackage is required but not found on PATH." >&2
  echo "Install/use a JDK that includes jpackage (JDK 14+)." >&2
  exit 1
fi

if (is_truthy "$MAC_SIGN" || is_truthy "$MAC_NOTARIZE") && [[ "$OS_NAME" != "Darwin" ]]; then
  echo "Error: macOS signing/notarization options can only be used on macOS." >&2
  exit 2
fi

if is_truthy "$MAC_NOTARIZE"; then
  if ! command -v xcrun >/dev/null 2>&1; then
    echo "Error: xcrun is required for macOS notarization." >&2
    exit 1
  fi
  if ! xcrun notarytool --help >/dev/null 2>&1; then
    echo "Error: xcrun notarytool is required for notarization." >&2
    exit 1
  fi
fi

if [[ "$PACKAGE_TYPE" != "app-image" && "$PACKAGE_TYPE" != "dmg" && "$PACKAGE_TYPE" != "pkg" && \
      "$PACKAGE_TYPE" != "exe" && "$PACKAGE_TYPE" != "msi" && "$PACKAGE_TYPE" != "deb" && \
      "$PACKAGE_TYPE" != "rpm" ]]; then
  echo "Error: Unsupported package type '$PACKAGE_TYPE'." >&2
  usage
  exit 2
fi

echo "Building application jars with Maven..."
echo "Using Maven: $MVN_BIN"
"$MVN_BIN" -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

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
  "$JPACKAGE_BIN"
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

if [[ "$OS_NAME" == "Darwin" && -n "$MAC_PACKAGE_IDENTIFIER" ]]; then
  jpackage_cmd+=(--mac-package-identifier "$MAC_PACKAGE_IDENTIFIER")
fi

if is_truthy "$MAC_SIGN"; then
  echo "macOS signing enabled."
  jpackage_cmd+=(--mac-sign)
  if [[ -n "$MAC_SIGN_IDENTITY" ]]; then
    echo "Using signing identity: $MAC_SIGN_IDENTITY"
    jpackage_cmd+=(--mac-signing-key-user-name "$MAC_SIGN_IDENTITY")
  fi
  if [[ -n "$MAC_SIGN_KEYCHAIN" ]]; then
    if [[ ! -f "$MAC_SIGN_KEYCHAIN" ]]; then
      echo "Error: macOS signing keychain not found: $MAC_SIGN_KEYCHAIN" >&2
      exit 2
    fi
    jpackage_cmd+=(--mac-signing-keychain "$MAC_SIGN_KEYCHAIN")
  fi
fi

echo "Running jpackage (type=$PACKAGE_TYPE, name=$APP_NAME)..."
"${jpackage_cmd[@]}"

if is_truthy "$MAC_NOTARIZE"; then
  artifact_path="$(resolve_artifact_path || true)"
  if [[ -z "${artifact_path:-}" ]]; then
    echo "Error: unable to locate packaged artifact for notarization in $DEST_DIR" >&2
    exit 1
  fi
  notarize_artifact "$artifact_path"
fi

echo "Packaging complete."
echo "Output directory: $DEST_DIR"
