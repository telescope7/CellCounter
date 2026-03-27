package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.analysis.DetectionFrameResult;
import com.prolymphname.cellcounter.analysis.DisplayFrameRenderer;
import com.prolymphname.cellcounter.analysis.ForegroundDetectionPipeline;
import com.prolymphname.cellcounter.analysis.TrackOverlayVisualPolicy;
import com.prolymphname.cellcounter.analysis.TrackVisualState;
import com.prolymphname.cellcounter.analysis.TrackedOverlay;
import com.prolymphname.cellcounter.trackingadapter.AssignmentStrategy;
import com.prolymphname.cellcounter.trackingadapter.GreedyAssignmentStrategy;
import com.prolymphname.cellcounter.trackingadapter.HungarianAssignmentStrategy;
import com.prolymphname.cellcounter.trackingadapter.TrackedCell;
import com.prolymphname.cellcounter.trackingadapter.TrackedCellHistoryEntry;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;

import java.util.*;

public class AnalysisLogic {

	// Fields from previous versions (ensure they are present)
	private VideoCapture cap;

	private boolean captureActive = false;
	private boolean videoSuccessfullyInitialized = false;

	private int frameNumber = 0;
	private double fps = 30;
	private Mat lastProcessedFrame;
	private BackgroundSubtractorMOG2 fgbg;
	private CentroidTracker cellTracker;
	private String videoFilename;
	private Mat referenceFrame = null;
	private boolean displayMOG2Foreground = false;
	private Mat currentRawFrameForDisplay = null;
	private Mat lastForegroundMaskForDisplay = null;
	private boolean displayTrackTrails = true;
	private boolean displayMatchRegion = true;
	private TrackingConfiguration trackingConfiguration = TrackingConfiguration.defaults();
	private final ForegroundDetectionPipeline foregroundDetectionPipeline = new ForegroundDetectionPipeline();
	private final DisplayFrameRenderer displayFrameRenderer = new DisplayFrameRenderer();
	private final TrackOverlayVisualPolicy trackOverlayVisualPolicy = new TrackOverlayVisualPolicy();

	private List<Double> trackStartTimes = new ArrayList<>(); // Renamed from crossingTimes
	private List<Double> speeds = new ArrayList<>();
	private static final int MAX_TRAIL_POINTS = 8;

	public static class HistoryItem {
		public int frame;
		public Point UL;
		public Point LR;

		public HistoryItem(int frame, Point UL, Point LR) {
			this.frame = frame;
			this.UL = UL;
			this.LR = LR;
		}
	}

	public static class Track {
		public Point centroid;
		public Rect bbox;
		public double startTime; // Time of first detection
		public int startFrame; // Frame of first detection
		public List<HistoryItem> history = new ArrayList<>();
		public int missed; // Consecutive frames missed
		public double instantSpeed = 0.0;
		public Point previousCentroidForSpeed = null;
		public boolean isNewTrack = true; // For coloring bounding box
	}

	public static class CentroidTracker {
		public int nextObjectID = 0;
		public Map<Integer, Track> objects = new HashMap<>();
		public Map<Integer, Integer> disappeared = new HashMap<>(); // Stores consecutive disappearance count
		public int maxDisappeared; // Max consecutive frames an object can disappear before being deregistered
		public Map<Integer, Track> completeTracks = new HashMap<>();
		private AnalysisLogic outer; // To access trackStartTimes and FPS
		private final double maxVerticalDisplacementPixels;
		private final double minHorizontalMovementPixels;
		private final double maxAssociationDistanceSq;
		private final AssignmentStrategy assignmentStrategy;

		public CentroidTracker(int maxDisappearedFrames,
							   double maxVerticalDisplacementPixels,
							   double minHorizontalMovementPixels,
							   double maxAssociationDistancePixels,
							   AssignmentStrategy assignmentStrategy,
							   AnalysisLogic outer) {
			this.maxDisappeared = maxDisappearedFrames; // If not updated for 'maxDisappearedFrames', it's removed
			this.maxVerticalDisplacementPixels = maxVerticalDisplacementPixels;
			this.minHorizontalMovementPixels = minHorizontalMovementPixels;
			this.maxAssociationDistanceSq = maxAssociationDistancePixels * maxAssociationDistancePixels;
			this.assignmentStrategy = assignmentStrategy;
			this.outer = outer;
		}

