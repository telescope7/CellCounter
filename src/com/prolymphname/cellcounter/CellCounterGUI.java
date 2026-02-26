package com.prolymphname.cellcounter;

import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.border.EmptyBorder; // For padding
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot; // To customize plot colors
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer; // For CDF line color
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer; // For Histogram bar color with XYPlot

public class CellCounterGUI extends JFrame {

	private static final long serialVersionUID = 1L;
	private AnalysisLogic analysisLogic;

	private boolean videoPlaying = false;
	private boolean paused = false;

	private JLabel videoLabel;
	private ChartPanel trackStartTimeChartPanel;
	private ChartPanel speedDistributionChartPanel;
	private JButton analyzeButton, fastButton, playButton, frameForwardButton, resetButton, saveAnalysisButton,
			saveFootprintButton;
	JToggleButton mog2ViewButton;
	private JSpinner playbackRateSpinner;
	private Timer videoTimer;
	private final double DEFAULT_VIDEO_RATE = 1.0;

	// Define some business-like colors
	private final Color CHART_BACKGROUND_COLOR = UIManager.getColor("Panel.background"); // Match L&F background

	public CellCounterGUI() {
		this.analysisLogic = new AnalysisLogic();
		initUI();
	}

	private void initUI() {
		setTitle("Cell Counter - Infiltrate Bio");
		setLayout(new BorderLayout(5, 5));
		setPreferredSize(new Dimension(1350, 750)); // Slightly wider for new controls
		((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

		// ---- Top Panel: Action Buttons ----
		JPanel topControlsPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5)); // Main buttons
		JPanel rateControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5)); // Rate controls

		analyzeButton = new JButton("📹 Analyze");
		fastButton = new JButton("⚡ Fast Analyze");
		playButton = new JButton("▶ Play"); // Text will change to "❚❚ Pause"
		frameForwardButton = new JButton("▶❚ Frame"); // NEW
		resetButton = new JButton("🔁 Reset");
		mog2ViewButton = new JToggleButton("🔬 MOG2 View"); // From previous, ensure it's here

		saveAnalysisButton = new JButton("💾 Save Analysis");
		saveFootprintButton = new JButton("👣 Save Footprint");

		AbstractButton[] mainButtons = { analyzeButton, fastButton, playButton, frameForwardButton, resetButton,
				mog2ViewButton };
		for (AbstractButton btn : mainButtons) {
			btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
			buttonPanel.add(btn);
		}

		// Save buttons might be better grouped or placed differently, but for now add
		// to main flow
		buttonPanel.add(saveAnalysisButton);
		buttonPanel.add(saveFootprintButton);

		// Playback Rate Control
		rateControlPanel.add(new JLabel("Rate:"));
		SpinnerNumberModel rateModel = new SpinnerNumberModel(DEFAULT_VIDEO_RATE, 0.1, 5.0, 0.1); // value, min, max,
																									// step
		playbackRateSpinner = new JSpinner(rateModel);
		playbackRateSpinner
				.setPreferredSize(new Dimension(60, (int) playbackRateSpinner.getPreferredSize().getHeight()));
		playbackRateSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		rateControlPanel.add(playbackRateSpinner);

		topControlsPanel.add(buttonPanel, BorderLayout.WEST);
		topControlsPanel.add(rateControlPanel, BorderLayout.EAST);
		add(topControlsPanel, BorderLayout.NORTH);

		// Initial button states
		fastButton.setEnabled(false);
		playButton.setEnabled(false);
		frameForwardButton.setEnabled(false);
		resetButton.setEnabled(false);
		mog2ViewButton.setEnabled(false);
		saveAnalysisButton.setEnabled(false);
		saveFootprintButton.setEnabled(false);
		playbackRateSpinner.setEnabled(false);

		// ---- Center Panel: Video Display (Left) and Graphs (Right) ----
		// ... (videoLabel, videoScroll setup as before) ...
		videoLabel = new JLabel("No video loaded. Please use 'Analyze Video' to load a file.", SwingConstants.CENTER);
		// ... (videoLabel properties)
		JScrollPane videoScroll = new JScrollPane(videoLabel); /* ... */

		JPanel graphsContainerPanel = new JPanel(new BorderLayout());
		JPanel graphsPanel = new JPanel();
		graphsPanel.setLayout(new BoxLayout(graphsPanel, BoxLayout.Y_AXIS));
		graphsPanel.setBorder(new EmptyBorder(0, 5, 0, 0));

		// Update chart creation to reflect new data meaning
		trackStartTimeChartPanel = createCombinedChart(new double[] {}, "Track Start Time Distribution", "Time (sec)",
				"Count", 1.0);
		speedDistributionChartPanel = createCombinedChart(new double[] {}, "Speed Distribution", "Speed (pixels/sec)",
				"Count", 5.0); // Adjust bin size for speed

		// ... (add charts to graphsPanel as before) ...
		graphsPanel.add(trackStartTimeChartPanel);
		graphsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		graphsPanel.add(speedDistributionChartPanel);
		graphsContainerPanel.add(graphsPanel, BorderLayout.CENTER);

		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, videoScroll, graphsContainerPanel);
		// ... (mainSplitPane properties as before) ...
		add(mainSplitPane, BorderLayout.CENTER);

		// Button Actions
		analyzeButton.addActionListener(e -> handleAnalyzeVideo());
		playButton.addActionListener(e -> handlePlayPauseToggle());
		frameForwardButton.addActionListener(e -> handleFrameForward());
		resetButton.addActionListener(e -> handleResetVideo());
		fastButton.addActionListener(e -> handleFastAnalyze());
		saveAnalysisButton.addActionListener(e -> handleSaveAnalysis());
		saveFootprintButton.addActionListener(e -> handleSaveFootprintData());
		mog2ViewButton.addItemListener(this::handleMOG2Toggle); // Use method reference

		playbackRateSpinner.addChangeListener(e -> handlePlaybackRateChange()); // NEW

		videoTimer = new Timer(33, e -> { // Initial delay, will be updated by playback rate
			if (analysisLogic.isVideoSuccessfullyInitialized() && videoPlaying && !paused) {
				updateFrame();
			}
		});
		// ... (setDefaultCloseOperation, pack, setLocationRelativeTo,
		// setDividerLocation)
		pack(); // Pack the components
		setLocationRelativeTo(null); // Center window
		mainSplitPane.setDividerLocation(0.65);
	}

	private ChartPanel createCombinedChart(double[] data, String title, String xAxisLabel, String yAxisLabel,
			double binSize) {
		if (data == null || data.length == 0)
			data = new double[] { 0 };

		Arrays.sort(data);

		// Histogram Dataset
		HistogramDataset histDataset = new HistogramDataset();
		double maxValue = Arrays.stream(data).max().orElse(binSize);
		int bins = Math.max(1, (int) Math.ceil(maxValue / binSize));
		histDataset.addSeries("Crossing Times", data, bins);

		// CDF Dataset
		XYSeries cdfSeries = new XYSeries("CDF");
		for (int i = 0; i < data.length; i++) {
			cdfSeries.add(data[i], (double) (i + 1) / data.length);
		}
		XYSeriesCollection cdfDataset = new XYSeriesCollection(cdfSeries);

		// Axes
		NumberAxis xAxis = new NumberAxis("Time (sec)");
		NumberAxis yAxisLeft = new NumberAxis("Count");
		NumberAxis yAxisRight = new NumberAxis("Cumulative Probability");
		yAxisRight.setRange(0.0, 1.0);

		// Histogram Renderer
		XYBarRenderer histRenderer = new XYBarRenderer();
		histRenderer.setSeriesPaint(0, new Color(0, 120, 215, 180)); // Slightly transparent blue
		histRenderer.setBarPainter(new StandardXYBarPainter());
		histRenderer.setShadowVisible(false);
		histRenderer.setMargin(0.05);

		// CDF Renderer
		XYLineAndShapeRenderer cdfRenderer = new XYLineAndShapeRenderer();
		cdfRenderer.setSeriesPaint(0, Color.RED);
		cdfRenderer.setSeriesStroke(0, new BasicStroke(2.5f));
		cdfRenderer.setSeriesShapesVisible(0, false);

		// Plot setup
		XYPlot plot = new XYPlot();
		plot.setDomainAxis(xAxis);
		plot.setBackgroundPaint(Color.WHITE);
		plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
		plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

		// Set histogram as dataset 0
		plot.setDataset(0, histDataset);
		plot.setRenderer(0, histRenderer);
		plot.setRangeAxis(0, yAxisLeft);
		plot.mapDatasetToRangeAxis(0, 0);

		// Set CDF as dataset 1
		plot.setDataset(1, cdfDataset);
		plot.setRenderer(1, cdfRenderer);
		plot.setRangeAxis(1, yAxisRight);
		plot.mapDatasetToRangeAxis(1, 1);

		// Make sure CDF is rendered last (on top)
		plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

		// Final Chart
		JFreeChart chart = new JFreeChart(title, new Font("Segoe UI", Font.BOLD, 14), plot, true);
		chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);

		return new ChartPanel(chart);
	}

	private void updateCharts() {
		// Fetch data for charts
		double[] startTimesArray = analysisLogic.getTrackStartTimes().stream().mapToDouble(Double::doubleValue)
				.toArray();
		double[] speedsArray = analysisLogic.getSpeeds().stream().mapToDouble(Double::doubleValue).toArray();

		// Re-create or update chart datasets
		// Track Start Times Chart
		ChartPanel newStartTimeChart = createCombinedChart(startTimesArray, "Track Start Time Distribution",
				"Time (sec)", "Count", 1.0);
		if (trackStartTimeChartPanel != null)
			trackStartTimeChartPanel.setChart(newStartTimeChart.getChart());

		// Speed Distribution Chart
		ChartPanel newSpeedChart = createCombinedChart(speedsArray, "Speed Distribution", "Speed (pixels/sec)", "Count",
				5.0); // Adjust bin size
		if (speedDistributionChartPanel != null)
			speedDistributionChartPanel.setChart(newSpeedChart.getChart());
	}

	private void handleAnalyzeVideo() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			String path = chooser.getSelectedFile().getAbsolutePath();
			if (analysisLogic.initializeVideo(path)) {
				videoPlaying = false; // Initial state: not playing
				paused = true; // Initial state: effectively paused at frame 0
				playButton.setText("▶ Play");
				playButton.setEnabled(true);
				frameForwardButton.setEnabled(analysisLogic.isCaptureActive()); // Can step from frame 0
				resetButton.setEnabled(true);
				fastButton.setEnabled(true);
				mog2ViewButton.setEnabled(true);
				mog2ViewButton.setSelected(false);
				saveAnalysisButton.setEnabled(true);
				saveFootprintButton.setEnabled(true);
				playbackRateSpinner.setEnabled(true);
				playbackRateSpinner.setValue(DEFAULT_VIDEO_RATE);
				updateVideoTimerDelay(DEFAULT_VIDEO_RATE);

				Mat firstFrame = analysisLogic.getLastProcessedFrame();
				if (firstFrame != null && !firstFrame.empty()) {
					videoLabel.setText(null);
					videoLabel.setIcon(new ImageIcon(matToBufferedImage(firstFrame)));
				} else {
					videoLabel.setIcon(null);
					videoLabel.setText("Error displaying first frame.");
					JOptionPane.showMessageDialog(this, "Video loaded, but could not display the first frame.",
							"Display Error", JOptionPane.WARNING_MESSAGE);
				}
				updateCharts();
			} else {
				// Failed to initialize video
				videoPlaying = false;
				paused = false;
				playButton.setText("▶ Play");
				playButton.setEnabled(false);
				frameForwardButton.setEnabled(false);
				resetButton.setEnabled(false);
				fastButton.setEnabled(false);
				mog2ViewButton.setEnabled(false);
				saveAnalysisButton.setEnabled(false);
				saveFootprintButton.setEnabled(false);
				playbackRateSpinner.setEnabled(false);
				JOptionPane.showMessageDialog(this, "Error opening or initializing video file.", "Error",
						JOptionPane.ERROR_MESSAGE);
				videoLabel.setIcon(null);
				videoLabel.setText("No video loaded. Please use 'Analyze Video' to load a file.");
			}
		}
	}

	private void handlePlayPauseToggle() {
		if (!analysisLogic.isVideoSuccessfullyInitialized()) {
			return;
		}

		if (videoPlaying && !paused) {
			// Case 1: Video is actively playing -> Pause it
			paused = true;
			videoTimer.stop();
			playButton.setText("▶ Play");
			// Enable frame forward only if there are more frames
			frameForwardButton.setEnabled(analysisLogic.isCaptureActive());
			System.out.println("Video Paused.");

		} else if (paused) {
			// Case 2: Video is paused -> Resume it
			// Ensure capture is still active (e.g., didn't reach end via frame forward)
			if (!analysisLogic.isCaptureActive()) {
				JOptionPane.showMessageDialog(this, "Video has ended. Please reset to play again.", "Video Ended",
						JOptionPane.INFORMATION_MESSAGE);
				playButton.setText("▶ Play"); // Keep as play, implying reset is needed
				frameForwardButton.setEnabled(false);
				return;
			}
			paused = false;
			videoPlaying = true; // It's now actively playing
			videoTimer.start();
			playButton.setText("❚❚ Pause");
			frameForwardButton.setEnabled(false); // Disable frame forward when playing
			System.out.println("Video Resumed.");

		} else {
			// Case 3: Video is stopped (neither playing nor paused, e.g., initial, after
			// reset, or after end) -> Start it
			if (!analysisLogic.isCaptureActive()) { // Video might have ended or not ready
				int choice = JOptionPane.showConfirmDialog(this,
						"Video has finished or is not ready. Reset and play from the beginning?", "Play Video",
						JOptionPane.YES_NO_OPTION);
				if (choice == JOptionPane.YES_OPTION) {
					handleResetVideo(); // This sets captureActive if successful & leaves it paused
					if (!analysisLogic.isCaptureActive()) {
						System.out.println("Reset failed or cancelled, cannot play.");
						return; // Reset failed or user cancelled
					}
					// After handleResetVideo, it's paused at frame 0. Now start playing.
					paused = false; // Transition from reset's paused state
					videoPlaying = true;
					videoTimer.start();
					playButton.setText("❚❚ Pause");
					frameForwardButton.setEnabled(false);
					System.out.println("Video Playing after Reset.");
					return; // Explicitly return after starting post-reset
				} else {
					System.out.println("User chose not to reset.");
					return; // User chose not to reset
				}
			}
			// If capture is active (e.g. first play after load)
			paused = false;
			videoPlaying = true;
			videoTimer.start();
			playButton.setText("❚❚ Pause");
			frameForwardButton.setEnabled(false);
			System.out.println("Video Playing.");
		}
	}

	private void handleResetVideo() {
		if (!analysisLogic.isVideoSuccessfullyInitialized()) {
			JOptionPane.showMessageDialog(this, "No video has been successfully loaded to reset.", "Reset Error",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		videoTimer.stop();
		videoPlaying = false;
		paused = true; // After reset, it's at frame 0, effectively paused
		playButton.setText("▶ Play");
		playButton.setEnabled(true);
		mog2ViewButton.setSelected(false);
		analysisLogic.setDisplayMOG2Foreground(false);
		playbackRateSpinner.setValue(DEFAULT_VIDEO_RATE);
		updateVideoTimerDelay(DEFAULT_VIDEO_RATE);

		analysisLogic.resetAnalysisForCurrentVideo();
		Mat firstFrameAfterReset = analysisLogic.getLastProcessedFrame();

		frameForwardButton.setEnabled(analysisLogic.isCaptureActive()); // Enable if reset was successful

		if (firstFrameAfterReset != null && !firstFrameAfterReset.empty()) {
			videoLabel.setIcon(new ImageIcon(matToBufferedImage(firstFrameAfterReset)));
			videoLabel.setText(null);
		} else {
			videoLabel.setIcon(null);
			videoLabel.setText("Error displaying frame after reset.");
			JOptionPane.showMessageDialog(this, "Failed to prepare video for display after reset.", "Reset Error",
					JOptionPane.ERROR_MESSAGE);
		}
		updateCharts();
	}

	private void handlePlaybackRateChange() {
		if (!analysisLogic.isVideoSuccessfullyInitialized() || analysisLogic.getFps() <= 0) {
			return;
		}
		double rate = DEFAULT_VIDEO_RATE;
		Object val = playbackRateSpinner.getValue();
		if (val instanceof Number) {
			rate = ((Number) val).doubleValue();
		}
		updateVideoTimerDelay(rate);
	}

	private void updateVideoTimerDelay(double rate) {
		if (rate <= 0.01)
			rate = 0.01; // Prevent division by zero or excessively long delay
		double fps = analysisLogic.getFps();
		if (fps <= 0)
			fps = 30; // Fallback fps

		int newDelay = (int) Math.round(1000.0 / (fps * rate));
		videoTimer.setDelay(Math.max(1, newDelay)); // Ensure delay is at least 1ms
		System.out.println("Playback rate set to: " + rate + "x, Timer delay: " + newDelay + "ms");
	}

	private void updateFrame() {
		Mat frame = analysisLogic.processNextFrameForGUI();
		if (frame != null && !frame.empty()) {
			videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
			updateCharts();
		} else if (frame == null && !analysisLogic.isCaptureActive() && (videoPlaying || paused)) {
			videoTimer.stop();
			videoPlaying = false; // No longer actively playing
			paused = true; // Effectively paused at the end
			playButton.setText("▶ Play"); // Shows play, implies reset is needed to play again
			frameForwardButton.setEnabled(false); // Can't step further
			if (SwingUtilities.isEventDispatchThread()) {
				JOptionPane.showMessageDialog(this, "End of video.", "Playback Finished",
						JOptionPane.INFORMATION_MESSAGE);
			} else {
				System.out.println("End of video reached in non-EDT context (timer).");
			}
		} else if (frame == null && (videoPlaying || paused)) { // Capture might still be active, but read failed
			videoTimer.stop();
			videoPlaying = false;
			paused = true; // Error state, treat as paused
			playButton.setText("▶ Play");
			frameForwardButton.setEnabled(false); // Can't reliably step
			System.err.println("UpdateFrame: Received null/empty frame unexpectedly.");
			if (SwingUtilities.isEventDispatchThread()) {
				JOptionPane.showMessageDialog(this, "Error during video playback.", "Playback Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void handleMOG2Toggle(ItemEvent e) {
		if (!analysisLogic.isVideoSuccessfullyInitialized())
			return;
		boolean showMask = (e.getStateChange() == ItemEvent.SELECTED);
		analysisLogic.setDisplayMOG2Foreground(showMask);
		Mat currentDisplayMat = analysisLogic.getLastProcessedFrame();
		if (currentDisplayMat != null && !currentDisplayMat.empty()) {
			videoLabel.setIcon(new ImageIcon(matToBufferedImage(currentDisplayMat.clone())));
		} else {
			/* ... handle ... */ }
		videoLabel.repaint();
	}

	private void handleFastAnalyze() {
		if (!analysisLogic.isVideoSuccessfullyInitialized()) { // Check if a base video is ready
			JOptionPane.showMessageDialog(this, "Please load a video first using 'Analyze Video'.", "No Video",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (videoPlaying && !paused) {
			JOptionPane.showMessageDialog(this, "Please pause or reset video before Fast Analyze.", "Warning",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		videoTimer.stop(); // Stop regular playback
		videoPlaying = false;
		paused = true; // Conceptually paused during fast analysis
		playButton.setText("Play");

		analysisLogic.resetAnalysisForCurrentVideo();
		; // Reset for a fresh analysis run

		int totalFrames = analysisLogic.getFrameCount();
		System.out.println("Fast analysis starting. Total frames: " + totalFrames);
		int updateFrequency = Math.max(1, totalFrames / 100); // Update GUI ~100 times

		SwingWorker<Void, Mat> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < totalFrames; i++) {
					// Process frame using the analysis-focused method (might not draw all overlays
					// for speed)
					Mat processedAnalyticalFrame = analysisLogic.processNextFrameForAnalysis();
					if (processedAnalyticalFrame == null)
						break; // End of video or error

					if (i % updateFrequency == 0 || i == totalFrames - 1) {

						Mat frameToShow = analysisLogic.getLastProcessedFrame();
						if (frameToShow != null && !frameToShow.empty()) {
							Mat frameWithText = frameToShow.clone();
							double percent = (analysisLogic.getCurrentFrameNumber() / (double) totalFrames) * 100;
							Imgproc.putText(frameWithText, String.format("Fast Analyze: %.1f%%", percent),
									new Point(10, frameWithText.rows() - 20), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8,
									new Scalar(0, 255, 255), 2);
							publish(frameWithText); // Publish for GUI update
						} else {
							// If frameToShow is null, maybe publish the last good one or a placeholder
							// For now, we just skip if null
						}
						if (processedAnalyticalFrame != null)
							processedAnalyticalFrame.release(); // if it was different from frameToShow
					} else {
						if (processedAnalyticalFrame != null)
							processedAnalyticalFrame.release();
					}
					Thread.sleep(1); // Be nice to EDT, but this loop is mostly non-GUI
				}
				return null;
			}

			@Override
			protected void process(List<Mat> chunks) {
				if (!chunks.isEmpty()) {
					Mat latestFrame = chunks.get(chunks.size() - 1);
					if (latestFrame != null && !latestFrame.empty()) {
						videoLabel.setIcon(new ImageIcon(matToBufferedImage(latestFrame)));
					}
					latestFrame.release(); // Release the displayed clone
					updateCharts();
				}
			}

			@Override
			protected void done() {
				// Display the very last frame from analysisLogic
				Mat finalFrame = analysisLogic.getLastProcessedFrame();
				if (finalFrame != null && !finalFrame.empty()) {
					videoLabel.setIcon(new ImageIcon(matToBufferedImage(finalFrame)));
				}
				updateCharts();
				System.out.println("Fast analysis complete.");
				JOptionPane.showMessageDialog(CellCounterGUI.this, "Fast Analysis Complete.", "Done",
						JOptionPane.INFORMATION_MESSAGE);
				// Optionally auto-save
				// handleSaveAnalysis();
				// handleSaveFootprintData();
				paused = false; // Reset pause state
			}
		};
		worker.execute();

	}

	private void handleFrameForward() {
	    // Only allow if video is initialized, paused, and capture is active
	    if (analysisLogic.isVideoSuccessfullyInitialized() && paused && analysisLogic.isCaptureActive()) {
	        // Temporarily set videoPlaying to true for updateFrame to process one frame as if it's "playing"
	        // This is a bit of a conceptual stretch, but updateFrame's logic uses videoPlaying.
	        // Or, updateFrame could have a mode for single step.
	        // For now, let updateFrame handle it.
	        updateFrame(); // Process and display one frame

	        // After processing, if capture is no longer active (last frame was just processed)
	        if (!analysisLogic.isCaptureActive()) {
	            frameForwardButton.setEnabled(false);
	            playButton.setText("▶ Play"); // Video ended
	            videoPlaying = false; // Ensure it's marked as not playing
	            // paused remains true, indicating it's at the end but "paused" there.
	        }
	        // If still active, it remains paused, frameForwardButton enabled.
	    }
	}

	private void handleSaveAnalysis() {
		// Now, check if a video was ever successfully initialized and thus has data
		// (even if playback finished)
		if (!analysisLogic.isVideoSuccessfullyInitialized()) {
			JOptionPane.showMessageDialog(this, "No video has been loaded or analysis performed.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		AnalysisLogic.CentroidTracker tracker = analysisLogic.getCellTracker();
		if (tracker == null) { // Should not happen if videoSuccessfullyInitialized is true and init was proper
			JOptionPane.showMessageDialog(this, "Analysis data (tracker) is not available.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		String[] metadata = promptForMetadata();
		if (metadata == null)
			return; // user canceled

		String cellType = metadata[0];
		String substrate = metadata[1];
		String flow = metadata[2];

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Analysis Data");
		chooser.setSelectedFile(new File("analysis_results.csv"));
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
				pw.println(
						"CellType,Substrate,FlowCondition,CellID,FirstSeenFrame,FirstSeenTime(s),CrossingFrame,CrossingTime(s),"
								+ "TotalDistance,DistanceToCross,DistanceAfterCross,"
								+ "AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed(pixels/sec)");

				Map<Integer, AnalysisLogic.Track> allTracks = new HashMap<>();
				allTracks.putAll(tracker.objects);
				allTracks.putAll(tracker.completeTracks);
				double fps = analysisLogic.getFps();

				for (Map.Entry<Integer, AnalysisLogic.Track> entry : allTracks.entrySet()) {
					int cellID = entry.getKey();
					AnalysisLogic.Track track = entry.getValue();
					if (track.history.isEmpty())
						continue;

					Map<String, Object> metrics = analysisLogic.computeMetricsForTrack(track);
					int firstFrame = track.startFrame;
					double firstTime = track.startTime;
					int crossFrame = track.startFrame;
					double crossTime = -1.0;
					if (crossFrame > 0) {
						crossTime = (double) crossFrame / fps;
					}

					pw.printf("%s,%s,%s,%d,%d,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n", cellType, substrate,
							flow, cellID, firstFrame, firstTime, crossFrame > 0 ? crossFrame : -1, crossTime,
							(Double) metrics.get("TotalDistance"), (Double) metrics.get("DistanceToCross"),
							(Double) metrics.get("DistanceAfterCross"), (Double) metrics.get("AvgFrameDistance"),
							(Double) metrics.get("MedianFrameDistance"), (Integer) metrics.get("FramesTracked"),
							(Integer) metrics.get("FramesMissed"), (Double) metrics.get("Speed"));

				}
				JOptionPane.showMessageDialog(this, "Analysis saved to " + file.getAbsolutePath(), "Success",
						JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Error saving analysis: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}

		}
	}

	private void handleSaveFootprintData() {
		if (!analysisLogic.isVideoSuccessfullyInitialized()) {
			JOptionPane.showMessageDialog(this, "No video has been loaded or analysis performed for footprint data.",
					"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		AnalysisLogic.CentroidTracker tracker = analysisLogic.getCellTracker();
		if (tracker == null) {
			JOptionPane.showMessageDialog(this, "Footprint data (tracker) is not available.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Prompt for metadata
		String[] metadata = promptForMetadata();
		if (metadata == null)
			return; // User canceled
		String cellType = metadata[0];
		String substrate = metadata[1];
		String flow = metadata[2];

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Footprint Data");
		chooser.setSelectedFile(new File("footprint_data.csv"));
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
				pw.println("CellType,Substrate,FlowCondition,CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
				Map<Integer, AnalysisLogic.Track> allTracks = new HashMap<>();
				allTracks.putAll(tracker.objects);
				allTracks.putAll(tracker.completeTracks);

				for (Map.Entry<Integer, AnalysisLogic.Track> entry : allTracks.entrySet()) {
					int cellID = entry.getKey();
					AnalysisLogic.Track track = entry.getValue();
					for (AnalysisLogic.HistoryItem item : track.history) {
						pw.printf("%s,%s,%s,%d,%d,%d,%d,%d,%d%n", cellType, substrate, flow, cellID, item.frame,
								(int) item.UL.x, (int) item.UL.y, (int) item.LR.x, (int) item.LR.y);
					}
				}
				JOptionPane.showMessageDialog(this, "Footprint data saved to " + file.getAbsolutePath(), "Success",
						JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Error saving footprint data: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}

	}

	private Image matToBufferedImage(Mat mat) {
		if (mat == null || mat.empty()) {
			BufferedImage placeholder = new BufferedImage(320, 240, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = placeholder.createGraphics();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, 320, 240);
			g.setColor(Color.LIGHT_GRAY);
			g.drawString("No Image", 130, 120);
			g.dispose();
			return placeholder;
		}
		int type = BufferedImage.TYPE_BYTE_GRAY;
		if (mat.channels() > 1) {
			type = BufferedImage.TYPE_3BYTE_BGR;
		}
		int width = Math.max(1, mat.cols());
		int height = Math.max(1, mat.rows());

		BufferedImage image = new BufferedImage(width, height, type);
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		mat.get(0, 0, data);
		return image;
	}
	// ----- END PLACEHOLDER -----

	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		try {

			// Apply Nimbus Look and Feel for a more modern appearance
			boolean nimbusFound = false;
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					nimbusFound = true;
					break;
				}
			}
			if (!nimbusFound) { // If Nimbus is not available, fall back to the system L&F
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			// If L&F setting fails, it will use the default Java L&F.
			System.err.println("Failed to set Look and Feel: " + e.getMessage());
			// e.printStackTrace(); // Optionally print stack trace
		}

		if (args.length == 5) {
			HeadlessProcessor processor = new HeadlessProcessor();
			processor.run(args[0], args[1], args[2], args[3], args[4]);
			System.exit(0);
		} else {
			SwingUtilities.invokeLater(() -> {
				// Optional Splash Screen (can be removed if not desired)
				JWindow splash = new JWindow();
				// Simple splash content
				JPanel splashPanel = new JPanel(new BorderLayout());
				splashPanel.setBackground(new Color(230, 230, 250)); // Light lavender
				splashPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 128), 2)); // Navy border
				JLabel splashLabel = new JLabel("<html><center>Cell Counter<br/><i>Infiltrate Bio</i></center></html>",
						SwingConstants.CENTER);
				splashLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
				splashLabel.setForeground(new Color(0, 0, 128)); // Navy text
				splashPanel.add(splashLabel, BorderLayout.CENTER);
				JProgressBar progressBar = new JProgressBar();
				progressBar.setIndeterminate(true);
				splashPanel.add(progressBar, BorderLayout.SOUTH);
				splash.getContentPane().add(splashPanel);

				splash.setSize(400, 150);
				splash.setLocationRelativeTo(null); // Center splash
				splash.setVisible(true);

				// Simulate loading time
				new Timer(2000, (ae) -> { // Show splash for 2 seconds
					splash.setVisible(false);
					splash.dispose();
					new CellCounterGUI().setVisible(true);
				}) {
					private static final long serialVersionUID = -6312010934618202078L;

					{
						setRepeats(false);
					}
				}.start();
			});
		}
	}

	private String[] promptForMetadata() {
		JTextField cellField = new JTextField();
		JTextField substrateField = new JTextField();
		JTextField flowField = new JTextField();

		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.add(new JLabel("Cell Type:"));
		panel.add(cellField);
		panel.add(new JLabel("Substrate Name:"));
		panel.add(substrateField);
		panel.add(new JLabel("Flow Condition:"));
		panel.add(flowField);

		int result = JOptionPane.showConfirmDialog(this, panel, "Enter Experimental Metadata",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (result == JOptionPane.OK_OPTION) {
			return new String[] { cellField.getText().trim(), substrateField.getText().trim(),
					flowField.getText().trim() };
		} else {
			return null; // User canceled
		}
	}

}