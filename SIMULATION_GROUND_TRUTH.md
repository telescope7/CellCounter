# Cell Simulation Ground Truth Outputs

The Java simulator (`com.prolymphname.cellcounter.simulation.CellSimulationGUI`) generates a synthetic rolling-cell video and ground-truth files for future automated tracking evaluation.

## Generated Files

Given output base name `<base>`:

- `<base>.mp4` (or `.avi` fallback): simulated microscopy-style video.
- `<base>.csv`: compatibility CSV matching the original Python script schema.
  - Columns: `cell_id,insertion_time_sec,insertion_frame,last_visible_time_sec,last_visible_frame`
  - Note: `last_visible_*` currently represents the frame/time when the cell crosses 80% of frame width (same behavior as original script).
- `<base>_events.csv`: canonical per-cell ground truth.
  - Columns:
    - `cell_id`
    - `insertion_time_sec`
    - `insertion_frame`
    - `cross_80_time_sec`
    - `cross_80_frame`
    - `exit_time_sec`
    - `exit_frame`
    - `entry_y_px`
    - `true_velocity_px_per_frame`
    - `velocity_scale`
- `<base>_trajectory.csv`: per-frame visible trajectory ground truth.
  - Columns: `frame,time_sec,cell_id,x_px,y_px,cell_radius_px,crossed_80pct`
- `<base>_manifest.json`: metadata + parameters + absolute paths, schema `cellcounter.simulation.groundtruth.v1`.

## Future Test Harness Direction

Use these files to compare CellCounter output with known truth:

1. Run simulator to generate video and manifest.
2. Run CellCounter on generated video.
3. Join predicted tracks with `events`/`trajectory` truth by nearest spatiotemporal match.
4. Compute objective metrics:
   - Precision/recall for detections
   - ID switch count
   - Track fragmentation
   - Velocity MAE vs `true_velocity_px_per_frame`
   - Crossing-frame error vs `cross_80_frame`
5. Optimize tracking configuration against these metrics.

The manifest file is intended to be the entry point for this future automated workflow.

## Running

Use either:

1. Main CellCounter GUI: click `Simulator` in the upper-right header.
2. Standalone simulator GUI:
   - `mvn -DskipTests exec:java -Dexec.mainClass=com.prolymphname.cellcounter.simulation.CellSimulationGUI`
3. CLI generator for automation:
   - `mvn -DskipTests exec:java -Dexec.mainClass=com.prolymphname.cellcounter.simulation.CellSimulationCli -Dexec.args="--outputBase=simcell --outputDir=/tmp --seed=42"`

If OpenCV native library loading fails at runtime, configure your local Java/OpenCV native library path the same way you do for `CellCounterApp`.

## Automated Evaluation CLI

Run end-to-end evaluation against a generated truth set:

```bash
mvn -DskipTests exec:java \
  -Dexec.mainClass=com.prolymphname.cellcounter.evaluation.GroundTruthEvaluationCli \
  -Dexec.args="--video=/path/to/simcell.avi --truth-events=/path/to/simcell_events.csv --output-prefix=/tmp/eval_run --tracking-config=/path/to/tracking-config.properties"
```

Outputs:
- CellCounter analysis CSV at `<output-prefix>_analysis.csv`
- CellCounter footprint CSV at `<output-prefix>_footprint.csv`
- Evaluation metrics CSV at `<output-prefix>_evaluation_metrics.csv`

Reported minimal metric set:
- `F1`
- `W1_time (sec)`
- `W1_velocity (px/sec)`
- `MAE_velocity (px/sec)`