		public void register(Point centroid, Rect bbox, double currentTime, int frameNumber) {
			Track track = new Track();
			track.centroid = centroid;
			track.bbox = bbox;
			track.startTime = currentTime;
			track.startFrame = frameNumber;
			track.isNewTrack = true;
			track.previousCentroidForSpeed = new Point(centroid.x, centroid.y); // Initialize for speed calc
			track.instantSpeed = 0.0;

			outer.trackStartTimes.add(currentTime); // Add start time to the main list

			track.history.add(new HistoryItem(frameNumber, new Point(bbox.x, bbox.y),
					new Point(bbox.x + bbox.width, bbox.y + bbox.height)));
			track.missed = 0;

			objects.put(nextObjectID, track);
			disappeared.put(nextObjectID, 0); // Reset disappearance count
			nextObjectID++;
		}

		public void deregister(int objectID) {
			Track track = objects.get(objectID);
			if (track != null) {
				// Potentially calculate final track metrics here if needed before moving
				completeTracks.put(objectID, track);
				//System.out.println("Deregistering track ID " + objectID + " after " + track.missed + " missed frames.");
			}
			objects.remove(objectID);
			disappeared.remove(objectID);
		}

		public void update(List<Rect> rects, double currentTime, int frameNumber, double currentFps) {
			// If no detections, increment disappeared count for all tracks
			if (rects.isEmpty()) {
				List<Integer> objectIDsList = new ArrayList<>(objects.keySet());
				for (Integer objectID : objectIDsList) {
					Track track = objects.get(objectID);
					track.missed++;
					disappeared.put(objectID, disappeared.get(objectID) + 1);
					track.instantSpeed = 0.0; // No movement if not seen
					if (disappeared.get(objectID) >= maxDisappeared) { // Use >= as per discussion
						deregister(objectID);
					}
				}
				return;
			}

			List<Point> inputCentroids = new ArrayList<>();
			for (Rect r : rects) {
				inputCentroids.add(new Point(r.x + r.width / 2.0, r.y + r.height / 2.0));
			}

			// If no current tracks, register all new detections
			if (objects.isEmpty()) {
				for (int i = 0; i < inputCentroids.size(); i++) {
					register(inputCentroids.get(i), rects.get(i), currentTime, frameNumber);
				}
				return;
			}

			List<Integer> objectIDsList = new ArrayList<>(objects.keySet());
			List<Point> currentTrackCentroids = new ArrayList<>();
			for (Integer objectID : objectIDsList) {
				currentTrackCentroids.add(objects.get(objectID).centroid);
			}

			// Build distance matrix D[track][detection].
			// D[i][j] = squared Euclidean distance if the pair satisfies all movement
			// constraints; Double.MAX_VALUE otherwise (forbidden).
			double[][] D = new double[currentTrackCentroids.size()][inputCentroids.size()];
			for (int i = 0; i < currentTrackCentroids.size(); i++) {
				Point trackCentroid = currentTrackCentroids.get(i);
				for (int j = 0; j < inputCentroids.size(); j++) {
					Point detectionCentroid = inputCentroids.get(j);
					double deltaX = detectionCentroid.x - trackCentroid.x;
					double deltaY = detectionCentroid.y - trackCentroid.y;

					// Constraint 1: no significant leftward movement
					if (deltaX < minHorizontalMovementPixels) {
						D[i][j] = Double.MAX_VALUE;
						continue;
					}
					// Constraint 2: limited vertical drift
					if (Math.abs(deltaY) > maxVerticalDisplacementPixels) {
						D[i][j] = Double.MAX_VALUE;
						continue;
					}
					// Constraint 3: within maximum association radius
					double distSq = deltaX * deltaX + deltaY * deltaY;
					D[i][j] = (distSq > maxAssociationDistanceSq) ? Double.MAX_VALUE : distSq;
				}
			}

			// Delegate to the pluggable assignment strategy (greedy or Hungarian).
			int[] assignment = assignmentStrategy.assign(D);

			Set<Integer> usedDetectionIndices = new HashSet<>();

			for (int trackIdx = 0; trackIdx < objectIDsList.size(); trackIdx++) {
				int bestDetectionIdx = assignment[trackIdx];
				if (bestDetectionIdx != -1) {
					int objectID = objectIDsList.get(trackIdx);
					Track track = objects.get(objectID);
					Point newCentroid = inputCentroids.get(bestDetectionIdx);

					// Calculate instant speed
					if (track.previousCentroidForSpeed != null && currentFps > 0) {
						double dist = Math.hypot(newCentroid.x - track.previousCentroidForSpeed.x,
								newCentroid.y - track.previousCentroidForSpeed.y);
						track.instantSpeed = dist * currentFps;
					} else {
						track.instantSpeed = 0.0;
					}
					track.previousCentroidForSpeed = new Point(newCentroid.x, newCentroid.y);
					track.centroid = newCentroid;
					track.bbox = rects.get(bestDetectionIdx);
					track.history.add(new HistoryItem(frameNumber, new Point(track.bbox.x, track.bbox.y),
							new Point(track.bbox.x + track.bbox.width, track.bbox.y + track.bbox.height)));
					track.missed = 0;
					disappeared.put(objectID, 0);

					usedDetectionIndices.add(bestDetectionIdx);
				}
			}

			// Handle unmatched tracks
			for (int i = 0; i < objectIDsList.size(); i++) {
				if (assignment[i] == -1) {
					int objectID = objectIDsList.get(i);
					Track track = objects.get(objectID);
					track.missed++;
					disappeared.put(objectID, disappeared.get(objectID) + 1);
					track.instantSpeed = 0.0;
					if (disappeared.get(objectID) >= maxDisappeared) {
						deregister(objectID);
					}
				}
			}

			// Register new tracks from unmatched detections
			for (int j = 0; j < inputCentroids.size(); j++) {
				if (!usedDetectionIndices.contains(j)) {
					register(inputCentroids.get(j), rects.get(j), currentTime, frameNumber);
				}
			}
		}
	} // End CentroidTracker

