package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.export.ExportMetadata;
import com.prolymphname.cellcounter.simulation.CellSimulationGUI;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import com.prolymphname.cellcounter.trackingadapter.TrackerAlgorithm;
import com.prolymphname.cellcounter.ui.AppIcon;
import com.prolymphname.cellcounter.ui.CardPanel;
import com.prolymphname.cellcounter.ui.ChartRefreshController;
import com.prolymphname.cellcounter.ui.DetectionTunerDialog;
import com.prolymphname.cellcounter.ui.GradientPanel;
import com.prolymphname.cellcounter.ui.StartupSplashWindow;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.configureIconOnlyButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createCard;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createChipLabel;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createPrimaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createSecondaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createToggleButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.enforceButtonSize;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.ACCENT;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.ACCENT_DEEP;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.APP_ICON_FILE_NAME;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.CHIP_ACTIVE;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.CHIP_IDLE;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.CHIP_PLAYING;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.CHIP_WARNING;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BODY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BUTTON;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_DISPLAY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_H2;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_LABEL;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_L;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_M;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_S;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XL;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XXS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_PRIMARY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_SECONDARY;

public class CellCounterGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final double DEFAULT_VIDEO_RATE = 1.0;

    private final CellCounterApplicationService appService;
    private final ChartRefreshController chartRefreshController = new ChartRefreshController();

    private boolean videoPlaying = false;
    private boolean paused = false;

    private JLabel videoLabel;
    private JLabel playbackRateValueLabel;
    private JLabel pipelineStateLabel;
    private ChartPanel trackStartTimeChartPanel;
    private ChartPanel speedDistributionChartPanel;

    private JButton analyzeButton;
    private JButton fastButton;
    private JButton playButton;
    private JButton frameForwardButton;
    private JButton resetButton;
    private JButton saveResultsButton;
    private JButton simulatorButton;
    private JButton tuneDetectionButton;
    private JButton helpButton;
    private JToggleButton mog2ViewButton;
    private JComboBox<TrackerAlgorithm> trackerAlgorithmCombo;
    private JSlider playbackRateSlider;
    private JSlider videoPositionSlider;
    private JLabel videoPositionValueLabel;
    private boolean suppressVideoPositionEvents = false;
    private SwingWorker<Mat, Void> seekWorker;

    private Timer videoTimer;

    private final Icon playIcon = new AppIcon(AppIcon.Kind.PLAY, Color.WHITE);
    private final Icon pauseIcon = new AppIcon(AppIcon.Kind.PAUSE, Color.WHITE);

    public static void showStartupSplash(int durationMillis, Runnable onComplete) {
        StartupSplashWindow splashWindow = new StartupSplashWindow(Math.max(150, durationMillis), onComplete);
        splashWindow.showSplash();
    }

    public CellCounterGUI() {
        this(new CellCounterApplicationService());
    }

    public CellCounterGUI(CellCounterApplicationService appService) {
        this.appService = appService;
        initUI();
    }

    private void initUI() {
        setTitle("Cell Counter | Biomaterials Intelligence");
        applyWindowIcon();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 760));
        setPreferredSize(new Dimension(1480, 920));

        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(SPACE_L, SPACE_L));
        root.setBorder(new EmptyBorder(SPACE_L, SPACE_XL, SPACE_L, SPACE_XL));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(SPACE_L, SPACE_L));
        body.setOpaque(false);
        body.add(buildControlsCard(), BorderLayout.NORTH);

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildVideoCard(), buildAnalyticsColumn());
        contentSplit.setOpaque(false);
        contentSplit.setBorder(null);
        contentSplit.setResizeWeight(0.58);
        contentSplit.setContinuousLayout(true);
        contentSplit.setDividerSize(9);
        body.add(contentSplit, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);

        bindActions();
        setInitialControlState();
        setPipelineState("Idle", CHIP_IDLE);
        setPlayButtonPlaying(false);

        videoTimer = new Timer(33, e -> {
            if (appService.isVideoSuccessfullyInitialized() && videoPlaying && !paused) {
                updateFrame(false);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                appService.releaseVideo();
            }
        });

        pack();
        setLocationRelativeTo(null);
        contentSplit.setDividerLocation(0.58);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(SPACE_M, SPACE_M));
        header.setOpaque(false);

        JLabel title = new JLabel("Biomaterials Cell Counter");
        title.setFont(FONT_DISPLAY);
        title.setForeground(TEXT_PRIMARY);

        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        titleGroup.add(title);

        pipelineStateLabel = createChipLabel("Idle", CHIP_IDLE);
        helpButton = createSecondaryButton("Help", new AppIcon(AppIcon.Kind.HELP, Color.WHITE));
        helpButton.setFont(FONT_LABEL);
        enforceButtonSize(helpButton, 94);

        JPanel statusGroup = new JPanel();
        statusGroup.setOpaque(false);
        statusGroup.setLayout(new BoxLayout(statusGroup, BoxLayout.X_AXIS));
        statusGroup.add(helpButton);
        statusGroup.add(Box.createHorizontalStrut(SPACE_XS));
        statusGroup.add(pipelineStateLabel);

        header.add(titleGroup, BorderLayout.WEST);
        header.add(statusGroup, BorderLayout.EAST);
        return header;
    }

    private JPanel buildControlsCard() {
        CardPanel controlsCard = createCard("", "", false);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, SPACE_XS, 0));
        topRow.setOpaque(false);
        JPanel secondRow = new JPanel(new FlowLayout(FlowLayout.LEFT, SPACE_XS, 0));
        secondRow.setOpaque(false);

        analyzeButton = createPrimaryButton("Open Video", new AppIcon(AppIcon.Kind.SEARCH, Color.WHITE));
        fastButton = createSecondaryButton("Fast Analyze", new AppIcon(AppIcon.Kind.BOLT, Color.WHITE));
        playButton = createPrimaryButton("Play/Analyze", playIcon);
        frameForwardButton = createSecondaryButton("", new AppIcon(AppIcon.Kind.STEP, Color.WHITE));
        resetButton = createSecondaryButton("", new AppIcon(AppIcon.Kind.RESET, Color.WHITE));
        saveResultsButton = createPrimaryButton("Save Results", new AppIcon(AppIcon.Kind.FILE, Color.WHITE));
        simulatorButton = createSecondaryButton("Simulator", new AppIcon(AppIcon.Kind.SIMULATOR, Color.WHITE));

        playButton.setToolTipText("Play / Pause");
        playButton.setHorizontalTextPosition(SwingConstants.LEFT);
        playButton.setIconTextGap(SPACE_XS);
        configureIconOnlyButton(frameForwardButton, "Step");
        configureIconOnlyButton(resetButton, "Replay");

        videoPositionSlider = new JSlider(0, 0, 0);
        videoPositionSlider.setOpaque(false);
        videoPositionSlider.setPreferredSize(new Dimension(260, 28));
        videoPositionSlider.setMaximumSize(new Dimension(260, 28));
        videoPositionValueLabel = createChipLabel(formatFrameChipText(0, 0), CHIP_IDLE);
        videoPositionValueLabel.setFont(FONT_LABEL);
        videoPositionValueLabel.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        videoPositionValueLabel.setPreferredSize(new Dimension(124, 24));

        playbackRateSlider = new JSlider(10, 500, 100);
        playbackRateSlider.setOpaque(false);
        playbackRateSlider.setPaintTicks(false);
        playbackRateSlider.setPaintLabels(false);
        playbackRateSlider.setFont(FONT_LABEL);
        playbackRateSlider.setPreferredSize(new Dimension(140, 28));
        playbackRateSlider.setMaximumSize(new Dimension(140, 28));

        playbackRateValueLabel = createChipLabel(formatPlaybackSpeedText(DEFAULT_VIDEO_RATE), CHIP_IDLE);
        playbackRateValueLabel.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        playbackRateValueLabel.setPreferredSize(new Dimension(176, 24));

        tuneDetectionButton = createSecondaryButton("Tune Detection", new AppIcon(AppIcon.Kind.SLIDERS, Color.WHITE));
        tuneDetectionButton.setFont(FONT_LABEL);
        mog2ViewButton = createToggleButton("Mask View", new AppIcon(AppIcon.Kind.EYE, Color.WHITE));

        trackerAlgorithmCombo = new JComboBox<>(TrackerAlgorithm.values());
        trackerAlgorithmCombo.setSelectedItem(appService.getTrackingConfiguration().getTrackerAlgorithm());
        trackerAlgorithmCombo.setFont(FONT_LABEL);
        trackerAlgorithmCombo.setPreferredSize(new Dimension(190, 28));
        trackerAlgorithmCombo.setMaximumSize(new Dimension(190, 28));
        trackerAlgorithmCombo.setOpaque(false);
        trackerAlgorithmCombo.setToolTipText("Tracker assignment algorithm");

        enforceButtonSize(analyzeButton, 136);
        enforceButtonSize(fastButton, 136);
        enforceButtonSize(playButton, 146);
        enforceButtonSize(frameForwardButton, 52);
        enforceButtonSize(resetButton, 52);
        enforceButtonSize(saveResultsButton, 146);
        enforceButtonSize(simulatorButton, 118);
        enforceButtonSize(tuneDetectionButton, 152);
        enforceButtonSize(mog2ViewButton, 126);

        topRow.add(analyzeButton);
        topRow.add(fastButton);
        topRow.add(playButton);
        topRow.add(frameForwardButton);
        topRow.add(resetButton);
        topRow.add(saveResultsButton);
        topRow.add(simulatorButton);

        secondRow.add(videoPositionValueLabel);
        secondRow.add(videoPositionSlider);
        secondRow.add(playbackRateValueLabel);
        secondRow.add(playbackRateSlider);
        secondRow.add(tuneDetectionButton);
        secondRow.add(mog2ViewButton);
        secondRow.add(trackerAlgorithmCombo);

        content.add(topRow);
        content.add(Box.createVerticalStrut(SPACE_XS));
        content.add(secondRow);
        controlsCard.add(content, BorderLayout.CENTER);
        return controlsCard;
    }

    private JPanel buildVideoCard() {
        CardPanel videoCard = createCard("", "", false);

        videoLabel = new JLabel("No video loaded. Click Open Video to begin.", SwingConstants.CENTER);
        videoLabel.setFont(FONT_BODY);
        videoLabel.setForeground(new Color(215, 230, 250));
        videoLabel.setOpaque(true);
        videoLabel.setBackground(new Color(7, 19, 40));
        videoLabel.setPreferredSize(new Dimension(840, 560));

        JPanel videoFrame = new JPanel(new BorderLayout());
        videoFrame.setOpaque(true);
        videoFrame.setBackground(new Color(7, 19, 40));
        videoFrame.setBorder(new LineBorder(new Color(82, 129, 193, 140), 1, true));
        videoFrame.add(videoLabel, BorderLayout.CENTER);

        videoCard.add(videoFrame, BorderLayout.CENTER);
        return videoCard;
    }

    private JPanel buildAnalyticsColumn() {
        JPanel rightColumn = new JPanel(new GridLayout(2, 1, 0, SPACE_M));
        rightColumn.setOpaque(false);

        trackStartTimeChartPanel = createCombinedChart(new double[] {}, "Track Start Distribution", "Time (sec)", "Count", 1.0);
        speedDistributionChartPanel = createCombinedChart(new double[] {}, "Speed Distribution", "Speed (px/s)", "Count", 5.0);

        CardPanel trackCard = createCard("", "", false);
        trackCard.add(trackStartTimeChartPanel, BorderLayout.CENTER);
        trackCard.setMinimumSize(new Dimension(360, 260));

        CardPanel speedCard = createCard("", "", false);
        speedCard.add(speedDistributionChartPanel, BorderLayout.CENTER);
        speedCard.setMinimumSize(new Dimension(360, 260));

        rightColumn.add(trackCard);
        rightColumn.add(speedCard);
        return rightColumn;
    }

    private void bindActions() {
        simulatorButton.addActionListener(e -> SwingUtilities.invokeLater(() -> new CellSimulationGUI().setVisible(true)));
        tuneDetectionButton.addActionListener(e -> handleTuneDetection());
        helpButton.addActionListener(e -> openHelpDocumentation());
        analyzeButton.addActionListener(e -> handleAnalyzeVideo());
        playButton.addActionListener(e -> handlePlayPauseToggle());
        frameForwardButton.addActionListener(e -> handleFrameForward());
        resetButton.addActionListener(e -> handleResetVideo());
        fastButton.addActionListener(e -> handleFastAnalyze());
        saveResultsButton.addActionListener(e -> handleSaveResults());
        mog2ViewButton.addItemListener(this::handleMOG2Toggle);
        trackerAlgorithmCombo.addActionListener(e -> handleTrackerAlgorithmChange());
        playbackRateSlider.addChangeListener(e -> handlePlaybackRateChange());
        videoPositionSlider.addChangeListener(e -> handleVideoPositionSliderChange());
    }

    private void setInitialControlState() {
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        playbackRateValueLabel.setText(formatPlaybackSpeedText(DEFAULT_VIDEO_RATE));
        chartRefreshController.configureForFps(appService.getFps());
        refreshVideoPositionControls();
    }

    private void handleTrackerAlgorithmChange() {
        TrackerAlgorithm selected = (TrackerAlgorithm) trackerAlgorithmCombo.getSelectedItem();
        if (selected == null) return;
        TrackingConfiguration current = appService.getTrackingConfiguration();
        if (current.getTrackerAlgorithm() == selected) return;

        TrackingConfiguration updated = new TrackingConfiguration(
                current.getMaxFramesDisappeared(),
                current.getMinContourArea(),
                current.getMaxRectCircumference(),
                current.getMaxVerticalDisplacementPixels(),
                current.getMinHorizontalMovementPixels(),
                current.getMaxAssociationDistancePixels(),
                current.getMog2HistoryFrames(),
                current.getMog2VarThreshold(),
                current.isMog2DetectShadows(),
                current.getMorphologyKernelSize(),
                current.getMorphologyOpenIterations(),
                current.getMorphologyDilateIterations(),
                current.getNormalizedMaskThreshold(),
                selected);

        boolean wasPlaying = videoPlaying && !paused;
        videoTimer.stop();
        videoPlaying = false;
        setPlayButtonPlaying(false);

        appService.setTrackingConfiguration(updated);

        if (appService.isVideoSuccessfullyInitialized()) {
            paused = true;
            mog2ViewButton.setSelected(false);
            appService.setDisplayMOG2Foreground(false);
            refreshCurrentVideoFrame();
            refreshChartsNow();
            refreshVideoPositionControls();
            setPipelineState("Configured", CHIP_ACTIVE);
        }
    }

    private void handleTuneDetection() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            JOptionPane.showMessageDialog(this,
                    "Load a video first. The tuner previews segmentation on the current paused frame.",
                    "No Video",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (videoPlaying && !paused) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Paused", CHIP_WARNING);
        }
        openDetectionTunerDialog();
    }

    private void openDetectionTunerDialog() {
        new DetectionTunerDialog(
                this,
                appService,
                appService.getTrackingConfiguration(),
                appService.getTrackingConfiguration().getTrackerAlgorithm(),
                previewFrame -> {
                    if (previewFrame != null && !previewFrame.empty()) {
                        videoLabel.setIcon(new ImageIcon(matToBufferedImage(previewFrame)));
                        videoLabel.setText(null);
                    }
                    if (previewFrame != null) {
                        previewFrame.release();
                    }
                },
                updated -> {
                    appService.setTrackingConfiguration(updated);
                    mog2ViewButton.setSelected(false);
                    appService.setDisplayMOG2Foreground(false);
                    refreshCurrentVideoFrame();
                    refreshChartsNow();
                    paused = true;
                    videoPlaying = false;
                    videoTimer.stop();
                    setPlayButtonPlaying(false);
                    setPipelineState("Configured", CHIP_ACTIVE);
                },
                this::refreshCurrentVideoFrame)
                .open();
    }

    private void refreshCurrentVideoFrame() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            return;
        }
        Mat frame = appService.getLastProcessedFrame();
        if (frame != null && !frame.empty()) {
            videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
            videoLabel.setText(null);
            videoLabel.repaint();
        }
    }

    private void refreshVideoPositionControls() {
        if (videoPositionSlider == null || videoPositionValueLabel == null) {
            return;
        }

        if (!appService.isVideoSuccessfullyInitialized()) {
            suppressVideoPositionEvents = true;
            videoPositionSlider.setMinimum(0);
            videoPositionSlider.setMaximum(0);
            videoPositionSlider.setValue(0);
            videoPositionSlider.setEnabled(false);
            suppressVideoPositionEvents = false;
            videoPositionValueLabel.setText(formatFrameChipText(0, 0));
            return;
        }

        int total = Math.max(1, appService.getFrameCount());
        int currentFrameIndex = Math.max(0, appService.getCurrentFrameNumber() - 1);
        int current = clampInt(currentFrameIndex, 0, total - 1);

        suppressVideoPositionEvents = true;
        videoPositionSlider.setMinimum(0);
        videoPositionSlider.setMaximum(total - 1);
        videoPositionSlider.setValue(current);
        videoPositionSlider.setEnabled(true);
        suppressVideoPositionEvents = false;

        videoPositionValueLabel.setText(formatFrameChipText(current + 1, total));
    }

    private String formatFrameChipText(int current, int total) {
        return "Frame: " + current + "/" + total;
    }

    private String formatPlaybackSpeedText(double rate) {
        return String.format("Playback Speed: %.1fx", rate);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ChartPanel createCombinedChart(double[] data, String title, String xAxisLabel, String yAxisLabel, double binSize) {
        if (data == null || data.length == 0) {
            data = new double[] { 0 };
        }

        Arrays.sort(data);

        HistogramDataset histDataset = new HistogramDataset();
        double maxValue = Arrays.stream(data).max().orElse(binSize);
        int bins = Math.max(1, (int) Math.ceil(maxValue / binSize));
        histDataset.addSeries(title, data, bins);

        XYSeries cdfSeries = new XYSeries("CDF");
        for (int i = 0; i < data.length; i++) {
            cdfSeries.add(data[i], (double) (i + 1) / data.length);
        }
        XYSeriesCollection cdfDataset = new XYSeriesCollection(cdfSeries);

        NumberAxis xAxis = new NumberAxis(xAxisLabel);
        NumberAxis yAxisLeft = new NumberAxis(yAxisLabel);
        NumberAxis yAxisRight = new NumberAxis("Cummulate Distribution");
        yAxisRight.setRange(0.0, 1.0);

        xAxis.setLabelFont(FONT_LABEL);
        xAxis.setTickLabelFont(FONT_LABEL);
        xAxis.setLabelPaint(TEXT_SECONDARY);
        xAxis.setTickLabelPaint(TEXT_SECONDARY);
        xAxis.setAxisLinePaint(new Color(97, 136, 194));
        xAxis.setTickMarkPaint(new Color(97, 136, 194));
        yAxisLeft.setLabelFont(FONT_LABEL);
        yAxisLeft.setTickLabelFont(FONT_LABEL);
        yAxisLeft.setLabelPaint(TEXT_SECONDARY);
        yAxisLeft.setTickLabelPaint(TEXT_SECONDARY);
        yAxisLeft.setAxisLinePaint(new Color(97, 136, 194));
        yAxisLeft.setTickMarkPaint(new Color(97, 136, 194));
        yAxisRight.setLabelFont(FONT_LABEL);
        yAxisRight.setTickLabelFont(FONT_LABEL);
        yAxisRight.setLabelPaint(TEXT_SECONDARY);
        yAxisRight.setTickLabelPaint(TEXT_SECONDARY);
        yAxisRight.setAxisLinePaint(new Color(97, 136, 194));
        yAxisRight.setTickMarkPaint(new Color(97, 136, 194));

        XYBarRenderer histRenderer = new XYBarRenderer();
        histRenderer.setSeriesPaint(0, new Color(58, 171, 255, 188));
        histRenderer.setBarPainter(new StandardXYBarPainter());
        histRenderer.setShadowVisible(false);
        histRenderer.setMargin(0.03);

        XYLineAndShapeRenderer cdfRenderer = new XYLineAndShapeRenderer();
        cdfRenderer.setSeriesPaint(0, new Color(116, 223, 255));
        cdfRenderer.setSeriesStroke(0, new BasicStroke(2.8f));
        cdfRenderer.setSeriesShapesVisible(0, false);

        XYPlot plot = new XYPlot();
        plot.setDomainAxis(xAxis);
        plot.setBackgroundPaint(new Color(7, 20, 43));
        plot.setDomainGridlinePaint(new Color(76, 111, 166, 132));
        plot.setRangeGridlinePaint(new Color(76, 111, 166, 132));
        plot.setOutlineVisible(false);

        plot.setDataset(0, histDataset);
        plot.setRenderer(0, histRenderer);
        plot.setRangeAxis(0, yAxisLeft);
        plot.mapDatasetToRangeAxis(0, 0);

        plot.setDataset(1, cdfDataset);
        plot.setRenderer(1, cdfRenderer);
        plot.setRangeAxis(1, yAxisRight);
        plot.mapDatasetToRangeAxis(1, 1);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

        JFreeChart chart = new JFreeChart(title, FONT_H2, plot, false);
        chart.setBackgroundPaint(new Color(0, 0, 0, 0));
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(TEXT_SECONDARY);
        }

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setOpaque(false);
        chartPanel.setBackground(new Color(0, 0, 0, 0));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setPreferredSize(new Dimension(420, 210));
        return chartPanel;
    }

    private void updateCharts() {
        double[] startTimesArray = appService.getTrackStartTimes().stream().mapToDouble(Double::doubleValue).toArray();
        double[] speedsArray = appService.getSpeeds().stream().mapToDouble(Double::doubleValue).toArray();

        ChartPanel newStartTimeChart = createCombinedChart(startTimesArray, "Track Start Distribution", "Time (sec)", "Count", 1.0);
        if (trackStartTimeChartPanel != null) {
            trackStartTimeChartPanel.setChart(newStartTimeChart.getChart());
        }

        ChartPanel newSpeedChart = createCombinedChart(speedsArray, "Speed Distribution", "Speed (px/s)", "Count", 5.0);
        if (speedDistributionChartPanel != null) {
            speedDistributionChartPanel.setChart(newSpeedChart.getChart());
        }
    }

    private void syncChartRefreshIntervalWithVideo() {
        chartRefreshController.configureForFps(appService.getFps());
    }

    private void refreshChartsNow() {
        updateCharts();
        chartRefreshController.markRefreshedAtFrame(appService.getCurrentFrameNumber());
    }

    private void refreshChartsIfDue() {
        if (chartRefreshController.shouldRefreshAtFrame(appService.getCurrentFrameNumber())) {
            refreshChartsNow();
        }
    }

    private void handleAnalyzeVideo() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String path = chooser.getSelectedFile().getAbsolutePath();
        if (appService.initializeVideo(path)) {
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            mog2ViewButton.setSelected(false);
            playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
            updateVideoTimerDelay(DEFAULT_VIDEO_RATE);
            syncChartRefreshIntervalWithVideo();
            setPipelineState("Loaded", CHIP_ACTIVE);

            Mat firstFrame = appService.getLastProcessedFrame();
            if (firstFrame != null && !firstFrame.empty()) {
                videoLabel.setText(null);
                videoLabel.setIcon(new ImageIcon(matToBufferedImage(firstFrame)));
            } else {
                videoLabel.setIcon(null);
                videoLabel.setText("Unable to render first frame.");
                JOptionPane.showMessageDialog(this, "Video loaded, but the first frame could not be rendered.",
                        "Display Error", JOptionPane.WARNING_MESSAGE);
            }
            refreshChartsNow();
            refreshVideoPositionControls();
            return;
        }

        videoPlaying = false;
        paused = false;
        setPlayButtonPlaying(false);
        setPipelineState("Idle", CHIP_IDLE);
        refreshVideoPositionControls();

        JOptionPane.showMessageDialog(this, "Error opening or initializing video file.", "Error", JOptionPane.ERROR_MESSAGE);
        videoLabel.setIcon(null);
        videoLabel.setText("No video loaded. Click Open Video to begin.");
    }

    private void handlePlayPauseToggle() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            return;
        }

        if (videoPlaying && !paused) {
            paused = true;
            videoTimer.stop();
            setPlayButtonPlaying(false);
            setPipelineState("Paused", CHIP_WARNING);
            return;
        }

        if (paused) {
            if (!appService.isCaptureActive()) {
                JOptionPane.showMessageDialog(this, "Video has ended. Reset to play again.", "Video Ended",
                        JOptionPane.INFORMATION_MESSAGE);
                setPlayButtonPlaying(false);
                return;
            }

            paused = false;
            videoPlaying = true;
            videoTimer.start();
            setPlayButtonPlaying(true);
            setPipelineState("Playing", CHIP_PLAYING);
            return;
        }

        if (!appService.isCaptureActive()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Video has finished or is not ready. Reset and play from the beginning?", "Play Video",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                handleResetVideo();
                if (!appService.isCaptureActive()) {
                    return;
                }
                paused = false;
                videoPlaying = true;
                videoTimer.start();
                setPlayButtonPlaying(true);
                setPipelineState("Playing", CHIP_PLAYING);
            }
            return;
        }

        paused = false;
        videoPlaying = true;
        videoTimer.start();
        setPlayButtonPlaying(true);
        setPipelineState("Playing", CHIP_PLAYING);
    }

    private void handleResetVideo() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            JOptionPane.showMessageDialog(this, "No video has been successfully loaded to reset.", "Reset Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        videoTimer.stop();
        videoPlaying = false;
        paused = true;
        setPlayButtonPlaying(false);
        mog2ViewButton.setSelected(false);
        appService.setDisplayMOG2Foreground(false);
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        updateVideoTimerDelay(DEFAULT_VIDEO_RATE);

        appService.resetAnalysisForCurrentVideo();
        Mat firstFrameAfterReset = appService.getLastProcessedFrame();

        if (firstFrameAfterReset != null && !firstFrameAfterReset.empty()) {
            videoLabel.setIcon(new ImageIcon(matToBufferedImage(firstFrameAfterReset)));
            videoLabel.setText(null);
        } else {
            videoLabel.setIcon(null);
            videoLabel.setText("Unable to render frame after reset.");
            JOptionPane.showMessageDialog(this, "Failed to prepare video for display after reset.", "Reset Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        setPipelineState("Loaded", CHIP_ACTIVE);
        syncChartRefreshIntervalWithVideo();
        refreshChartsNow();
        refreshVideoPositionControls();
    }

    private void handlePlaybackRateChange() {
        double rate = sliderToRate();
        playbackRateValueLabel.setText(formatPlaybackSpeedText(rate));

        if (!appService.isVideoSuccessfullyInitialized() || appService.getFps() <= 0) {
            return;
        }
        updateVideoTimerDelay(rate);
    }

    private void updateVideoTimerDelay(double rate) {
        if (rate <= 0.01) {
            rate = 0.01;
        }

        double fps = appService.getFps();
        if (fps <= 0) {
            fps = 30;
        }

        int newDelay = (int) Math.round(1000.0 / (fps * rate));
        videoTimer.setDelay(Math.max(1, newDelay));
    }

    private void updateFrame() {
        updateFrame(false);
    }

    private void updateFrame(boolean forceChartRefresh) {
        Mat frame = appService.processNextFrameForGUI();
        if (frame != null && !frame.empty()) {
            videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
            if (forceChartRefresh) {
                refreshChartsNow();
            } else {
                refreshChartsIfDue();
            }
            refreshVideoPositionControls();
            return;
        }

        if (frame == null && !appService.isCaptureActive() && (videoPlaying || paused)) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Complete", CHIP_ACTIVE);
            refreshChartsNow();
            refreshVideoPositionControls();
            if (SwingUtilities.isEventDispatchThread()) {
                JOptionPane.showMessageDialog(this, "End of video.", "Playback Finished", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        if (frame == null && (videoPlaying || paused)) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Error", CHIP_WARNING);
            refreshVideoPositionControls();
            if (SwingUtilities.isEventDispatchThread()) {
                JOptionPane.showMessageDialog(this, "Error during video playback.", "Playback Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleMOG2Toggle(ItemEvent e) {
        if (!appService.isVideoSuccessfullyInitialized()) {
            return;
        }

        boolean showMask = (e.getStateChange() == ItemEvent.SELECTED);
        appService.setDisplayMOG2Foreground(showMask);
        Mat currentDisplayMat = appService.getLastProcessedFrame();
        if (currentDisplayMat != null && !currentDisplayMat.empty()) {
            videoLabel.setIcon(new ImageIcon(matToBufferedImage(currentDisplayMat.clone())));
        }
        videoLabel.repaint();
    }

    private void handleFastAnalyze() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            JOptionPane.showMessageDialog(this, "Please load a video first using Open Video.", "No Video",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (videoPlaying && !paused) {
            JOptionPane.showMessageDialog(this, "Pause or reset before Fast Analyze.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        videoTimer.stop();
        videoPlaying = false;
        paused = true;
        setPlayButtonPlaying(false);
        setPipelineState("Fast Analyze", CHIP_WARNING);

        appService.resetAnalysisForCurrentVideo();
        syncChartRefreshIntervalWithVideo();
        int totalFrames = appService.getFrameCount();
        int updateFrequency = Math.max(1, totalFrames / 100);

        SwingWorker<Void, Mat> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i < totalFrames; i++) {
                    Mat processedAnalyticalFrame = appService.processNextFrameForAnalysis();
                    if (processedAnalyticalFrame == null) {
                        break;
                    }

                    if (i % updateFrequency == 0 || i == totalFrames - 1) {
                        Mat frameToShow = appService.getLastProcessedFrame();
                        if (frameToShow != null && !frameToShow.empty()) {
                            Mat frameWithText = frameToShow.clone();
                            double percent = (appService.getCurrentFrameNumber() / (double) totalFrames) * 100;
                            Imgproc.putText(frameWithText, String.format("Fast Analyze: %.1f%%", percent),
                                    new Point(10, frameWithText.rows() - 20), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8,
                                    new Scalar(28, 233, 197), 2);
                            publish(frameWithText);
                        }
                        processedAnalyticalFrame.release();
                    } else {
                        processedAnalyticalFrame.release();
                    }
                    Thread.sleep(1);
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
                    latestFrame.release();
                    refreshChartsIfDue();
                }
            }

            @Override
            protected void done() {
                Mat finalFrame = appService.getLastProcessedFrame();
                if (finalFrame != null && !finalFrame.empty()) {
                    videoLabel.setIcon(new ImageIcon(matToBufferedImage(finalFrame)));
                }
                refreshChartsNow();
                refreshVideoPositionControls();
                setPipelineState("Loaded", CHIP_ACTIVE);
                JOptionPane.showMessageDialog(CellCounterGUI.this, "Fast Analysis Complete.", "Done",
                        JOptionPane.INFORMATION_MESSAGE);
                paused = false;
            }
        };
        worker.execute();
    }

    private void handleFrameForward() {
        if (appService.isVideoSuccessfullyInitialized() && paused && appService.isCaptureActive()) {
            updateFrame(true);
            refreshVideoPositionControls();
            if (!appService.isCaptureActive()) {
                setPlayButtonPlaying(false);
                videoPlaying = false;
                setPipelineState("Complete", CHIP_ACTIVE);
            }
        }
    }

    private void handleVideoPositionSliderChange() {
        if (suppressVideoPositionEvents) {
            return;
        }

        int selected = videoPositionSlider.getValue();
        int total = Math.max(1, appService.getFrameCount());
        videoPositionValueLabel.setText(formatFrameChipText(selected + 1, total));

        if (videoPositionSlider.getValueIsAdjusting()) {
            return;
        }
        if (!appService.isVideoSuccessfullyInitialized()) {
            return;
        }
        if (seekWorker != null && !seekWorker.isDone()) {
            return;
        }

        videoTimer.stop();
        videoPlaying = false;
        paused = true;
        setPlayButtonPlaying(false);
        setPipelineState("Seeking", CHIP_WARNING);
        setMainControlsEnabled(false);

        final int targetFrame = selected;
        seekWorker = new SwingWorker<>() {
            @Override
            protected Mat doInBackground() {
                return appService.seekToFrameForGUI(targetFrame);
            }

            @Override
            protected void done() {
                try {
                    Mat seekFrame = get();
                    if (seekFrame != null && !seekFrame.empty()) {
                        videoLabel.setIcon(new ImageIcon(matToBufferedImage(seekFrame)));
                        videoLabel.setText(null);
                        refreshChartsNow();
                        setPipelineState(appService.isCaptureActive() ? "Paused" : "Complete",
                                appService.isCaptureActive() ? CHIP_WARNING : CHIP_ACTIVE);
                    } else {
                        setPipelineState("Error", CHIP_WARNING);
                    }
                } catch (Exception ex) {
                    setPipelineState("Error", CHIP_WARNING);
                    JOptionPane.showMessageDialog(CellCounterGUI.this,
                            "Failed to seek video: " + ex.getMessage(),
                            "Seek Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setMainControlsEnabled(true);
                    refreshVideoPositionControls();
                }
            }
        };
        seekWorker.execute();
    }

    private void handleSaveResults() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            JOptionPane.showMessageDialog(this, "No video has been loaded or analysis performed.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] metadata = promptForMetadata();
        if (metadata == null) {
            return;
        }

        ExportMetadata exportMetadata = new ExportMetadata(metadata[0], metadata[1], metadata[2]);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Results (analysis + footprint)");
        chooser.setSelectedFile(new File("results.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            File parent = selected.getParentFile() == null ? new File(".") : selected.getParentFile();
            String rawName = selected.getName().trim();
            String baseName = rawName;
            if (baseName.toLowerCase().endsWith(".csv")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            if (baseName.isEmpty()) {
                baseName = "results";
            }

            File analysisFile = new File(parent, baseName + "_analysis.csv");
            File footprintFile = new File(parent, baseName + "_footprint.csv");
            try {
                appService.saveAnalysisCsv(analysisFile, exportMetadata);
                appService.saveFootprintCsv(footprintFile, exportMetadata);
                JOptionPane.showMessageDialog(this,
                        "Saved:\n- " + analysisFile.getAbsolutePath() + "\n- " + footprintFile.getAbsolutePath(),
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException | IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, "Error saving results: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setPlayButtonPlaying(boolean isPlaying) {
        if (isPlaying) {
            playButton.setText("Play/Analyze");
            playButton.setIcon(pauseIcon);
        } else {
            playButton.setText("Play/Analyze");
            playButton.setIcon(playIcon);
        }
    }

    private void setPipelineState(String state, Color color) {
        pipelineStateLabel.setText(state);
        pipelineStateLabel.setOpaque(true);
        pipelineStateLabel.setBackground(color);
        pipelineStateLabel.setForeground(Color.WHITE);
    }

    private void openHelpDocumentation() {
        Path helpPath = resolveHelpDocumentationPath();
        if (helpPath == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Help documentation file was not found.\nExpected docs/help/index.html in the project or packaged app.",
                    "Help Unavailable",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Desktop browser launch is not supported on this system.\nOpen this file manually:\n"
                            + helpPath.toAbsolutePath(),
                    "Help",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().browse(helpPath.toUri());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to open help in browser.\n" + ex.getMessage() + "\n\nFile:\n" + helpPath.toAbsolutePath(),
                    "Help Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyWindowIcon() {
        Image appIcon = loadWindowIconImage();
        if (appIcon == null) {
            return;
        }
        setIconImage(appIcon);
        if (Taskbar.isTaskbarSupported()) {
            try {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(appIcon);
                }
            } catch (UnsupportedOperationException | SecurityException ignored) {
                // Optional enhancement only.
            }
        }
    }

    private Image loadWindowIconImage() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("packaging", "assets", APP_ICON_FILE_NAME));
        candidates.add(Path.of("assets", APP_ICON_FILE_NAME));
        candidates.add(Path.of(APP_ICON_FILE_NAME));

        CodeSource codeSource = CellCounterGUI.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            try {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                Path base = Files.isDirectory(location) ? location : location.getParent();
                if (base != null) {
                    candidates.add(base.resolve("assets").resolve(APP_ICON_FILE_NAME));
                    Path parent = base.getParent();
                    if (parent != null) {
                        candidates.add(parent.resolve("assets").resolve(APP_ICON_FILE_NAME));
                    }
                }
            } catch (Exception ignored) {
                // Fall through to existing candidate paths.
            }
        }

        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path absolute = candidate.toAbsolutePath().normalize();
            if (!Files.isRegularFile(absolute)) {
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(absolute.toFile());
                if (image != null) {
                    return image;
                }
            } catch (IOException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private Path resolveHelpDocumentationPath() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("docs", "help", "index.html"));
        candidates.add(Path.of("help", "index.html"));
        candidates.add(Path.of("target", "classes", "help", "index.html"));

        CodeSource codeSource = CellCounterGUI.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            try {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                Path base = Files.isDirectory(location) ? location : location.getParent();
                if (base != null) {
                    candidates.add(base.resolve("help").resolve("index.html"));
                    candidates.add(base.resolve("docs").resolve("help").resolve("index.html"));
                    Path parent = base.getParent();
                    if (parent != null) {
                        candidates.add(parent.resolve("help").resolve("index.html"));
                        candidates.add(parent.resolve("docs").resolve("help").resolve("index.html"));
                    }
                }
            } catch (Exception ignored) {
                // Fall through to existing candidate paths.
            }
        }

        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolute)) {
                return absolute;
            }
        }
        return null;
    }

    private void setMainControlsEnabled(boolean enabled) {
        if (analyzeButton != null) {
            analyzeButton.setEnabled(enabled);
        }
        if (fastButton != null) {
            fastButton.setEnabled(enabled);
        }
        if (playButton != null) {
            playButton.setEnabled(enabled);
        }
        if (frameForwardButton != null) {
            frameForwardButton.setEnabled(enabled);
        }
        if (resetButton != null) {
            resetButton.setEnabled(enabled);
        }
        if (mog2ViewButton != null) {
            mog2ViewButton.setEnabled(enabled);
        }
        if (saveResultsButton != null) {
            saveResultsButton.setEnabled(enabled);
        }
        if (simulatorButton != null) {
            simulatorButton.setEnabled(enabled);
        }
        if (helpButton != null) {
            helpButton.setEnabled(enabled);
        }
        if (tuneDetectionButton != null) {
            tuneDetectionButton.setEnabled(enabled);
        }
        if (playbackRateSlider != null) {
            playbackRateSlider.setEnabled(enabled);
        }
        if (videoPositionSlider != null) {
            videoPositionSlider.setEnabled(enabled);
        }
    }

    private int rateToSlider(double rate) {
        return (int) Math.round(rate * 100.0);
    }

    private double sliderToRate() {
        return playbackRateSlider.getValue() / 100.0;
    }

    private Image matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            BufferedImage placeholder = new BufferedImage(640, 360, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(new Color(7, 19, 40));
            g.fillRect(0, 0, placeholder.getWidth(), placeholder.getHeight());
            g.setColor(new Color(202, 221, 245));
            g.setFont(FONT_BODY);
            g.drawString("No Image", placeholder.getWidth() / 2 - 28, placeholder.getHeight() / 2);
            g.dispose();
            return placeholder;
        }

        int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        int width = Math.max(1, mat.cols());
        int height = Math.max(1, mat.rows());

        BufferedImage image = new BufferedImage(width, height, type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }

    public static void main(String[] args) {
        CellCounterApp.main(args);
    }

    private String[] promptForMetadata() {
        JTextField cellField = new JTextField();
        JTextField substrateField = new JTextField();
        JTextField flowField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(0, 1, SPACE_XXS, SPACE_XXS));
        panel.add(new JLabel("Cell Type:"));
        panel.add(cellField);
        panel.add(new JLabel("Substrate Name:"));
        panel.add(substrateField);
        panel.add(new JLabel("Flow Condition:"));
        panel.add(flowField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Enter Experimental Metadata",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return new String[] { cellField.getText().trim(), substrateField.getText().trim(), flowField.getText().trim() };
        }
        return null;
    }

}
