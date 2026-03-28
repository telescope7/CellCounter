# CellCounter

CellCounter is a Java desktop application for rolling-cell tracking and export. It includes:

- a modern Swing GUI for interactive analysis
- a headless CLI mode for batch processing
- a simulator for generating synthetic videos and ground-truth CSVs
- evaluation and genetic-algorithm tuning CLIs for comparing tracking output against truth data
- macOS packaging support, including DMG creation and optional signing/notarization

## Requirements

### Core build/runtime

- JDK 17+
- Maven
- `jpackage` available from the active JDK

### macOS packaging

- macOS
- `jpackage`
- `hdiutil` (built into macOS)

### macOS signing/notarization

- Apple Developer account
- Developer ID Application certificate installed in Keychain
- `xcrun`
- `codesign`
- `xcrun notarytool`

## Configuration Defaults

`CellCounter.properties` is the required default source for startup tracking parameters.

- GUI startup loads tracking defaults from [`CellCounter.properties`](CellCounter.properties)
- headless startup also loads defaults from the same file
- code paths that request `TrackingConfiguration.defaults()` now resolve through that file

If `CellCounter.properties` is missing or malformed, the application now fails fast instead of silently falling back to hardcoded tracking defaults.

Current default properties:

```properties
startupSplashSeconds=1.5
maxFramesDisappeared=7
minContourArea=15
maxRectCircumference=219
maxVerticalDisplacementPixels=10
minHorizontalMovementPixels=-2
maxAssociationDistancePixels=109
mog2HistoryFrames=200
mog2VarThreshold=17
mog2DetectShadows=false
morphologyKernelSize=5
morphologyOpenIterations=1
morphologyDilateIterations=1
normalizedMaskThreshold=19
trackerAlgorithm=GREEDY
```

## Running From Source

### Build

```bash
/opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository -DskipTests compile
```

### Run tests

```bash
/opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository test
```

### Run the GUI with Maven

```bash
/opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository -DskipTests exec:java
```

### Run using the existing script

The project also includes [`run.sh`](run.sh), which launches the GUI with an explicit Java path and `java.library.path` setup.

```bash
./run.sh
```

## Headless Processing

`CellCounterApp` also supports headless mode. The simplest project-local way to run it is through Maven:

```bash
/opt/homebrew/bin/mvn -Dmaven.repo.local=.m2/repository -DskipTests exec:java \
  -Dexec.args="/path/to/video.avi /tmp/output_prefix"
```

You can also provide:

- `--tracking-config=<file.properties>`
- any individual tracking option such as `--maxFramesDisappeared=...`
- metadata like `--cellType=... --substrate=... --flow=...`

CLI tracking options override the defaults from `CellCounter.properties`.

## Ground-Truth Evaluation

### Evaluate a tracking configuration against simulator truth

```bash
./run-ground-truth-eval.sh \
  --video=/path/to/simcell.avi \
  --truth-events=/path/to/simcell_events.csv \
  --output-prefix=/tmp/eval_run \
  --tracking-config=/path/to/tracking-config.properties \
  --score-baseline-config=/path/to/baseline-config.properties
```

Outputs include:

- `<output-prefix>_analysis.csv`
- `<output-prefix>_footprint.csv`
- `<output-prefix>_evaluation_metrics.csv`

### Run GA tuning

```bash
./run-ground-truth-ga.sh \
  --video=/path/to/simcell.avi \
  --truth-events=/path/to/simcell_events.csv \
  --tracking-config=/path/to/tracking-config.properties \
  --generations=8 \
  --population=16 \
  --mutation-rate=0.15 \
  --max-timeout-sec=45 \
  --output-dir=/tmp/ga_run
```

Outputs include:

- `ga_best_tracking_config.properties`
- `ga_history.csv`
- `ga_report.txt`

## Packaging The Application

Packaging is handled by [`package-app.sh`](package-app.sh).

The script now:

- auto-discovers Maven from:
  - `PATH`
  - `/opt/homebrew/bin/mvn`
  - `/usr/local/bin/mvn`