	public AnalysisLogic() {
		rebuildTrackingPipeline();
	}

	private void rebuildTrackingPipeline() {
		TrackingConfiguration cfg = trackingConfiguration.normalized();
		this.trackingConfiguration = cfg;
		this.fgbg = Video.createBackgroundSubtractorMOG2(
				cfg.getMog2HistoryFrames(),
				cfg.getMog2VarThreshold(),
				cfg.isMog2DetectShadows());
		AssignmentStrategy strategy = switch (cfg.getTrackerAlgorithm()) {
			case HUNGARIAN -> new HungarianAssignmentStrategy();
			default -> new GreedyAssignmentStrategy();
		};
		this.cellTracker = new CentroidTracker(
				cfg.getMaxFramesDisappeared(),
				cfg.getMaxVerticalDisplacementPixels(),
				cfg.getMinHorizontalMovementPixels(),
				cfg.getMaxAssociationDistancePixels(),
				strategy,
				this);
	}

	public boolean initializeVideo(String videoPath) {
        releaseVideo();
        this.videoFilename = videoPath;
        this.cap = new VideoCapture(videoPath);

        if (!this.cap.isOpened()) { this.captureActive = false; this.videoSuccessfullyInitialized = false; return false; }
        this.fps = this.cap.get(Videoio.CAP_PROP_FPS);
        if (this.fps <= 0) this.fps = 30;

        this.videoSuccessfullyInitialized = true;
        resetStateAndPrepareFirstFrame();

        if (!this.captureActive || this.lastProcessedFrame == null || this.lastProcessedFrame.empty()) {
            releaseVideo(); return false;
        }
	        return true;
	    }

	public TrackingConfiguration getTrackingConfiguration() {
		return trackingConfiguration;
	}

