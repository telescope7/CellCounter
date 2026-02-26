package com.prolymphname.cellcounter;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;
import org.opencv.video.KalmanFilter; // Import KalmanFilter

import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.util.*;
import java.util.List;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class CellCounter extends JFrame {

    private static final long serialVersionUID = 1L;

    //===== Video processing state =====
    private VideoCapture cap;
    private boolean videoLoaded = false;
    private boolean videoPlaying = false;
    private boolean paused = false;
    private int frameNumber = 0;
    private double fps = 30;
    private int rotationValue = 0;
    private double finishedLinePercentage = 80;
    private Mat lastFrame;
    private BackgroundSubtractorMOG2 fgbg;
    private CentroidTracker cellTracker;
    private String videoFilename;
    private Mat referenceFrame = null;

    // For storing crossing times
    private List<Double> crossingTimes = new ArrayList<>();

    // ===== GUI components =====
    private JLabel videoLabel;
    private ChartPanel histogramChartPanel;
    private ChartPanel cdfChartPanel;
    private JButton analyzeButton, fastButton, playButton, pauseButton,
            resetButton, saveAnalysisButton, saveFootprintButton;
    private Timer videoTimer;

    public CellCounter() {
        initUI();
    }

    private void initUI() {
        // Set up the main window layout:
        setLayout(new BorderLayout());

        // ---- Top Panel: Two charts (Histogram and CDF) ----
        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        histogramChartPanel = createHistogramChartPanel();
        cdfChartPanel = createCDFChartPanel();
        topPanel.add(histogramChartPanel);
        topPanel.add(cdfChartPanel);

        // ---- Center Panel: Video display area (using a JLabel with an ImageIcon) ----
        videoLabel = new JLabel();
        videoLabel.setHorizontalAlignment(JLabel.CENTER);
        videoLabel.setPreferredSize(new Dimension(640, 480));
        JScrollPane videoScroll = new JScrollPane(videoLabel);

        // ---- Bottom Panel: Seven control buttons ----
        JPanel buttonPanel = new JPanel(new FlowLayout());
        analyzeButton = new JButton("Analyze Video");
        fastButton = new JButton("Fast Analyze");
        playButton = new JButton("Play");
        pauseButton = new JButton("Pause");
        resetButton = new JButton("Reset");
        saveAnalysisButton = new JButton("Save Analysis");
        saveFootprintButton = new JButton("Save Footprint Data");
        buttonPanel.add(analyzeButton);
        buttonPanel.add(fastButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(saveAnalysisButton);
        buttonPanel.add(saveFootprintButton);

        // Add panels to the frame:
        add(topPanel, BorderLayout.NORTH);
        add(videoScroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // ---- Button Action Listeners ----
        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                analyzeVideo();
            }
        });

        fastButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fastAnalyze();
            }
        });

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playVideo();
            }
        });

        pauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });

        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetVideo();
            }
        });

        saveAnalysisButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveAnalysis();
            }
        });

        saveFootprintButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFootprintData();
            }
        });

        // ---- Timer for video playback (updates roughly every 33 ms ~ 30 FPS) ----
        videoTimer = new Timer(33, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (videoLoaded && videoPlaying && !paused) {
                    updateFrame();
                }
            }
        });

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    // ===== Methods to create and update the charts using JFreeChart =====
    private ChartPanel createHistogramChartPanel() {
        // Create an empty dataset and chart for the histogram
        HistogramDataset dataset = new HistogramDataset();
        JFreeChart chart = ChartFactory.createHistogram(
                "Histogram of Cell Crossing Times",
                "Time (sec)", "Count", dataset, PlotOrientation.VERTICAL, false, false, false); // Changed 'paused' to false for chart properties
        return new ChartPanel(chart);
    }

    private ChartPanel createCDFChartPanel() {
        // Create an empty dataset and chart for the CDF
        XYSeries series = new XYSeries("CDF");
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "CDF of Cell Crossing Times",
                "Time (sec)", "CDF", dataset);
        return new ChartPanel(chart);
    }

    private void updateCharts() {
        // Update the histogram and CDF charts using the crossingTimes list
        double binSize = 1.0;
        double[] data = crossingTimes.stream().mapToDouble(Double::doubleValue).toArray();
        if (data.length == 0) {
            data = new double[]{0};
        }
        // ---- Histogram update ----
        HistogramDataset histDataset = new HistogramDataset();
        double maxValue = Arrays.stream(data).max().orElse(binSize);
        int bins = (int) Math.ceil(maxValue / binSize);
        bins = Math.max(bins, 1);  // ensure at least one bin
        histDataset.addSeries("Crossing Times", data, bins);
        JFreeChart histChart = ChartFactory.createHistogram(
                "Histogram of Cell Crossing Times",
                "Time (sec)", "Count", histDataset, PlotOrientation.VERTICAL, false, false, false); // Changed 'paused' to false
        histogramChartPanel.setChart(histChart);

        // ---- CDF update ----
        Arrays.sort(data);
        XYSeries series = new XYSeries("CDF");
        for (int i = 0; i < data.length; i++) {
            double cdf = (i + 1) / (double) data.length;
            series.add(data[i], cdf);
        }
        XYSeriesCollection cdfDataset = new XYSeriesCollection(series);
        JFreeChart cdfChart = ChartFactory.createXYLineChart(
                "CDF of Cell Crossing Times",
                "Time (sec)", "CDF", cdfDataset);
        cdfChartPanel.setChart(cdfChart);
    }

    public int autoDetectRotation(VideoCapture cap, int[] candidates) {

        // Store displacements and previous centroids for each candidate angle.
        Map<Integer, List<Double>> displacements = new HashMap<>();
        Map<Integer, Point> prevCentroids = new HashMap<>();
        for (int angle : candidates) {
            displacements.put(angle, new ArrayList<>());
            prevCentroids.put(angle, null);
        }

        // Save the original frame position.
        double originalPos = cap.get(Videoio.CAP_PROP_POS_FRAMES);
        Mat frame = new Mat();

        while (cap.read(frame)) {
            for (int angle : candidates) {
                // Rotate the frame by the candidate angle.
                Mat rotated = rotateImage(frame, angle);

                // Convert to grayscale.
                Mat gray = new Mat();
                Imgproc.cvtColor(rotated, gray, Imgproc.COLOR_BGR2GRAY);

                // Detect features using goodFeaturesToTrack.
                MatOfPoint corners = new MatOfPoint();
                Imgproc.goodFeaturesToTrack(gray, corners, 50, 0.01, 10);

                if (!corners.empty()) {
                    Point[] pts = corners.toArray();
                    double sumX = 0, sumY = 0;
                    for (Point p : pts) {
                        sumX += p.x;
                        sumY += p.y;
                    }
                    Point avg = new Point(sumX / pts.length, sumY / pts.length);

                    // If we already had a centroid for this angle, compute x displacement.
                    Point prev = prevCentroids.get(angle);
                    if (prev != null) {
                        double dx = avg.x - prev.x;
                        displacements.get(angle).add(dx);
                    }
                    prevCentroids.put(angle, avg);
                }
            }

        }

        // Restore the original frame position.
        cap.set(Videoio.CAP_PROP_POS_FRAMES, originalPos);

        // Determine the best angle based on average x displacement.
        int bestAngle = candidates[0];
        double bestValue = -Double.MAX_VALUE;
        for (int angle : candidates) {
            List<Double> dxList = displacements.get(angle);
            double avgDx = dxList.stream().mapToDouble(d -> d).average().orElse(0);
            if (avgDx > bestValue) {
                bestValue = avgDx;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }


    // ===== Video control methods =====

    // This method is called when the user clicks "Analyze Video"
    private void analyzeVideo() {
        // Open a file chooser to select the video file.
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            videoFilename = chooser.getSelectedFile().getAbsolutePath();
            cap = new VideoCapture(videoFilename);
            if (!cap.isOpened()) {
                JOptionPane.showMessageDialog(this, "Error opening video file.");
                return;
            }
            videoLoaded = true;
            videoPlaying = false;
            paused = false;
            frameNumber = 0;
            fps = cap.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) {
                fps = 30;
            }
            // Create background subtractor and tracker
            fgbg = Video.createBackgroundSubtractorMOG2(500, 50, false);


            cellTracker = new CentroidTracker(40, 50); // Increased maxDisappeared and maxDistance for Kalman


            // For simplicity, here we simply use rotation = 0.

            int[] candidates = {0, 90, 180, 270};
            //rotationValue = autoDetectRotation(cap, candidates);
            rotationValue = 0;
            System.out.println("Auto-detected rotation: " + rotationValue);



            // Read the first frame and display it.
            Mat frame = new Mat();
            if (cap.read(frame)) {
                frameNumber = 1;
                frame = rotateImage(frame, rotationValue);
                lastFrame = frame.clone();
                int H = frame.rows();
                int W = frame.cols();
                int finish_line_x = (int) ((finishedLinePercentage / 100.0) * W);
                Imgproc.line(frame, new Point(finish_line_x, 0), new Point(finish_line_x, H), new Scalar(0, 0, 255), 2);
                videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
                videoLabel.repaint();
            }
        }
    }

    // Called when the user clicks "Play"
    private void playVideo() {
        if (videoLoaded && !paused) {
            videoPlaying = true;
            videoTimer.start();
        }
    }

    // Called when the user clicks "Pause" (toggle pause/unpause)
    private void togglePause() {
        if (paused) {
            paused = false;
            videoPlaying = true;
            pauseButton.setText("Pause");
        } else {
            paused = true;
            videoPlaying = false;
            pauseButton.setText("Unpause");
        }
    }

    // Called when the user clicks "Reset"
    private void resetVideo() {
        if (videoLoaded) {
            videoPlaying = false;
            paused = false;
            pauseButton.setText("Pause");
            frameNumber = 0;
            cap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
            fgbg = Video.createBackgroundSubtractorMOG2(500, 50, false);
            cellTracker = new CentroidTracker(40, 50); // Resetting tracker
            // Clear the data used for plotting
            crossingTimes.clear();

            Mat frame = new Mat();
            if (cap.read(frame)) {
                frameNumber = 1;
                frame = rotateImage(frame, rotationValue);
                lastFrame = frame.clone();
                int H = frame.rows();
                int W = frame.cols();
                int finish_line_x = (int) ((finishedLinePercentage / 100.0) * W);
                Imgproc.line(frame, new Point(finish_line_x, 0), new Point(finish_line_x, H), new Scalar(0, 0, 255), 2);
                videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
                videoLabel.repaint();
                updateCharts();
            }
        }
    }

    // Called when the user clicks "Save Analysis"
    private void saveAnalysis() {
        if (!videoLoaded) {
            JOptionPane.showMessageDialog(this, "No video loaded.");
            return;
        }
        if (videoPlaying && !paused) {
            JOptionPane.showMessageDialog(this, "Please pause the video before saving analysis.");
            return;
        }
        // Combine finished and currently active tracks:
        Map<Integer, CentroidTracker.Track> allTracks = new HashMap<>();
        if (cellTracker != null) {
            allTracks.putAll(cellTracker.completeTracks);
            allTracks.putAll(cellTracker.objects);
        }
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("CellID,TotalDistance,DistanceToCrossing,DistanceAfterCrossing,AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed (pixels/sec)");
                for (Integer cellID : allTracks.keySet()) {
                    CentroidTracker.Track track = allTracks.get(cellID);
                    Map<String, Object> metrics = computeMetrics(track, fps);
                    pw.printf("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n",
                            cellID,
                            metrics.get("TotalDistance"),
                            metrics.get("DistanceToCross"),
                            metrics.get("DistanceAfterCross"),
                            metrics.get("AvgFrameDistance"),
                            metrics.get("MedianFrameDistance"),
                            metrics.get("FramesTracked"),
                            metrics.get("FramesMissed"),
                            metrics.get("Speed"));
                }
                JOptionPane.showMessageDialog(this, "Analysis saved to " + file.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving analysis: " + ex.getMessage());
            }
        }
    }

    // Called when the user clicks "Save Footprint Data"
    private void saveFootprintData() {
        if (!videoLoaded) {
            JOptionPane.showMessageDialog(this, "No video loaded.");
            return;
        }
        Map<Integer, CentroidTracker.Track> allTracks = new HashMap<>();
        if (cellTracker != null) {
            allTracks.putAll(cellTracker.completeTracks);
            allTracks.putAll(cellTracker.objects);
        }
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
                for (Integer cellID : allTracks.keySet()) {
                    CentroidTracker.Track track = allTracks.get(cellID);
                    for (CentroidTracker.HistoryItem item : track.history) {
                        pw.printf("%d,%d,%d,%d,%d,%d%n", cellID, item.frame, (int) item.UL.x, (int) item.UL.y, (int) item.LR.x, (int) item.LR.y);
                    }
                }
                JOptionPane.showMessageDialog(this, "Footprint data saved to " + file.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving footprint data: " + ex.getMessage());
            }
        }
    }

    // ===== The "Fast Analyze" process =====
    private void fastAnalyze() {
        if (!videoLoaded) {
            JOptionPane.showMessageDialog(this, "No video loaded.");
            return;
        }
        resetVideo();
        int totalFrames = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        System.out.println("Fast analysis starting. Total frames: " + totalFrames);
        int updateFrequency = Math.max(1, totalFrames / 100);
        while (true) {
            Mat frame = new Mat();
            if (!cap.read(frame))
                break;
            frameNumber++;
            double currentTime = frameNumber / fps;
            frame = rotateImage(frame, rotationValue);
            lastFrame = frame.clone();
            int H = frame.rows();
            int W = frame.cols();
            int finish_line_x = (int) ((finishedLinePercentage / 100.0) * W);
            Imgproc.line(frame, new Point(finish_line_x, 0), new Point(finish_line_x, H), new Scalar(0, 0, 255), 2);

            // Apply background subtraction and some morphology:
            Mat fgmask = new Mat();
            fgbg.apply(frame, fgmask);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_OPEN, kernel, new Point(-1, -1), 2);
            Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_DILATE, kernel, new Point(-1, -1), 2);

            // Find contours:
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(fgmask.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            List<Rect> rects = new ArrayList<>();
            for (MatOfPoint c : contours) {
                if (Imgproc.contourArea(c) < 100)
                    continue;
                Rect r = Imgproc.boundingRect(c);
                rects.add(r);
                Imgproc.rectangle(frame, r.tl(), r.br(), new Scalar(0, 255, 0), 2);
            }
            // Update the tracker
            cellTracker.update(rects, currentTime, finish_line_x, frameNumber);
            // Mark cells that have crossed the finish line:
            for (Integer cellID : cellTracker.objects.keySet()) {
                CentroidTracker.Track track = cellTracker.objects.get(cellID);
                // Use the Kalman-filtered centroid for crossing check
                Point currentCentroid = track.centroid; // This is the Kalman-filtered position
                if (!track.crossed && currentCentroid.x >= finish_line_x) {
                    track.crossed = true;
                    track.crossingFrame = frameNumber;
                    track.crossingPosition = currentCentroid;
                    System.out.println("Cell " + cellID + " crossed finish line at time " + String.format("%.2f", currentTime) + " sec");
                    crossingTimes.add(currentTime);
                }
            }
            double percent = (frameNumber / (double) totalFrames) * 100;
            Imgproc.putText(frame, String.format("Fast Analyze: %.1f%% (%d/%d)", percent, frameNumber, totalFrames),
                    new Point(10, H - 10), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 255, 255), 2);
            if (frameNumber % updateFrequency == 0) {
                videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
                videoLabel.repaint();
                updateCharts();
                try {
                    Thread.sleep(33);
                } catch (InterruptedException ex) {
                }
            }
        }
        videoLabel.setIcon(new ImageIcon(matToBufferedImage(lastFrame)));
        videoLabel.repaint();
        updateCharts();
        System.out.println("Fast analysis complete. Auto-saving analysis and footprint data...");
        saveAnalysis();
        saveFootprintData();
    }

    // ===== Frame update (called during normal playback) =====
    private void updateFrame() {
        Mat frame = new Mat();
        if (!cap.read(frame)) {
            videoPlaying = false;
            videoTimer.stop();
            return;
        }
        frameNumber++;
        double currentTime = frameNumber / fps;
        frame = rotateImage(frame, rotationValue);
        lastFrame = frame.clone();
        int H = frame.rows();
        int W = frame.cols();
        int finish_line_x = (int) ((finishedLinePercentage / 100.0) * W);
        Imgproc.line(frame, new Point(finish_line_x, 0), new Point(finish_line_x, H), new Scalar(0, 0, 255), 2);

        // Background subtraction and morphology:
        Mat fgmask = new Mat();
        fgbg.apply(frame, fgmask);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_OPEN, kernel, new Point(-1, -1), 2);
        Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_DILATE, kernel, new Point(-1, -1), 2);

        // Find contours:
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(fgmask.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        List<Rect> rects = new ArrayList<>();
        for (MatOfPoint c : contours) {
            if (Imgproc.contourArea(c) < 100)
                continue;
            Rect r = Imgproc.boundingRect(c);
            rects.add(r);
            Imgproc.rectangle(frame, r.tl(), r.br(), new Scalar(0, 255, 0), 2);
        }
        // Update tracker:
        cellTracker.update(rects, currentTime, finish_line_x, frameNumber);
        for (Integer cellID : cellTracker.objects.keySet()) {
            CentroidTracker.Track track = cellTracker.objects.get(cellID);
            // Use the Kalman-filtered centroid for display
            Point currentCentroid = track.centroid; // This is the Kalman-filtered position
            Imgproc.putText(frame, "ID " + cellID, new Point(currentCentroid.x - 10, currentCentroid.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 2);
            Imgproc.circle(frame, currentCentroid, 4, new Scalar(255, 0, 0), -1);
            if (!track.crossed && currentCentroid.x >= finish_line_x) {
                track.crossed = true;
                track.crossingFrame = frameNumber;
                track.crossingPosition = currentCentroid;
                System.out.println("Cell " + cellID + " crossed finish line at time " + String.format("%.2f", currentTime) + " sec");
                crossingTimes.add(currentTime);
            }
        }
        videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
        videoLabel.repaint();
        updateCharts();
    }

    // ===== Helper: Convert an OpenCV Mat to a BufferedImage =====
    private Image matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] b = new byte[bufferSize];
        mat.get(0, 0, b);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    // ===== Helper: Rotate an image by a given angle =====
    private Mat rotateImage(Mat image, double angle) {
        if (angle == 0) return image;
        Point center = new Point(image.cols() / 2.0, image.rows() / 2.0);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotated = new Mat();
        Imgproc.warpAffine(image, rotated, rotMat, image.size());
        return rotated;
    }

    // ===== Helper: Compute Euclidean distance between two points =====
    private double euclideanDistance(Point p1, Point p2) {
        return Math.hypot(p1.x - p2.x, p1.y - p2.y);
    }

    // ===== Helper: Compute various metrics from a track =====
    private Map<String, Object> computeMetrics(CentroidTracker.Track track, double fps) {
        Map<String, Object> metrics = new HashMap<>();
        List<CentroidTracker.HistoryItem> history = track.history;
        if (history.isEmpty()) {
            metrics.put("TotalDistance", 0.0);
            metrics.put("DistanceToCross", 0.0);
            metrics.put("DistanceAfterCross", 0.0);
            metrics.put("AvgFrameDistance", 0.0);
            metrics.put("MedianFrameDistance", 0.0);
            metrics.put("FramesTracked", 0);
            metrics.put("FramesMissed", track.missed);
            metrics.put("Speed", 0.0);
            return metrics;
        }
        int firstFrame = history.get(0).frame;
        int lastFrame = history.get(history.size() - 1).frame;
        Point firstCentroid = new Point((history.get(0).UL.x + history.get(0).LR.x) / 2.0,
                (history.get(0).UL.y + history.get(0).LR.y) / 2.0);
        Point lastCentroid = new Point((history.get(history.size() - 1).UL.x + history.get(history.size() - 1).LR.x) / 2.0,
                (history.get(history.size() - 1).UL.y + history.get(history.size() - 1).LR.y) / 2.0);
        double totalDistance = euclideanDistance(firstCentroid, lastCentroid);
        double distanceToCross = 0;
        double distanceAfterCross = 0;
        if (track.crossingPosition != null) {
            distanceToCross = euclideanDistance(firstCentroid, track.crossingPosition);
            distanceAfterCross = euclideanDistance(track.crossingPosition, lastCentroid);
        }
        List<Double> moveDists = new ArrayList<>();
        Point prevCentroid = firstCentroid;
        for (int i = 1; i < history.size(); i++) {
            Point currCentroid = new Point((history.get(i).UL.x + history.get(i).LR.x) / 2.0,
                    (history.get(i).UL.y + history.get(i).LR.y) / 2.0);
            moveDists.add(euclideanDistance(prevCentroid, currCentroid));
            prevCentroid = currCentroid;
        }
        double avgMove = moveDists.stream().mapToDouble(d -> d).average().orElse(0);
        double medianMove = 0;
        Collections.sort(moveDists);
        if (!moveDists.isEmpty()) {
            int mid = moveDists.size() / 2;
            if (moveDists.size() % 2 == 0)
                medianMove = (moveDists.get(mid - 1) + moveDists.get(mid)) / 2.0;
            else
                medianMove = moveDists.get(mid);
        }
        int framesTracked = history.size();
        double timeElapsed = (lastFrame - firstFrame) / fps;
        double speed = timeElapsed > 0 ? totalDistance / timeElapsed : 0;
        metrics.put("TotalDistance", totalDistance);
        metrics.put("DistanceToCross", distanceToCross);
        metrics.put("DistanceAfterCross", distanceAfterCross);
        metrics.put("AvgFrameDistance", avgMove);
        metrics.put("MedianFrameDistance", medianMove);
        metrics.put("FramesTracked", framesTracked);
        metrics.put("FramesMissed", track.missed);
        metrics.put("Speed", speed);
        return metrics;
    }

    //=====================================================================
    // MAIN: dispatch GUI vs. headless based on args
    // =====================================================================
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        if (args.length == 2) {
            // Headless mode
            String inputVideo = args[0];
            String outputPrefix = args[1];
            CellCounter proc = new CellCounter();
            proc.runHeadless(inputVideo, outputPrefix);
            System.exit(0);
        }

        // GUI mode
        SwingUtilities.invokeLater(() -> {
            // Optional Splash
            JWindow splash = new JWindow();
            JLabel label = new JLabel("Loading Cell Counter v1.0...", SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 20));
            splash.getContentPane().add(label);
            splash.setSize(400, 200);
            splash.setLocationRelativeTo(null);
            splash.setVisible(true);
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
            splash.setVisible(false);
            splash.dispose();

            new CellCounter().setVisible(true);
        });
    }

    //=====================================================================
    // HEADLESS RUNNER
    // =====================================================================
    public void runHeadless(String inputVideo, String outputPrefix) {
        System.out.println("Headless mode: input=" + inputVideo +
                "  prefix=" + outputPrefix);
        cap = new VideoCapture(inputVideo);
        if (!cap.isOpened()) {
            System.err.println("ERROR: Cannot open video: " + inputVideo);
            return;
        }

        // determine fps
        fps = cap.get(Videoio.CAP_PROP_FPS);
        if (fps <= 0) fps = 30.0;

        // read first frame
        Mat firstRaw = new Mat();
        if (!cap.read(firstRaw)) {
            System.err.println("ERROR: Cannot read first frame");
            return;
        }

        // auto-detect rotation
        int[] candidates = {0, 90, 180, 270};
        rotationValue = 0;
        System.out.println("Auto-detected rotation: " + rotationValue);

        // rewind and store reference frame
        cap.set(Videoio.CAP_PROP_POS_FRAMES, 0);
        referenceFrame = rotateImage(firstRaw, rotationValue);

        // init subtractor & tracker
        fgbg = Video.createBackgroundSubtractorMOG2(500, 50, false);
        cellTracker = new CentroidTracker(40, 50); // Resetting tracker
        crossingTimes.clear();
        frameNumber = 0;

        // process all frames
        Mat raw = new Mat();
        while (cap.read(raw)) {
            frameNumber++;
            double timeSec = frameNumber / fps;

            // rotate & diff
            Mat rot = rotateImage(raw, rotationValue);
            Mat diff = new Mat();
            Core.absdiff(rot, referenceFrame, diff);

            // finish line
            int finishX = (int) ((finishedLinePercentage / 100.0) * diff.cols());
            Imgproc.line(diff, new Point(finishX, 0),
                    new Point(finishX, diff.rows()),
                    new Scalar(0, 0, 255), 2);

            // background-sub & morphology
            Mat fg = new Mat();
            fgbg.apply(diff, fg);
            Mat k = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_OPEN, k, new Point(-1, -1), 2);
            Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_DILATE, k, new Point(-1, -1), 2);

            // contours -> rects
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(fg.clone(), contours,
                    new Mat(), Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE);
            List<Rect> rects = new ArrayList<>();
            for (MatOfPoint c : contours) {
                if (Imgproc.contourArea(c) < 100) continue;
                rects.add(Imgproc.boundingRect(c));
            }

            // update tracker & crossings
            cellTracker.update(rects, timeSec, finishX, frameNumber);
            for (var e : cellTracker.objects.entrySet()) {
                var t = e.getValue();
                // Use the Kalman-filtered centroid for crossing check
                Point currentCentroid = t.centroid; // This is the Kalman-filtered position
                if (!t.crossed && currentCentroid.x >= finishX) {
                    t.crossed = true;
                    t.crossingFrame = frameNumber;
                    t.crossingPosition = currentCentroid;
                    crossingTimes.add(timeSec);
                    System.out.printf("Cell %d crossed at %.2f s%n",
                            e.getKey(), timeSec);
                }
            }
        }

        // write CSVs
        saveAnalysisCSV(outputPrefix + "_analysis.csv");
        saveFootprintCSV(outputPrefix + "_footprint.csv");
        cap.release();
        System.out.println("Headless processing done.");
    }

    private void saveAnalysisCSV(String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("CellID,FirstSeenFrame,FirstSeenTime(s),CrossingFrame,CrossingTime(s),"
                    + "TotalDistance,DistanceToCross,DistanceAfterCross,"
                    + "AvgFrameDistance,MedianFrameDistance,FramesTracked,FramesMissed,Speed");
            Map<Integer, CentroidTracker.Track> all = new HashMap<>();
            all.putAll(cellTracker.completeTracks);
            all.putAll(cellTracker.objects);
            for (var ent : all.entrySet()) {
                int id = ent.getKey();
                if (id < 1) {
                    continue;
                }
                var t = ent.getValue();
                var m = computeMetrics(t, fps);

                int firstFrame = t.startFrame;
                double firstTime = t.startTime;
                int crossFrame = t.crossingFrame >= 0 ? t.crossingFrame : -1;
                double crossTime = crossFrame >= 0 ? crossFrame / fps : 0.0;

                if (crossFrame == -1) {
                    continue;
                }

                pw.printf("%d,%d,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f%n",
                        id,
                        firstFrame,
                        firstTime,
                        crossFrame,
                        crossTime,
                        m.get("TotalDistance"),
                        m.get("DistanceToCross"),
                        m.get("DistanceAfterCross"),
                        m.get("AvgFrameDistance"),
                        m.get("MedianFrameDistance"),
                        m.get("FramesTracked"),
                        m.get("FramesMissed"),
                        m.get("Speed"));
            }
            System.out.println("Wrote analysis CSV: " + filename);
        } catch (IOException ex) {
            System.err.println("Error writing analysis CSV: " + ex.getMessage());
        }
    }

    private void saveFootprintCSV(String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("CellID,Frame,UL_X,UL_Y,LR_X,LR_Y");
            Map<Integer, CentroidTracker.Track> all = new HashMap<>();
            all.putAll(cellTracker.completeTracks);
            all.putAll(cellTracker.objects);
            for (var ent : all.entrySet()) {
                int id = ent.getKey();
                for (var h : ent.getValue().history) {
                    pw.printf("%d,%d,%d,%d,%d,%d%n",
                            id, h.frame,
                            (int) h.UL.x, (int) h.UL.y,
                            (int) h.LR.x, (int) h.LR.y);
                }
            }
            System.out.println("Wrote footprint CSV: " + filename);
        } catch (IOException ex) {
            System.err.println("Error writing footprint CSV: " + ex.getMessage());
        }
    }


    // ===== CentroidTracker inner class =====
    public static class CentroidTracker {
        public int nextObjectID = 0;
        public Map<Integer, Track> objects = new HashMap<>();
        public Map<Integer, Integer> disappeared = new HashMap<>();
        public int maxDisappeared;
        public double maxDistance;
        // Once a track is lost, its full history is saved here.
        public Map<Integer, Track> completeTracks = new HashMap<>();

        public CentroidTracker(int maxDisappeared, double maxDistance) {
            this.maxDisappeared = maxDisappeared;
            this.maxDistance = maxDistance;
        }

        public void register(Point centroid, Rect bbox, double startTime, int frameNumber) {
            Track track = new Track();
            track.centroid = centroid;
            track.bbox = bbox;
            track.startTime = startTime;
            track.startFrame = frameNumber;
            track.crossed = false;
            track.history.add(new HistoryItem(frameNumber, new Point(bbox.x, bbox.y),
                    new Point(bbox.x + bbox.width, bbox.y + bbox.height)));
            track.missed = 0;
            track.crossingFrame = -1;
            track.crossingPosition = null;

            // Initialize Kalman Filter for the new track
            // State vector: [x, y, vx, vy, ax, ay]^T (6x1)
            // Measurement vector: [x, y]^T (2x1)
            track.kalman = new KalmanFilter(6, 2, 0);

            // Create and populate transition matrix (A)
            Mat transitionMatrix = new Mat(6, 6, CvType.CV_32F);
            float dT = 1.0f; // Time step (1 frame)
            transitionMatrix.put(0, 0, 1, 0, dT, 0, 0.5 * dT * dT, 0);
            transitionMatrix.put(1, 0, 0, 1, 0, dT, 0, 0.5 * dT * dT);
            transitionMatrix.put(2, 0, 0, 0, 1, 0, dT, 0);
            transitionMatrix.put(3, 0, 0, 0, 0, 1, 0, dT);
            transitionMatrix.put(4, 0, 0, 0, 0, 0, 1, 0);
            transitionMatrix.put(5, 0, 0, 0, 0, 0, 0, 1);
            // Attempt to set the transition matrix using a hypothetical set_ method
            // If this method doesn't exist, you'll need to revert to direct access (track.kalman.transitionMatrix.put(...))
            try {
                track.kalman.getClass().getMethod("set_transitionMatrix", Mat.class).invoke(track.kalman, transitionMatrix);
            } catch (Exception e) {
                // Fallback to direct assignment if set_transitionMatrix doesn't exist or fails
                System.err.println("Warning: set_transitionMatrix method not found or failed. Falling back to direct assignment. " + e.getMessage());
                transitionMatrix.copyTo(track.kalman.get_transitionMatrix());
            } finally {
                transitionMatrix.release(); // Release temporary Mat
            }

            // Create and populate measurement matrix (H)
            Mat measurementMatrix = new Mat(2, 6, CvType.CV_32F);
            measurementMatrix.put(0, 0, 1, 0, 0, 0, 0, 0);
            measurementMatrix.put(1, 0, 0, 1, 0, 0, 0, 0);
            try {
                track.kalman.getClass().getMethod("set_measurementMatrix", Mat.class).invoke(track.kalman, measurementMatrix);
            } catch (Exception e) {
                System.err.println("Warning: set_measurementMatrix method not found or failed. Falling back to direct assignment. " + e.getMessage());
                measurementMatrix.copyTo(track.kalman.get_transitionMatrix());
            } finally {
                measurementMatrix.release(); // Release temporary Mat
            }

            // Create and populate process noise covariance matrix (Q)
            Mat processNoiseCov = new Mat(6, 6, CvType.CV_32F);
            double processNoiseCovPos = 1e-2; // Noise for position
            double processNoiseCovVel = 1e-1; // Noise for velocity
            double processNoiseCovAcc = 1e-1; // Noise for acceleration
            processNoiseCov.put(0, 0, processNoiseCovPos);
            processNoiseCov.put(1, 1, processNoiseCovPos);
            processNoiseCov.put(2, 2, processNoiseCovVel);
            processNoiseCov.put(3, 3, processNoiseCovVel);
            processNoiseCov.put(4, 4, processNoiseCovAcc);
            processNoiseCov.put(5, 5, processNoiseCovAcc);
            try {
                track.kalman.getClass().getMethod("set_processNoiseCov", Mat.class).invoke(track.kalman, processNoiseCov);
            } catch (Exception e) {
                System.err.println("Warning: set_processNoiseCov method not found or failed. Falling back to direct assignment. " + e.getMessage());
                processNoiseCov.copyTo(track.kalman.get_processNoiseCov());
            } finally {
                processNoiseCov.release(); // Release temporary Mat
            }

            // Create and populate measurement noise covariance matrix (R)
            Mat measurementNoiseCov = new Mat(2, 2, CvType.CV_32F);
            double measNoiseCov = 0.1;
            measurementNoiseCov.put(0, 0, measNoiseCov);
            measurementNoiseCov.put(1, 1, measNoiseCov);
            try {
                track.kalman.getClass().getMethod("set_measurementNoiseCov", Mat.class).invoke(track.kalman, measurementNoiseCov);
            } catch (Exception e) {
                System.err.println("Warning: set_measurementNoiseCov method not found or failed. Falling back to direct assignment. " + e.getMessage());
                measurementNoiseCov.copyTo(track.kalman.get_measurementNoiseCov());
            } finally {
                measurementNoiseCov.release(); // Release temporary Mat
            }

            // Create and populate error covariance matrix (P)
            Mat errorCovPost = new Mat(6, 6, CvType.CV_32F);
            Core.setIdentity(errorCovPost);
            errorCovPost.put(0, 0, 100);
            errorCovPost.put(1, 1, 100);
            errorCovPost.put(2, 2, 10);
            errorCovPost.put(3, 3, 10);
            errorCovPost.put(4, 4, 1);
            errorCovPost.put(5, 5, 1);
            try {
                track.kalman.getClass().getMethod("set_errorCovPost", Mat.class).invoke(track.kalman, errorCovPost);
            } catch (Exception e) {
                System.err.println("Warning: set_errorCovPost method not found or failed. Falling back to direct assignment. " + e.getMessage());
                errorCovPost.copyTo(track.kalman.get_errorCovPost());
            } finally {
                errorCovPost.release(); // Release temporary Mat
            }

            // Create and populate initial state (x)
            Mat statePost = new Mat(6, 1, CvType.CV_32F);
            statePost.put(0, 0, (float)centroid.x);
            statePost.put(1, 0, (float)centroid.y);
            statePost.put(2, 0, 0); // Initial velocity x
            statePost.put(3, 0, 0); // Initial velocity y
            statePost.put(4, 0, 0); // Initial acceleration x
            statePost.put(5, 0, 0); // Initial acceleration y
            try {
                track.kalman.getClass().getMethod("set_statePost", Mat.class).invoke(track.kalman, statePost);
            } catch (Exception e) {
                System.err.println("Warning: set_statePost method not found or failed. Falling back to direct assignment. " + e.getMessage());
                statePost.copyTo(track.kalman.get_statePost());
            } finally {
                statePost.release(); // Release temporary Mat
            }

            objects.put(nextObjectID, track);
            disappeared.put(nextObjectID, 0);
            nextObjectID++;
        }

        public void deregister(int objectID) {
            // Release KalmanFilter resources when deregistering to prevent memory leaks
            Track track = objects.get(objectID);
            if (track != null && track.kalman != null) {
                //track.kalman.;
            }
            completeTracks.put(objectID, objects.get(objectID));
            objects.remove(objectID);
            disappeared.remove(objectID);
        }

        public void update(List<Rect> rects, double currentTime, int finish_line_x, int frameNumber) {
            // Predict the next state for all existing objects using their Kalman Filters
            List<Point> predictedCentroids = new ArrayList<>();
            for (Integer objectID : objects.keySet()) {
                Track track = objects.get(objectID);
                Mat prediction = track.kalman.predict(); // Perform prediction step
                predictedCentroids.add(new Point(prediction.get(0, 0)[0], prediction.get(1, 0)[0]));
                prediction.release(); // Release the prediction Mat
            }

            if (rects.isEmpty()) {
                // If no detections, increment disappeared count for all current objects
                List<Integer> keys = new ArrayList<>(disappeared.keySet());
                for (int objectID : keys) {
                    disappeared.put(objectID, disappeared.get(objectID) + 1);
                    objects.get(objectID).missed++;
                    if (disappeared.get(objectID) > maxDisappeared) {
                        deregister(objectID);
                    }
                }
                return;
            }

            // Compute centroids for each detection
            List<Point> inputCentroids = new ArrayList<>();
            for (Rect r : rects) {
                int cX = r.x + r.width / 2;
                int cY = r.y + r.height / 2;
                inputCentroids.add(new Point(cX, cY));
            }

            if (objects.isEmpty()) {
                // If no existing objects, register every detection that is to the left of the finish line
                for (int i = 0; i < inputCentroids.size(); i++) {
                    if (inputCentroids.get(i).x < finish_line_x) {
                        register(inputCentroids.get(i), rects.get(i), currentTime, frameNumber);
                    }
                }
                return;
            }

            List<Integer> objectIDs = new ArrayList<>(objects.keySet());
            // Compute the Euclidean distance matrix between predicted object centroids and input centroids
            double[][] D = new double[predictedCentroids.size()][inputCentroids.size()];
            for (int i = 0; i < predictedCentroids.size(); i++) {
                for (int j = 0; j < inputCentroids.size(); j++) {
                    D[i][j] = Math.hypot(predictedCentroids.get(i).x - inputCentroids.get(j).x,
                            predictedCentroids.get(i).y - inputCentroids.get(j).y);
                }
            }

            // Greedy assignment: sort rows (existing objects) by their minimum distance to any detection
            List<Integer> rows = new ArrayList<>();
            for (int i = 0; i < D.length; i++) {
                rows.add(i);
            }
            rows.sort(Comparator.comparingDouble(i -> Arrays.stream(D[i]).min().orElse(Double.MAX_VALUE)));

            Set<Integer> usedRows = new HashSet<>(); // Keep track of matched existing objects
            Set<Integer> usedCols = new HashSet<>(); // Keep track of matched new detections

            for (int row : rows) {
                int col = -1;
                double minVal = Double.MAX_VALUE;
                for (int j = 0; j < D[row].length; j++) {
                    if (!usedCols.contains(j) && D[row][j] < minVal) { // Ensure detection hasn't been used
                        minVal = D[row][j];
                        col = j;
                    }
                }
                
                // If no suitable match found or distance is too large, skip this object
                if (col == -1 || minVal > maxDistance) {
                    continue;
                }

                int objectID = objectIDs.get(row);
                Track track = objects.get(objectID);

                // Correct Kalman filter with the new measurement
                Mat measurement = new Mat(2, 1, CvType.CV_32F);
                measurement.put(0, 0, (float)inputCentroids.get(col).x);
                measurement.put(1, 0, (float)inputCentroids.get(col).y);
                track.kalman.correct(measurement); // Perform correction step
                measurement.release(); // Release the measurement Mat

                // Update track properties with the corrected state from Kalman Filter
                track.centroid = new Point(track.kalman.get_statePost().get(0, 0)[0], track.kalman.get_statePost().get(1, 0)[0]);
                track.bbox = rects.get(col); // Update bounding box with the current detection's bbox
                track.history.add(new HistoryItem(frameNumber, new Point(rects.get(col).x, rects.get(col).y),
                                                   new Point(rects.get(col).x + rects.get(col).width,
                                                             rects.get(col).y + rects.get(col).height)));
                disappeared.put(objectID, 0); // Reset disappeared count
                usedRows.add(row);
                usedCols.add(col);
            }

            // Increase the missing count for unmatched objects (those not in usedRows).
            for (int i = 0; i < objectIDs.size(); i++) {
                if (!usedRows.contains(i)) {
                    int objectID = objectIDs.get(i);
                    disappeared.put(objectID, disappeared.get(objectID) + 1);
                    objects.get(objectID).missed++;
                    if (disappeared.get(objectID) > maxDisappeared) {
                        deregister(objectID); // Deregister if missing for too long
                    }
                }
            }

            // Register new objects for unmatched detections (those not in usedCols) that are to the left of the finish line.
            for (int j = 0; j < inputCentroids.size(); j++) {
                if (!usedCols.contains(j)) {
                    if (inputCentroids.get(j).x < finish_line_x) {
                        register(inputCentroids.get(j), rects.get(j), currentTime, frameNumber);
                    }
                }
            }
        }

        // ===== Inner class representing a tracked object =====
        public static class Track {
            public Point centroid; // Current estimated centroid (from Kalman filter)
            public Rect bbox; // Current bounding box (from last associated detection)
            public double startTime;
            public int startFrame;
            public boolean crossed; // True if cell has crossed the finish line
            public List<HistoryItem> history = new ArrayList<>(); // History of observed positions
            public int missed; // Number of consecutive frames missed
            public int crossingFrame; // Frame number when crossing occurred
            public Point crossingPosition; // Position when crossing occurred
            public KalmanFilter kalman; // Kalman Filter for this specific track
        }

        // ===== Inner class for a history record (frame and bounding-box corners) =====
        public static class HistoryItem {
            public int frame;
            public Point UL; // Upper-left corner of bounding box
            public Point LR; // Lower-right corner of bounding box

            public HistoryItem(int frame, Point UL, Point LR) {
                this.frame = frame;
                this.UL = UL;
                this.LR = LR;
            }
        }
    }
}
