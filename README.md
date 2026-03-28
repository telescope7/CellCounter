# CellCounter

CellCounter is a Java desktop application for rolling-cell analysis in biomaterials workflows. The project now includes:

- an interactive Swing GUI with dual video panes
- a shared headless analysis path for batch processing
- a simulator for generating synthetic rolling-cell videos and companion CSV outputs
- packaging support for desktop distribution, including macOS DMG, signing, and notarization

## Core Capabilities

### GUI analysis

The GUI is designed for side-by-side review of the same frame in two perspectives:

- `Raw Input + Tracks`: the raw microscopy frame
- `Foreground + Tracks`: the background-subtracted diagnostic view with tracking overlays

The foreground pane is the primary tuning surface. The raw pane remains clean by default and can mirror the rich tracking overlays when `Mirror Tracking` is enabled.

### Runtime settings

The `Settings` control opens a compact inline settings bar directly in the main window.

Important behavior:

- `Settings` can be opened even before a video is loaded
- before a video is loaded, values can still be changed and applied for the session
- preview rendering becomes available automatically once a video is loaded
- startup defaults come from `CellCounter.properties`

## Requirements

### Build/runtime

- JDK 17+
- Maven
- OpenCV Java runtime available through the project runtime path or packaged build
- `jpackage` from the active JDK for native packaging

### macOS packaging/signing

- macOS
- `jpackage`
- `hdiutil`
- `codesign`
- `xcrun`
- `xcrun notarytool` for notarization
- Apple Developer account and Developer ID Application certificate for signed distribution

## Configuration Defaults

`CellCounter.properties` is the required default source for startup tracking parameters.

That file is used by:

- GUI startup
- headless startup
- code paths that request `TrackingConfiguration.defaults()`

If `CellCounter.properties` is missing or malformed, startup now fails fast instead of silently falling back to hardcoded defaults.

Current default properties in the repository:

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
confidenceFieldWidthPercent=60
rightEdgeExitZonePercent=5
trackerAlgorithm=GREEDY
```

## Running From Source

### Compile

```bash
mvn -Dmaven.repo.local=.m2/repository -DskipTests compile
```

### Run tests

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

### Launch the GUI with Maven

```bash
mvn -Dmaven.repo.local=.m2/repository -DskipTests exec:java
```

### Launch using the project script

```bash
./run.sh
```

## Standard GUI Workflow

1. Launch the app.
2. Optionally open `Settings` before loading a video and preconfigure the session.
3. Click `Open Video`.
4. Review the dual video panes.
5. Adjust `Playback Speed` if needed.
6. Open `Settings` while paused to refine segmentation and tracking parameters.
7. Run `Play/Analyze` for live analysis or `Fast Analyze` for a quicker full pass.
8. Save both CSV outputs with `Save Results`.

### Major keyboard shortcuts

- `O`: Open Video
- `F`: Fast Analyze
- `P` or `K`: Play/Pause toggle
- `Esc`: Pause only
- `.` or `Right Arrow`: Single-frame step
- hold `Space`: Repeated frame stepping
- `R`: Replay/reset
- `S`: Save Results
- `T`: Show/hide Settings
- `H`: Open Help

## Headless Processing

The shared analysis engine is also available in headless mode.

### Basic run

```bash
mvn -Dmaven.repo.local=.m2/repository -DskipTests exec:java \
  -Dexec.args="/path/to/video.avi /tmp/output_prefix"
```

### Common headless options

- `--tracking-config=<file.properties>`
- any individual tracking override such as `--maxFramesDisappeared=...`
- metadata such as `--cellType=... --substrate=... --flow=...`

CLI options override the defaults from `CellCounter.properties`.

## Testing Strategy

The repository includes focused JUnit coverage for key non-UI seams:

- assignment strategies
- tracking configuration normalization/default loading
- chart refresh cadence
- track metrics and visualization geometry helpers
- CSV export behavior

Recommended local validation before packaging:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

## Packaging The Application

Packaging is handled by `package-app.sh`.

The script automatically:

- discovers Maven from `PATH`, `/opt/homebrew/bin/mvn`, or `/usr/local/bin/mvn`
- builds the project jar and runtime dependencies
- bundles `CellCounter.properties`
- bundles the HTML help set
- bundles icon assets
- uses `jpackage` to create native deliverables

### Common package commands

Unsigned app image:

```bash
./package-app.sh --type=app-image
```

Unsigned DMG:

```bash
./package-app.sh --type=dmg
```

Custom destination:

```bash
./package-app.sh --type=dmg --dest=packaging/dist
```

### Supported types

```text
app-image, dmg, pkg, exe, msi, deb, rpm
```

## Installing The DMG

1. Double-click the generated `.dmg`
2. Drag `CellCounter.app` into `Applications`
3. Launch from `Applications`

Or from Terminal:

```bash
open packaging/dist/CellCounter-1.0.dmg
```

## macOS Signing

Signed DMG:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter"
```

Signed app image:

```bash
./package-app.sh \
  --type=app-image \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter"
```

Optional signing keychain:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-sign-keychain "$HOME/Library/Keychains/login.keychain-db"
```

## macOS Notarization

If `--notarize` is provided, the script automatically enables signing.

### Recommended: store credentials once with notarytool

```bash
xcrun notarytool store-credentials cellcounter-notary \
  --apple-id "you@example.com" \
  --team-id "TEAMID" \
  --password "app-specific-password"
```

Then run:

```bash
./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter" \
  --notarize \
  --notary-profile cellcounter-notary
```

### Alternative: Apple ID + env var password

```bash
export APPLE_APP_SPECIFIC_PASSWORD="your-app-specific-password"

./package-app.sh \
  --type=dmg \
  --mac-sign \
  --mac-sign-identity "Developer ID Application: Your Name (TEAMID)" \
  --mac-package-identifier "com.prolymphname.cellcounter" \
  --notarize \
  --apple-id "you@example.com" \
  --team-id "TEAMID"
```

## Help Documentation

The in-app `Help` link opens the local HTML documentation set in `docs/help`.

That documentation covers:

- GUI workflow
- button/control reference
- configuration semantics
- tuning strategy
- troubleshooting and packaging notes

## Project Notes

- The raw pane is clean by default; `Mirror Tracking` copies the rich diagnostic overlays from the foreground pane into the raw pane.
- `Settings` can be used before a video is loaded, but preview rendering requires an initialized video.
- The frame progress display is now text-only (`Frame: X/Y`) rather than a scrubber.