	public void setTrackingConfiguration(TrackingConfiguration trackingConfiguration) {
		if (trackingConfiguration == null) {
			return;
		}
		this.trackingConfiguration = trackingConfiguration.normalized();
		if (this.videoSuccessfullyInitialized && this.cap != null && this.cap.isOpened()) {
			resetStateAndPrepareFirstFrame();
		} else {
			rebuildTrackingPipeline();
		}
	}


	public void setReferenceFrameForDiff(Mat frame) {
		if (frame != null) {
			this.referenceFrame = frame.clone();
		} else {
			this.referenceFrame = null;
		}
	}

	private void resetStateAndPrepareFirstFrame() {
		this.frameNumber = 0;
		if (this.cap != null && this.cap.isOpened()) {
			this.cap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
			this.captureActive = true;
		} else {
			this.captureActive = false;
			this.videoSuccessfullyInitialized = false;
			return;
		}

		rebuildTrackingPipeline();
		this.trackStartTimes.clear();
		this.speeds.clear();
		this.displayMOG2Foreground = false; // Default view
		if (this.lastForegroundMaskForDisplay != null) {
			this.lastForegroundMaskForDisplay.release();
			this.lastForegroundMaskForDisplay = null;
		}

		Mat firstFrameMat = new Mat();
		if (this.cap.read(firstFrameMat) && !firstFrameMat.empty()) {
			this.frameNumber = 1; // Processing frame 1
			if (this.currentRawFrameForDisplay != null)
				this.currentRawFrameForDisplay.release();
			this.currentRawFrameForDisplay = firstFrameMat.clone(); // Store raw

			Mat rotatedFirst = firstFrameMat;

			if (this.lastProcessedFrame != null)
				this.lastProcessedFrame.release();
			// processFrame will use frameNumber=1
			this.lastProcessedFrame = processFrame(rotatedFirst, true); // Process with overlays

			if (rotatedFirst != firstFrameMat && rotatedFirst != this.lastProcessedFrame)
				rotatedFirst.release();

			this.cap.set(Videoio.CAP_PROP_POS_FRAMES, 0); // Rewind
			this.frameNumber = 0; // Reset for next actual processing
		} else {
			System.err.println("Failed to read the first frame during resetStateAndPrepareFirstFrame.");
			this.captureActive = false;
			if (this.lastProcessedFrame != null)
				this.lastProcessedFrame.release();
			this.lastProcessedFrame = null;
			if (this.currentRawFrameForDisplay != null) {
				this.currentRawFrameForDisplay.release();
				this.currentRawFrameForDisplay = null;
			}
		}
		if (!firstFrameMat.empty() && firstFrameMat != this.currentRawFrameForDisplay)
			firstFrameMat.release();
	}

	public void resetAnalysisForCurrentVideo() {
		if (!this.videoSuccessfullyInitialized || this.cap == null || !this.cap.isOpened()) {
			System.err.println("Cannot reset analysis: Video not successfully initialized or capture is invalid.");
			this.captureActive = false;
			return;
		}
		resetStateAndPrepareFirstFrame();
	}

	public Mat processNextFrameForGUI() {
		if (!this.captureActive || this.cap == null || !this.cap.isOpened()) {
			if (this.cap == null || !this.cap.isOpened())
				this.captureActive = false;
			return null;
		}

		Mat rawFrame = new Mat();
		if (!this.cap.read(rawFrame) || rawFrame.empty()) {
			this.captureActive = false;
			rawFrame.release();
			return null;
		}
		this.frameNumber++;

		// Store the raw frame for potential display toggle refreshes
		if (this.currentRawFrameForDisplay != null) {
			this.currentRawFrameForDisplay.release();
		}
		this.currentRawFrameForDisplay = rawFrame.clone(); // Store raw frame clone

		Mat rotatedFrame = rawFrame; // rawFrame is used by rotateImage
										// rotateImage returns a new Mat if angle != 0
		if (rotatedFrame == null || rotatedFrame.empty()) {
			System.err.println("Error: Rotated frame is null or empty in processNextFrameForGUI.");
			rawFrame.release(); // rawFrame was cloned to currentRawFrameForDisplay
			if (this.currentRawFrameForDisplay != null) { // Clear stored raw if rotation failed badly
				this.currentRawFrameForDisplay.release();
				this.currentRawFrameForDisplay = null;
			}
			return null;
		}

		if (this.lastProcessedFrame != null) {
			this.lastProcessedFrame.release();
		}
		// processFrame gets the rotatedFrame. If rotateImage returned the same Mat
		// (angle 0),
		// it's the one cloned into currentRawFrameForDisplay. If it's new, rawFrame is
		// original.
		// processFrame will use the rotatedFrame and is responsible for its lifecycle
		// if it clones it.
		this.lastProcessedFrame = processFrame(rotatedFrame, true); // true for drawOverlays (though now internal to
																	// processFrame logic)

		// Release intermediate Mats: rawFrame was cloned, rotatedFrame was input to
		// processFrame
		// processFrame is expected to handle the lifecycle of rotatedFrame (e.g. by
		// cloning if needed for displayOutput)
		// If rotateImage created a new Mat for rotatedFrame, and processFrame also
		// clones it, then rotatedFrame needs release here.
		if (rotatedFrame != rawFrame) { // if rotateImage returned a new Mat
			rotatedFrame.release();
		}
		rawFrame.release(); // Original from cap.read(), now cloned to currentRawFrameForDisplay

		return this.lastProcessedFrame;
	}