- uses `jpackage`
- bundles:
  - app jars and runtime dependencies
  - `CellCounter.properties`
  - help HTML
  - icon assets

### Basic package commands

Unsigned app image:

```bash
./package-app.sh --type=app-image
```

Unsigned DMG:

```bash
./package-app.sh --type=dmg
```

Custom output directory:

```bash
./package-app.sh --type=dmg --dest=packaging/dist
```

### Supported package types

```text
app-image, dmg, pkg, exe, msi, deb, rpm
```

### Typical macOS output

After a DMG build, the artifact will appear in the chosen output directory, for example:

```text
packaging/dist/CellCounter-1.0.dmg
```

## Installing The DMG

On macOS:

1. Double-click the generated `.dmg`
2. Drag `CellCounter.app` into `Applications`
3. Launch from `Applications`

You can also open the DMG directly from Terminal:

```bash
open packaging/dist/CellCounter-1.0.dmg
```

## macOS Signing

The packaging script supports optional code signing through `jpackage`.

### Signed DMG

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter"
```

### Signed app image

```bash
./package-app.sh \
  --type=app-image \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter"
```

### Optional signing keychain

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-sign-keychain "$HOME/Library/Keychains/login.keychain-db"
```

## macOS Notarization

The script supports optional notarization and stapling.

Important:

- notarization requires signing
- if `--notarize` is provided, the script automatically enables `--mac-sign`
- for `.app` notarization, the script creates a zip for submission and then staples the `.app`
- for `.dmg`, the script submits and staples the DMG directly

### Recommended: store credentials with notarytool

Create a reusable keychain profile once:

```bash
xcrun notarytool store-credentials cellcounter-notary \
  --apple-id "you@example.com" \
  --team-id "TEAMID" \
  --password "app-specific-password"
```

Then build, sign, notarize, and staple:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter" \
  --notarize \
  --notary-profile cellcounter-notary
```

### Alternate: provide Apple ID / Team ID and password env var

Set an app-specific password in an environment variable:

```bash
export APPLE_APP_SPECIFIC_PASSWORD="your-app-specific-password"
```

Then run:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter" \
  --notarize \
  --apple-id "you@example.com" \
  --team-id "TEAMID"
```

If needed, you can change the env var name the script reads:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --notarize \
  --apple-id "you@example.com" \
  --team-id "TEAMID" \
  --app-password-env MY_APPLE_PASSWORD
```

## Packaging Script Options

Run:

```bash
./package-app.sh --help
```

Key options:

- `--type=<app-image|dmg|pkg|exe|msi|deb|rpm>`
- `--name=<AppName>`
- `--dest=<outputDir>`
- `--input-dir=<stagingDir>`
- `--icon=<path>`
- `--mac-sign`
- `--mac-sign-identity=<name>`
- `--mac-sign-keychain=<path>`
- `--mac-package-identifier=<id>`
- `--notarize`
- `--notary-profile=<profile>`
- `--apple-id=<id>`
- `--team-id=<id>`
- `--app-password-env=<ENV_NAME>`

Environment overrides:

- `APP_NAME`
- `PACKAGE_TYPE`
- `DEST_DIR`
- `INPUT_DIR`
- `ICON_PATH`
- `MVN_CMD`
- `JPACKAGE_CMD`
- `MAC_SIGN`
- `MAC_SIGN_IDENTITY`
- `MAC_SIGN_KEYCHAIN`
- `MAC_PACKAGE_IDENTIFIER`
- `MAC_NOTARIZE`
- `NOTARYTOOL_PROFILE`
- `APPLE_ID`
- `APPLE_TEAM_ID`
- `APP_PASSWORD_ENV`

## Notes

- The unsigned packaging path has been validated locally for both `app-image` and `dmg`.
- Signed/notarized packaging requires your real Apple signing identity and notarization credentials.
- If Gatekeeper warnings matter for distribution beyond your own machine, use the signed + notarized DMG flow.

## Additional Documentation

- Simulation and ground-truth outputs: [`SIMULATION_GROUND_TRUTH.md`](SIMULATION_GROUND_TRUTH.md)
- In-app help HTML: `docs/help/`
