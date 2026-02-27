package com.prolymphname.cellcounter;

import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;

import java.util.*;

public class AnalysisLogic {

	private static final int MAX_FRAMES_DISAPPEARED = 10;

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
	private double minContourArea = 5;
	private static final double MAX_RECT_CIRCUMFERENCE = 90.0;

	private List<Double> trackStartTimes = new ArrayList<>(); // Renamed from crossingTimes
	private List<Double> speeds = new ArrayList<>();

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

		// Movement constraints for matching
		private static final double MAX_VERTICAL_DISPLACEMENT_PIXELS = 40.0; // Tunable: Max allowed Y movement
		private static final double MIN_HORIZONTAL_MOVEMENT_PIXELS = -3.0; // Tunable: Max allowed backward X movement
																			// (negative for left, positive for
																			// tolerance if objects must move right)
																			// Set to 0 if must stay or move right. -5
																			// allows minor jitter left.
		private static final double MAX_ASSOCIATION_DISTANCE_SQ = 175.0 * 175.0; // Max Euclidean distance squared for
																				// association (tunable)

		public CentroidTracker(int maxDisappearedFrames, AnalysisLogic outer) {
			this.maxDisappeared = maxDisappearedFrames; // If not updated for 'maxDisappearedFrames', it's removed
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

			// Calculate distance matrix D between current tracks and new detections
			// D[i][j] is the squared Euclidean distance between track i and detection j
			double[][] D = new double[currentTrackCentroids.size()][inputCentroids.size()];
			for (int i = 0; i < currentTrackCentroids.size(); i++) {
				Point trackCentroid = currentTrackCentroids.get(i);

				for (int j = 0; j < inputCentroids.size(); j++) {
					Point detectionCentroid = inputCentroids.get(j);

					double deltaX = detectionCentroid.x - trackCentroid.x;
					double deltaY = detectionCentroid.y - trackCentroid.y;

					// Apply movement constraints
					// 1. No significant leftward movement
					if (deltaX < MIN_HORIZONTAL_MOVEMENT_PIXELS) {
						D[i][j] = Double.MAX_VALUE;
						continue;
					}
					// 2. Limited vertical drift
					if (Math.abs(deltaY) > MAX_VERTICAL_DISPLACEMENT_PIXELS) {
						D[i][j] = Double.MAX_VALUE;
						continue;
					}

					// 3. Calculate squared Euclidean distance (more efficient than sqrt for
					// comparison)
					double distSq = deltaX * deltaX + deltaY * deltaY;
					if (distSq > MAX_ASSOCIATION_DISTANCE_SQ) {
						D[i][j] = Double.MAX_VALUE;
					} else {
						D[i][j] = distSq;
					}
				}
			}

			// Greedy assignment:
			// Sort available tracks by their minimum valid distance to any detection
			// (Indices refer to objectIDsList and currentTrackCentroids)
			List<Integer> sortedTrackIndices = new ArrayList<>();
			for (int i = 0; i < currentTrackCentroids.size(); i++)
				sortedTrackIndices.add(i);

			// Custom sort: tracks with at least one valid (non-MAX_VALUE) match first, then
			// by min distance
			sortedTrackIndices.sort((idx1, idx2) -> {
				double minD1 = (D[idx1].length == 0) ? Double.MAX_VALUE
						: Arrays.stream(D[idx1]).min().orElse(Double.MAX_VALUE);
				double minD2 = (D[idx2].length == 0) ? Double.MAX_VALUE
						: Arrays.stream(D[idx2]).min().orElse(Double.MAX_VALUE);
				return Double.compare(minD1, minD2);
			});

			Set<Integer> usedTrackIndices = new HashSet<>(); // Indices from sortedTrackIndices / objectIDsList
			Set<Integer> usedDetectionIndices = new HashSet<>(); // Indices from inputCentroids / rects

			for (int trackIdx : sortedTrackIndices) {
				if (D[trackIdx].length == 0)
					continue; // No detections for this track (should not happen if inputCentroids not empty)

				int bestDetectionIdx = -1;
				double minDistanceSq = MAX_ASSOCIATION_DISTANCE_SQ; // Use the threshold as initial min

				for (int detectionIdx = 0; detectionIdx < D[trackIdx].length; detectionIdx++) {
					if (!usedDetectionIndices.contains(detectionIdx) && D[trackIdx][detectionIdx] < minDistanceSq) {
						minDistanceSq = D[trackIdx][detectionIdx];
						bestDetectionIdx = detectionIdx;
					}
				}

				if (bestDetectionIdx != -1) { // Found a valid match within constraints
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
					// Store for next frame's speed calculation
					track.previousCentroidForSpeed = new Point(newCentroid.x, newCentroid.y);
					track.centroid = newCentroid;
					track.bbox = rects.get(bestDetectionIdx);
					track.history.add(new HistoryItem(frameNumber, new Point(track.bbox.x, track.bbox.y),
							new Point(track.bbox.x + track.bbox.width, track.bbox.y + track.bbox.height)));
					track.missed = 0; // Reset missed counter
					disappeared.put(objectID, 0); // Reset disappeared counter

					usedTrackIndices.add(trackIdx);
					usedDetectionIndices.add(bestDetectionIdx);
				}
			}

			// Handle tracks that were not matched (increment disappeared count or
			// deregister)
			for (int i = 0; i < objectIDsList.size(); i++) {
				if (!usedTrackIndices.contains(i)) {
					int objectID = objectIDsList.get(i);
					Track track = objects.get(objectID);
					track.missed++;
					disappeared.put(objectID, disappeared.get(objectID) + 1);
					track.instantSpeed = 0.0; // No movement
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
		fgbg = Video.createBackgroundSubtractorMOG2(500, 10, false);
		// Pass 'this' to CentroidTracker to allow access to trackStartTimes
		cellTracker = new CentroidTracker(MAX_FRAMES_DISAPPEARED, this); // maxDisappeared = 40
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

		this.fgbg = Video.createBackgroundSubtractorMOG2(500, 50, false); // Re-init BGS
		this.cellTracker = new CentroidTracker(MAX_FRAMES_DISAPPEARED, this); // Re-init tracker, maxDisappeared=40
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
		Mat fgmask = new Mat();
        Mat sourceForBGS = frameInput; // frameInput is already a clone if necessary

        if (referenceFrame != null && !referenceFrame.empty()) {
            Mat diff = new Mat(); 
            Core.absdiff(frameInput, referenceFrame, diff); 
            sourceForBGS = diff;
        }
        if (fgbg != null) fgbg.apply(sourceForBGS, fgmask); else { Mat.zeros(frameInput.size(), CvType.CV_8UC1).copyTo(fgmask); }
        if (sourceForBGS != frameInput) sourceForBGS.release(); // Release diff if it was created

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3,3));
        Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_OPEN, kernel, new Point(-1,-1), 2);
        Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_DILATE, kernel, new Point(-1,-1), 2);
        kernel.release();

		Mat fgmaskForContours = fgmask.clone();
		List<MatOfPoint> contours = new ArrayList<>();
		Imgproc.findContours(fgmaskForContours, contours, new Mat(), Imgproc.RETR_EXTERNAL,
				Imgproc.CHAIN_APPROX_SIMPLE);
		fgmaskForContours.release();
		
		// Alternative: Histogram Stretching (Normalization)
		Core.normalize(fgmask, fgmask, 0, 255, Core.NORM_MINMAX);
		// IMPORTANT: Add thresholding after normalization
		// This threshold value (e.g., 150) is crucial and may need tuning.
		Imgproc.threshold(fgmask, fgmask, 150, 255, Imgproc.THRESH_BINARY);
		if (this.lastForegroundMaskForDisplay != null) {
			this.lastForegroundMaskForDisplay.release();
		}
		this.lastForegroundMaskForDisplay = fgmask.clone();

		List<Rect> rects = new ArrayList<>();
	    for (MatOfPoint c : contours) {
	        if (Imgproc.contourArea(c) < this.minContourArea) { //
	            c.release();
	            continue;
	        }
	        Rect r = Imgproc.boundingRect(c); //

	        // New check for rectangle circumference
	        double circumference = 2 * (r.width + r.height);
	        if (circumference > MAX_RECT_CIRCUMFERENCE) {
	            c.release(); // Release contour Mat
	            // r is a local variable, no need to release an OpenCV Rect object itself
	            continue; // Skip this rectangle
	        }

	        rects.add(r);
	        c.release(); //
	    }
	    contours.clear(); //
	    

		double currentTime = (double) this.frameNumber / this.fps;
		if (cellTracker != null) {
			cellTracker.update(rects, currentTime, this.frameNumber, this.fps);
		}

		if (cellTracker != null && cellTracker.objects != null) {
			for (Track track : cellTracker.objects.values()) {
				if (track.centroid == null)
					continue;
				// Crossing logic removed, speeds are added if valid from track.instantSpeed
				// if (!track.crossed && track.centroid.x >= finish_line_x) { ... } // REMOVED
				// The speeds list is populated directly when track.instantSpeed is
				// calculated/valid if needed for a graph
				// But the original request: "The speed calculation remains unchanged."
				// The `speeds` list in AnalysisLogic was populated when a cell crossed the
				// finish line.
				// Now, it needs a new trigger. Let's assume the "Speed Distribution" graph uses
				// the instantSpeed of all *active* tracks at each frame, or final average
				// speeds.
				// For simplicity, let's populate `speeds` with the `instantSpeed` of tracks
				// that are updated.
				// This might make the "Speed Distribution" graph very busy.
				// A better approach might be to collect final average speeds when tracks are
				// deregistered,
				// or collect all instantSpeeds and then histogram them.
				// For now, let's keep `speeds.add(track.instantSpeed)` in
				// CentroidTracker.update if a track is updated.
				// Or remove the direct `speeds.add` from `processFrame` and rely on
				// `getSpeeds()` to compute from tracks later.

				// Let's simplify: The `speeds` list will contain the `instantSpeed` for every
				// tracked object *every frame it's updated*.
				// This happens within CentroidTracker.update() if `track.instantSpeed` is
				// valid.
				// The old logic: `if (!Double.isNaN(track.instantSpeed) &&
				// !Double.isInfinite(track.instantSpeed)) { speeds.add(track.instantSpeed); }`
				// This was inside the `if (!track.crossed ...)` block.
				// Now, we need to decide when to add to the `speeds` list for the graph.
				// If the "Speed" graph is to show a distribution of all observed instant
				// speeds:
				if (!Double.isNaN(track.instantSpeed) && !Double.isInfinite(track.instantSpeed)
						&& track.instantSpeed > 0) {
					// Check if this track was just updated (not missed)
					if (track.missed == 0) { // Add speed if track was seen this frame
						this.speeds.add(track.instantSpeed);
					}
				}
			}
		}
		// The `trackStartTimes` list is populated in CentroidTracker.register().

		Mat displayImage = generateDisplayFromState(frameInput, this.displayMOG2Foreground, fgmask);
		fgmask.release();
		return displayImage;
	}