	public Mat processNextFrameForAnalysis() { // Used by Fast Analyze / Headless
		if (!this.captureActive || this.cap == null || !this.cap.isOpened()) {
			if (this.cap == null || !this.cap.isOpened())
				this.captureActive = false;
			return null;
		}
		Mat frame = new Mat();
		if (!this.cap.read(frame) || frame.empty()) {
			this.captureActive = false;
			frame.release();
			return null;
		}
		this.frameNumber++;
		// For pure analysis, overlays might be skipped or minimal for speed
		// Assuming processFrame's boolean handles this, or make a separate
		// processFrameRaw()
		if (this.lastProcessedFrame != null)
			this.lastProcessedFrame.release();
		this.lastProcessedFrame = processFrame(frame, false); // Example: no overlays for headless
		frame.release();
		return this.lastProcessedFrame;
	}

	public Mat seekToFrameForGUI(int targetFrameIndex) {
		if (!this.videoSuccessfullyInitialized || this.cap == null || !this.cap.isOpened()) {
			return null;
		}

		int frameCount = getFrameCount();
		int lastFrameIndex = Math.max(0, frameCount - 1);
		int target = Math.max(0, Math.min(targetFrameIndex, lastFrameIndex));

		this.cap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
		this.frameNumber = 0;
		rebuildTrackingPipeline();
		this.trackStartTimes.clear();
		this.speeds.clear();

		if (this.lastForegroundMaskForDisplay != null) {
			this.lastForegroundMaskForDisplay.release();
			this.lastForegroundMaskForDisplay = null;
		}

		Mat rawFrame = new Mat();
		try {
			for (int i = 0; i <= target; i++) {
				if (!this.cap.read(rawFrame) || rawFrame.empty()) {
					this.captureActive = false;
					break;
				}
				this.frameNumber++;

				if (this.currentRawFrameForDisplay != null) {
					this.currentRawFrameForDisplay.release();
				}
				this.currentRawFrameForDisplay = rawFrame.clone();

				if (this.lastProcessedFrame != null) {
					this.lastProcessedFrame.release();
				}
				this.lastProcessedFrame = processFrame(rawFrame, true);
			}
		} finally {
			rawFrame.release();
		}

		this.captureActive = this.cap.isOpened() && this.frameNumber < frameCount;
		return this.lastProcessedFrame;
	}

	public void releaseVideo() {
		if (this.cap != null && this.cap.isOpened()) {
			this.cap.release();
		}
		this.cap = null; // Important to nullify
		this.captureActive = false;
		this.videoSuccessfullyInitialized = false; // Fully released
		this.videoFilename = null;
		this.frameNumber = 0;
		if (this.lastProcessedFrame != null) {
			this.lastProcessedFrame.release();
			this.lastProcessedFrame = null;
		}
		if (this.referenceFrame != null) {
			this.referenceFrame.release();
			this.referenceFrame = null;
		}
		if (this.trackStartTimes != null)
			this.trackStartTimes.clear();
		if (this.speeds != null)
			this.speeds.clear();
		if (this.currentRawFrameForDisplay != null) {
			this.currentRawFrameForDisplay.release();
			this.currentRawFrameForDisplay = null;
		}
		if (this.lastForegroundMaskForDisplay != null) {
			this.lastForegroundMaskForDisplay.release();
			this.lastForegroundMaskForDisplay = null;
		}

		System.out.println("Video resources released.");
	}

	public boolean isCaptureActive() {
		return this.captureActive;
	}

	public boolean isVideoSuccessfullyInitialized() {
		return this.videoSuccessfullyInitialized;
	}

	// Accessor for track start times (previously crossingTimes)
	public List<Double> getTrackStartTimes() {
		return Collections.unmodifiableList(trackStartTimes);
	}

	private Mat processFrame(Mat frameInput, boolean drawOverlaysCurrentlyUnused) {
		try (DetectionFrameResult detection = foregroundDetectionPipeline.detect(
				frameInput,
				referenceFrame,
				fgbg,
				trackingConfiguration)) {
			if (this.lastForegroundMaskForDisplay != null) {
				this.lastForegroundMaskForDisplay.release();
			}
			this.lastForegroundMaskForDisplay = detection.mask().clone();

			double currentTime = (double) this.frameNumber / this.fps;
			if (cellTracker != null) {
				cellTracker.update(detection.rects(), currentTime, this.frameNumber, this.fps);
			}

			recordObservedInstantSpeeds();

			Mat displayImage = renderDisplayFromCurrentState(frameInput, this.displayMOG2Foreground, detection.mask());
			markRenderedTracksAsExisting();
			return displayImage;
		}
	}

	public Mat previewCurrentFrameForTuning(TrackingConfiguration previewConfiguration, boolean showMaskView) {
		if (!videoSuccessfullyInitialized || videoFilename == null || videoFilename.isBlank()) {
			return null;
		}

		TrackingConfiguration cfg = (previewConfiguration == null ? trackingConfiguration : previewConfiguration).normalized();
		int targetFrameIndex = frameNumber <= 0 ? 0 : frameNumber - 1;
		int warmupFrames = Math.max(30, cfg.getMog2HistoryFrames());
		int startFrameIndex = Math.max(0, targetFrameIndex - warmupFrames);

		VideoCapture previewCap = new VideoCapture(videoFilename);
		if (!previewCap.isOpened()) {
			previewCap.release();
			return null;
		}

		BackgroundSubtractorMOG2 previewSubtractor = Video.createBackgroundSubtractorMOG2(
				cfg.getMog2HistoryFrames(),
				cfg.getMog2VarThreshold(),
				cfg.isMog2DetectShadows());

		Mat frame = new Mat();
		Mat fgmask = new Mat();
		Mat previewDisplay = null;

		try {
			if (startFrameIndex > 0) {
				previewCap.set(Videoio.CAP_PROP_POS_FRAMES, startFrameIndex);
			}

			int frameIndex = startFrameIndex;
			while (frameIndex <= targetFrameIndex && previewCap.read(frame)) {
				if (frame.empty()) {
					frameIndex++;
					continue;
				}

				try (DetectionFrameResult detection = foregroundDetectionPipeline.detect(
						frame,
						referenceFrame,
						previewSubtractor,
						cfg)) {
					if (frameIndex == targetFrameIndex) {
						previewDisplay = displayFrameRenderer.renderPreviewFrame(
								frame,
								detection.mask(),
								detection.rects(),
								showMaskView);
					}
				}
				frameIndex++;
			}

			if (previewDisplay == null && currentRawFrameForDisplay != null && !currentRawFrameForDisplay.empty()) {
				try (DetectionFrameResult detection = foregroundDetectionPipeline.detect(
						currentRawFrameForDisplay,
						referenceFrame,
						previewSubtractor,
						cfg)) {
					previewDisplay = displayFrameRenderer.renderPreviewFrame(
							currentRawFrameForDisplay,
							detection.mask(),
							detection.rects(),
							showMaskView);
				}
			}
		} finally {
			frame.release();
			fgmask.release();
			previewCap.release();
		}

		return previewDisplay;
	}