// } // End of AnalysisLogic class

	public Map<String, Object> computeMetricsForTrack(Track track) {
		Map<String, Object> metrics = new HashMap<>();
		// ... (calculate TotalDistance, AvgFrameDistance, MedianFrameDistance,
		// FramesTracked, FramesMissed, Speed as before)
		// Speed here could be average speed over track lifetime.
		// instantSpeed is per-frame.
		// For now, keep existing metric calculations that don't rely on finish line.

		List<HistoryItem> history = track.history;
		if (history.isEmpty()) {
			metrics.put("TotalDistance", 0.0);
			metrics.put("DistanceToCross", 0.0);
			metrics.put("DistanceAfterCross", 0.0);
			metrics.put("AvgFrameDistance", 0.0);
			metrics.put("MedianFrameDistance", 0.0);
			metrics.put("FramesTracked", 0);
			metrics.put("FramesMissed", track.missed);
			metrics.put("Speed", 0.0);
			metrics.put("Speed (Overall Avg)", 0.0);
			return metrics;
		}

		double totalDistance = 0;
		Point prevHistCentroid = null;
		if (!history.isEmpty()) {
			prevHistCentroid = new Point((history.get(0).UL.x + history.get(0).LR.x) / 2.0,
					(history.get(0).UL.y + history.get(0).LR.y) / 2.0);
			for (int i = 1; i < history.size(); i++) {
				Point currHistCentroid = new Point((history.get(i).UL.x + history.get(i).LR.x) / 2.0,
						(history.get(i).UL.y + history.get(i).LR.y) / 2.0);
				totalDistance += Math.hypot(currHistCentroid.x - prevHistCentroid.x,
						currHistCentroid.y - prevHistCentroid.y);
				prevHistCentroid = currHistCentroid;
			}
		}

		List<Double> moveDists = new ArrayList<>();
		if (history.size() > 1) {
			prevHistCentroid = new Point((history.get(0).UL.x + history.get(0).LR.x) / 2.0,
					(history.get(0).UL.y + history.get(0).LR.y) / 2.0);
			for (int i = 1; i < history.size(); i++) {
				Point currHistCentroid = new Point((history.get(i).UL.x + history.get(i).LR.x) / 2.0,
						(history.get(i).UL.y + history.get(i).LR.y) / 2.0);
				moveDists.add(
						Math.hypot(currHistCentroid.x - prevHistCentroid.x, currHistCentroid.y - prevHistCentroid.y));
				prevHistCentroid = currHistCentroid;
			}
		}

		double avgMove = moveDists.stream().mapToDouble(d -> d).average().orElse(0);
		double medianMove = 0;
		if (!moveDists.isEmpty()) {
			Collections.sort(moveDists);
			int mid = moveDists.size() / 2;
			medianMove = (moveDists.size() % 2 == 0) ? (moveDists.get(mid - 1) + moveDists.get(mid)) / 2.0
					: moveDists.get(mid);
		}

		int framesTracked = history.size();
		// Total time tracked based on frames and FPS
		double timeElapsedTracked = framesTracked > 0 ? (double) framesTracked / this.fps : 0;
		double overallSpeed = timeElapsedTracked > 0 ? totalDistance / timeElapsedTracked : 0;

		metrics.put("TotalDistance", totalDistance);
		metrics.put("DistanceToCross", 0.0);
		metrics.put("DistanceAfterCross", 0.0);
		metrics.put("AvgFrameDistance", avgMove);
		metrics.put("MedianFrameDistance", medianMove);
		metrics.put("FramesTracked", framesTracked);
		metrics.put("FramesMissed", track.missed);
		metrics.put("Speed", overallSpeed);
		metrics.put("Speed (Overall Avg)", overallSpeed); // Clarify this is overall average
		return metrics;
	}

	// Add this new method to AnalysisLogic.java
	public void setDisplayMOG2Foreground(boolean show) {
		boolean changed = (this.displayMOG2Foreground != show);
		this.displayMOG2Foreground = show;

		if (changed && videoSuccessfullyInitialized && currentRawFrameForDisplay != null
				&& !currentRawFrameForDisplay.empty()) {
			Mat rotatedFrame = null;
			Mat rawCloneForRotation = currentRawFrameForDisplay.clone(); // Clone before rotating

			try {
				rotatedFrame = rawCloneForRotation;
				if (rotatedFrame == null || rotatedFrame.empty()) {
					System.err.println(
							"Error rotating current raw frame for display toggle in setDisplayMOG2Foreground.");
					if (this.lastProcessedFrame != null)
						this.lastProcessedFrame.release();
					this.lastProcessedFrame = null; // Can't generate new frame
					return;
				}

				// generateDisplayFromState expects a Mat it can use or clone.
				// rotatedFrame here is a fresh Mat (or same as rawCloneForRotation if angle=0)
					Mat newDisplayFrame = generateDisplayFromState(rotatedFrame, this.displayMOG2Foreground,
							this.lastForegroundMaskForDisplay);

				if (this.lastProcessedFrame != null) {
					this.lastProcessedFrame.release();
				}
				this.lastProcessedFrame = newDisplayFrame; // newDisplayFrame takes ownership

			} finally {
				// rawCloneForRotation was the input to rotateImage, its lifecycle is managed by
				// rotateImage
				// (i.e., rotateImage returns a new Mat or the input if no rotation).
				// Here, rawCloneForRotation itself should be released as it was a clone.
				rawCloneForRotation.release();

				// rotatedFrame is either the same as rawCloneForRotation (if angle=0) or a new
				// Mat.
				// If it's new and not assigned to lastProcessedFrame (e.g., newDisplayFrame
				// took its content via clone),
				// it needs release. If newDisplayFrame *is* rotatedFrame, then it's fine.
				// generateDisplayFromState either clones rotatedFrame or uses a converted mask.
				// So, rotatedFrame (if it was newly created by rotateImage) can be released
				// here
				// *after* it has been used by generateDisplayFromState.
				if (rotatedFrame != null && rotatedFrame != rawCloneForRotation
						&& rotatedFrame != this.lastProcessedFrame) {
					rotatedFrame.release();
				}
			}
		}
	}

	private Mat generateDisplayFromState(Mat rotatedFrameInput, boolean showMaskView, Mat precomputedMask) {
		Mat displayOutput;
		Mat maskForDisplay = precomputedMask;
		boolean releaseMaskForDisplay = false;
		if (maskForDisplay == null || maskForDisplay.empty()) {
			maskForDisplay = Mat.zeros(rotatedFrameInput.size(), CvType.CV_8UC1);
			releaseMaskForDisplay = true;
		}

		Scalar newTrackColor = new Scalar(0, 0, 255); // Red for new
		Scalar existingTrackColor = new Scalar(0, 255, 0); // Green for existing

		if (showMaskView) {
			Mat bgrFgmask = new Mat();
			Imgproc.cvtColor(maskForDisplay, bgrFgmask, Imgproc.COLOR_GRAY2BGR);
			displayOutput = bgrFgmask;
			// Imgproc.line(displayOutput, new Point(finish_line_x, 0), ..., new
			// Scalar(0,0,255),2); // REMOVED
		} else {
			displayOutput = rotatedFrameInput.clone();
		}
		if (releaseMaskForDisplay) {
			maskForDisplay.release();
		}

		// Draw objects (bounding boxes and IDs) on displayOutput
		if (cellTracker != null && cellTracker.objects != null) {
			for (Map.Entry<Integer, Track> entry : cellTracker.objects.entrySet()) {
				Integer cellID = entry.getKey();
				Track track = entry.getValue();
				Point currentCentroid = track.centroid;
				Scalar boxColor = track.isNewTrack ? newTrackColor : existingTrackColor;

				if (track.bbox != null) {
					Imgproc.rectangle(displayOutput, track.bbox.tl(), track.bbox.br(), boxColor, 1); // Thicker box
				}
				if (currentCentroid != null) {
					Imgproc.putText(displayOutput, "ID " + cellID,
							new Point(currentCentroid.x - 10, currentCentroid.y - 10), Imgproc.FONT_HERSHEY_SIMPLEX,
							0.5, boxColor, 1); // Use boxColor for ID too, or different
					Imgproc.circle(displayOutput, currentCentroid, 4, boxColor, 1);
				}
				if (track.isNewTrack) { // After drawing it as new for the first time
					track.isNewTrack = false;
				}
			}
		}
		return displayOutput;
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

	public CentroidTracker getCellTracker() {
		return cellTracker;
	}

	public String getVideoFilename() {
		return videoFilename;
	}

}