	// Add this new method to AnalysisLogic.java
	public void setDisplayMOG2Foreground(boolean show) {
		boolean changed = (this.displayMOG2Foreground != show);
		this.displayMOG2Foreground = show;

		if (changed) {
			rerenderCurrentDisplayFrame();
		}
	}

	public boolean isDisplayTrackTrailsEnabled() {
		return displayTrackTrails;
	}

	public void setDisplayTrackTrails(boolean show) {
		boolean changed = (this.displayTrackTrails != show);
		this.displayTrackTrails = show;
		if (changed) {
			rerenderCurrentDisplayFrame();
		}
	}

	public boolean isDisplayMatchRegionEnabled() {
		return displayMatchRegion;
	}

	public void setDisplayMatchRegion(boolean show) {
		boolean changed = (this.displayMatchRegion != show);
		this.displayMatchRegion = show;
		if (changed) {
			rerenderCurrentDisplayFrame();
		}
	}

	private void recordObservedInstantSpeeds() {
		if (cellTracker == null || cellTracker.objects == null) {
			return;
		}
		for (Track track : cellTracker.objects.values()) {
			if (track.centroid == null) {
				continue;
			}
			if (!Double.isNaN(track.instantSpeed) && !Double.isInfinite(track.instantSpeed)
					&& track.instantSpeed > 0 && track.missed == 0) {
				this.speeds.add(track.instantSpeed);
			}
		}
	}

	private Mat renderDisplayFromCurrentState(Mat sourceFrame, boolean showMaskView, Mat precomputedMask) {
		Mat maskForDisplay = precomputedMask;
		boolean releaseMaskForDisplay = false;
		if (maskForDisplay == null || maskForDisplay.empty()) {
			maskForDisplay = Mat.zeros(sourceFrame.size(), CvType.CV_8UC1);
			releaseMaskForDisplay = true;
		}
			try {
				return displayFrameRenderer.renderTrackedFrame(
						sourceFrame,
						showMaskView,
						maskForDisplay,
						buildTrackedOverlays(),
						displayTrackTrails,
						displayMatchRegion,
						trackingConfiguration.getMinHorizontalMovementPixels(),
						trackingConfiguration.getMaxVerticalDisplacementPixels(),
						trackingConfiguration.getMaxAssociationDistancePixels());
		} finally {
			if (releaseMaskForDisplay) {
				maskForDisplay.release();
			}
		}
	}

	private List<TrackedOverlay> buildTrackedOverlays() {
		List<TrackedOverlay> overlays = new ArrayList<>();
		if (cellTracker == null || cellTracker.objects == null) {
			return overlays;
		}
		List<Map.Entry<Integer, Track>> activeEntries = new ArrayList<>(cellTracker.objects.entrySet());
		boolean[] occlusionRisk = computeOcclusionRisk(activeEntries);
		for (int i = 0; i < activeEntries.size(); i++) {
			Map.Entry<Integer, Track> entry = activeEntries.get(i);
			Track track = entry.getValue();
			TrackVisualState state = trackOverlayVisualPolicy.resolveState(track.isNewTrack, track.missed);
			overlays.add(new TrackedOverlay(
					entry.getKey(),
					track.bbox,
					track.centroid,
					state,
					track.missed,
					occlusionRisk[i],
					buildTrailPoints(track)));
		}
		return overlays;
	}

	private boolean[] computeOcclusionRisk(List<Map.Entry<Integer, Track>> activeEntries) {
		boolean[] risk = new boolean[activeEntries.size()];
		double associationDistance = trackingConfiguration.getMaxAssociationDistancePixels();
		for (int i = 0; i < activeEntries.size(); i++) {
			for (int j = i + 1; j < activeEntries.size(); j++) {
				Track first = activeEntries.get(i).getValue();
				Track second = activeEntries.get(j).getValue();
				if (trackOverlayVisualPolicy.isOcclusionRisk(
						first.bbox,
						first.centroid,
						second.bbox,
						second.centroid,
						associationDistance)) {
					risk[i] = true;
					risk[j] = true;
				}
			}
		}
		return risk;
	}

	private List<Point> buildTrailPoints(Track track) {
		if (track == null || track.history == null || track.history.isEmpty()) {
			return List.of();
		}
		int startIndex = Math.max(0, track.history.size() - MAX_TRAIL_POINTS);
		List<Point> points = new ArrayList<>();
		for (int i = startIndex; i < track.history.size(); i++) {
			HistoryItem item = track.history.get(i);
			double centerX = (item.UL.x + item.LR.x) / 2.0;
			double centerY = (item.UL.y + item.LR.y) / 2.0;
			points.add(new Point(centerX, centerY));
		}
		if (track.centroid != null && (points.isEmpty() || !samePoint(points.get(points.size() - 1), track.centroid))) {
			points.add(new Point(track.centroid.x, track.centroid.y));
		}
		return points;
	}

	private boolean samePoint(Point first, Point second) {
		return first != null
				&& second != null
				&& Double.compare(first.x, second.x) == 0
				&& Double.compare(first.y, second.y) == 0;
	}

	private void rerenderCurrentDisplayFrame() {
		if (!videoSuccessfullyInitialized || currentRawFrameForDisplay == null || currentRawFrameForDisplay.empty()) {
			return;
		}

		Mat rotatedFrame = null;
		Mat rawCloneForRotation = currentRawFrameForDisplay.clone();
		try {
			rotatedFrame = rawCloneForRotation;
			if (rotatedFrame == null || rotatedFrame.empty()) {
				System.err.println("Error re-rendering current frame from display state.");
				if (this.lastProcessedFrame != null) {
					this.lastProcessedFrame.release();
				}
				this.lastProcessedFrame = null;
				return;
			}

			Mat newDisplayFrame = renderDisplayFromCurrentState(rotatedFrame, this.displayMOG2Foreground,
					this.lastForegroundMaskForDisplay);
			if (this.lastProcessedFrame != null) {
				this.lastProcessedFrame.release();
			}
			this.lastProcessedFrame = newDisplayFrame;
		} finally {
			rawCloneForRotation.release();
			if (rotatedFrame != null && rotatedFrame != rawCloneForRotation && rotatedFrame != this.lastProcessedFrame) {
				rotatedFrame.release();
			}
		}
	}

	private void markRenderedTracksAsExisting() {
		if (cellTracker == null || cellTracker.objects == null) {
			return;
		}
		for (Track track : cellTracker.objects.values()) {
			if (track.isNewTrack) {
				track.isNewTrack = false;
			}
		}
	}

	// Accessors
	public Mat getLastProcessedFrame() {
		return lastProcessedFrame;
	}

	public List<Double> getSpeeds() {
		return Collections.unmodifiableList(speeds);
	}

	public double getFps() {
		return fps;
	}

	public int getFrameCount() {
		return cap != null ? (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT) : 0;
	}

	public int getCurrentFrameNumber() {
		return frameNumber;
	}

	public List<TrackedCell> getTrackedCellsSnapshot() {
		if (cellTracker == null) {
			return List.of();
		}
		Map<Integer, Track> orderedTracks = new TreeMap<>();
		orderedTracks.putAll(cellTracker.objects);
		orderedTracks.putAll(cellTracker.completeTracks);

		List<TrackedCell> snapshot = new ArrayList<>(orderedTracks.size());
		for (Map.Entry<Integer, Track> entry : orderedTracks.entrySet()) {
			snapshot.add(toTrackedCell(entry.getKey(), entry.getValue()));
		}
		return snapshot;
	}

	private TrackedCell toTrackedCell(int cellId, Track track) {
		List<TrackedCellHistoryEntry> historyEntries = new ArrayList<>(track.history.size());
		for (HistoryItem item : track.history) {
			historyEntries.add(new TrackedCellHistoryEntry(
					item.frame,
					(int) item.UL.x,
					(int) item.UL.y,
					(int) item.LR.x,
					(int) item.LR.y));
		}
		return new TrackedCell(cellId, track.startFrame, track.startTime, track.missed, historyEntries);
	}

}
