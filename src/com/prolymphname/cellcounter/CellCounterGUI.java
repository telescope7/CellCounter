package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.export.ExportMetadata;
import com.prolymphname.cellcounter.simulation.CellSimulationGUI;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
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
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
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

public class CellCounterGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    // Design system: spacing tokens
    private static final int SPACE_XXS = 4;
    private static final int SPACE_XS = 8;
    private static final int SPACE_S = 12;
    private static final int SPACE_M = 16;
    private static final int SPACE_L = 24;
    private static final int SPACE_XL = 32;
    private static final int RADIUS_CARD = 22;

    // Design system: palette
    private static final Color BG_TOP = new Color(3, 10, 24);
    private static final Color BG_BOTTOM = new Color(8, 23, 52);
    private static final Color GLASS_SURFACE_TOP = new Color(15, 33, 66, 232);
    private static final Color GLASS_SURFACE_BOTTOM = new Color(10, 24, 50, 222);
    private static final Color BORDER_SOFT = new Color(122, 167, 234, 84);
    private static final Color TEXT_PRIMARY = new Color(235, 245, 255);
    private static final Color TEXT_SECONDARY = new Color(175, 203, 236);
    private static final Color PRIMARY_ACTION = new Color(38, 102, 255);
    private static final Color PRIMARY_ACTION_DARK = new Color(24, 74, 205);
    private static final Color ACCENT = new Color(58, 188, 255);
    private static final Color ACCENT_DEEP = new Color(30, 132, 234);
    private static final Color CHIP_IDLE = new Color(85, 113, 157);
    private static final Color CHIP_ACTIVE = new Color(39, 137, 240);
    private static final Color CHIP_PLAYING = new Color(27, 184, 143);
    private static final Color CHIP_WARNING = new Color(221, 141, 56);

    // Design system: typography
    private static final Font FONT_DISPLAY = resolveFont(new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.BOLD,
            28);
    private static final Font FONT_H2 = resolveFont(new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.BOLD, 17);
    private static final Font FONT_BODY = resolveFont(new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.PLAIN, 13);
    private static final Font FONT_LABEL = resolveFont(new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.PLAIN, 12);
    private static final Font FONT_BUTTON = resolveFont(new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.BOLD, 12);
    private static final String APP_ICON_FILE_NAME = "cellcounter-icon-1024.png";

    private static final double DEFAULT_VIDEO_RATE = 1.0;

    private final CellCounterApplicationService appService;

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
                updateFrame();
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
        playbackRateSlider.addChangeListener(e -> handlePlaybackRateChange());
        videoPositionSlider.addChangeListener(e -> handleVideoPositionSliderChange());
    }

    private void setInitialControlState() {
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        playbackRateValueLabel.setText(formatPlaybackSpeedText(DEFAULT_VIDEO_RATE));
        refreshVideoPositionControls();
    }

    private void handleConfigureTracking() {
        TrackingConfiguration current = appService.getTrackingConfiguration();
        TrackingConfiguration updated = promptForTrackingConfiguration(current);
        if (updated == null) {
            return;
        }

        boolean wasInitialized = appService.isVideoSuccessfullyInitialized();
        boolean wasPlaying = videoPlaying && !paused;
        videoTimer.stop();
        videoPlaying = false;
        paused = wasInitialized;
        setPlayButtonPlaying(false);

        appService.setTrackingConfiguration(updated);

        if (wasInitialized) {
            mog2ViewButton.setSelected(false);
            appService.setDisplayMOG2Foreground(false);
            Mat firstFrame = appService.getLastProcessedFrame();
            if (firstFrame != null && !firstFrame.empty()) {
                videoLabel.setIcon(new ImageIcon(matToBufferedImage(firstFrame)));
                videoLabel.setText(null);
            }
            updateCharts();
            refreshVideoPositionControls();
            setPipelineState("Configured", CHIP_ACTIVE);
        } else {
            refreshVideoPositionControls();
            setPipelineState("Idle", CHIP_IDLE);
        }

        String message = wasPlaying
                ? "Tracking configuration applied. Playback was paused and reset to frame 1."
                : "Tracking configuration applied.";
        JOptionPane.showMessageDialog(this, message, "Configuration", JOptionPane.INFORMATION_MESSAGE);
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
        TrackingConfiguration[] appliedConfig = new TrackingConfiguration[] { appService.getTrackingConfiguration() };

        JSlider mog2HistorySlider = createTuningSlider(30, 1200,
                clampInt(appliedConfig[0].getMog2HistoryFrames(), 30, 1200));
        JSlider mog2VarThresholdSlider = createTuningSlider(1, 200,
                clampInt((int) Math.round(appliedConfig[0].getMog2VarThreshold()), 1, 200));
        JSlider maskThresholdSlider = createTuningSlider(0, 255,
                clampInt((int) Math.round(appliedConfig[0].getNormalizedMaskThreshold()), 0, 255));
        JSlider minContourAreaSlider = createTuningSlider(0, 600,
                clampInt((int) Math.round(appliedConfig[0].getMinContourArea()), 0, 600));
        JSlider maxRectCircumferenceSlider = createTuningSlider(20, 500,
                clampInt((int) Math.round(appliedConfig[0].getMaxRectCircumference()), 20, 500));
        JSlider morphologyKernelSlider = createTuningSlider(1, 15,
                clampInt(appliedConfig[0].getMorphologyKernelSize(), 1, 15));
        JSlider morphologyOpenSlider = createTuningSlider(0, 8,
                clampInt(appliedConfig[0].getMorphologyOpenIterations(), 0, 8));
        JSlider morphologyDilateSlider = createTuningSlider(0, 8,
                clampInt(appliedConfig[0].getMorphologyDilateIterations(), 0, 8));
        JSlider maxAssociationDistanceSlider = createTuningSlider(30, 350,
                clampInt((int) Math.round(appliedConfig[0].getMaxAssociationDistancePixels()), 30, 350));
        JSlider maxFramesDisappearedSlider = createTuningSlider(1, 1000,
                clampInt(appliedConfig[0].getMaxFramesDisappeared(), 1, 1000));
        JSlider maxVerticalDisplacementSlider = createTuningSlider(0, 500,
                clampInt((int) Math.round(appliedConfig[0].getMaxVerticalDisplacementPixels()), 0, 500));
        JSlider minHorizontalMovementSlider = createTuningSlider(-100, 100,
                clampInt((int) Math.round(appliedConfig[0].getMinHorizontalMovementPixels()), -100, 100));

        JCheckBox detectShadowsCheck = new JCheckBox("Enable MOG2 shadows");
        detectShadowsCheck.setSelected(appliedConfig[0].isMog2DetectShadows());
        styleConfigCheckBox(detectShadowsCheck);

        JCheckBox maskPreviewCheck = new JCheckBox("Preview mask view");
        maskPreviewCheck.setSelected(true);
        styleConfigCheckBox(maskPreviewCheck);

        JLabel historyValue = createTuningValueChip(mog2HistorySlider.getValue() + " f");
        JLabel varValue = createTuningValueChip(String.valueOf(mog2VarThresholdSlider.getValue()));
        JLabel thresholdValue = createTuningValueChip(maskThresholdSlider.getValue() + " px");
        JLabel contourValue = createTuningValueChip(minContourAreaSlider.getValue() + " px2");
        JLabel rectValue = createTuningValueChip(maxRectCircumferenceSlider.getValue() + " px");
        JLabel kernelValue = createTuningValueChip(toOdd(morphologyKernelSlider.getValue()) + " px");
        JLabel openValue = createTuningValueChip(String.valueOf(morphologyOpenSlider.getValue()));
        JLabel dilateValue = createTuningValueChip(String.valueOf(morphologyDilateSlider.getValue()));
        JLabel associationValue = createTuningValueChip(maxAssociationDistanceSlider.getValue() + " px");
        JLabel maxFramesDisappearedValue = createTuningValueChip(String.valueOf(maxFramesDisappearedSlider.getValue()));
        JLabel maxVerticalDisplacementValue = createTuningValueChip(maxVerticalDisplacementSlider.getValue() + " px");
        JLabel minHorizontalMovementValue = createTuningValueChip(minHorizontalMovementSlider.getValue() + " px");

        JLabel statusLabel = new JLabel("Adjust sliders and release to preview on the current frame.");
        statusLabel.setFont(FONT_BODY);
        statusLabel.setForeground(TEXT_SECONDARY);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(SPACE_XS, SPACE_XS, SPACE_XS, SPACE_XS));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, SPACE_XS, 0);

        addTuningRow(form, gbc, "MOG2 History", mog2HistorySlider, historyValue);
        addTuningRow(form, gbc, "MOG2 Variance Threshold", mog2VarThresholdSlider, varValue);
        addTuningRow(form, gbc, "Mask Threshold", maskThresholdSlider, thresholdValue);
        addTuningRow(form, gbc, "Min Contour Area", minContourAreaSlider, contourValue);
        addTuningRow(form, gbc, "Max Rectangle Circumference", maxRectCircumferenceSlider, rectValue);
        addTuningRow(form, gbc, "Morphology Kernel (odd)", morphologyKernelSlider, kernelValue);
        addTuningRow(form, gbc, "Morphology Open Iterations", morphologyOpenSlider, openValue);
        addTuningRow(form, gbc, "Morphology Dilate Iterations", morphologyDilateSlider, dilateValue);
        addTuningRow(form, gbc, "Max Association Distance", maxAssociationDistanceSlider, associationValue);
        addTuningRow(form, gbc, "Max Frames Disappeared", maxFramesDisappearedSlider, maxFramesDisappearedValue);
        addTuningRow(form, gbc, "Max Vertical Displacement", maxVerticalDisplacementSlider, maxVerticalDisplacementValue);
        addTuningRow(form, gbc, "Min Horizontal Movement", minHorizontalMovementSlider, minHorizontalMovementValue);
        addTuningCheckboxRow(form, gbc, detectShadowsCheck);
        addTuningCheckboxRow(form, gbc, maskPreviewCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(new LineBorder(new Color(82, 129, 193, 140), 1, true));
        scrollPane.setPreferredSize(new Dimension(700, 420));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(new Color(8, 23, 52));
        scrollPane.setOpaque(false);

        CardPanel dialogCard = createCard("Detection Tuner", "Interactive segmentation preview for paused frame");
        dialogCard.add(scrollPane, BorderLayout.CENTER);

        JButton applyButton = createPrimaryButton("Apply Parameters", null);
        JButton resetButton = createSecondaryButton("Reset Sliders", null);
        JButton closeButton = createSecondaryButton("Close", null);
        enforceButtonSize(applyButton, 168);
        enforceButtonSize(resetButton, 142);
        enforceButtonSize(closeButton, 110);

        JPanel actionRow = new JPanel(new BorderLayout(SPACE_M, 0));
        actionRow.setOpaque(false);
        actionRow.add(statusLabel, BorderLayout.CENTER);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, SPACE_XS, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(resetButton);
        buttonRow.add(closeButton);
        buttonRow.add(applyButton);
        actionRow.add(buttonRow, BorderLayout.EAST);

        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(SPACE_M, SPACE_M));
        root.setBorder(new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
        root.add(dialogCard, BorderLayout.CENTER);
        root.add(actionRow, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(this, "Detection Tuner", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(root);
        dialog.setSize(820, 640);
        dialog.setMinimumSize(new Dimension(780, 580));
        dialog.setLocationRelativeTo(this);

        final boolean[] suppressPreview = new boolean[] { false };

        java.util.List<JSlider> previewSliders = java.util.List.of(
                mog2HistorySlider,
                mog2VarThresholdSlider,
                maskThresholdSlider,
                minContourAreaSlider,
                maxRectCircumferenceSlider,
                morphologyKernelSlider,
                morphologyOpenSlider,
                morphologyDilateSlider,
                maxAssociationDistanceSlider,
                maxFramesDisappearedSlider,
                maxVerticalDisplacementSlider,
                minHorizontalMovementSlider);

        Consumer<Boolean> setTunerParameterInputsEnabled = enabled -> {
            for (JSlider slider : previewSliders) {
                slider.setEnabled(enabled);
            }
            detectShadowsCheck.setEnabled(enabled);
            maskPreviewCheck.setEnabled(enabled);
            applyButton.setEnabled(enabled);
            resetButton.setEnabled(enabled);
            closeButton.setEnabled(true);
        };

        Runnable updateValueLabels = () -> {
            historyValue.setText(mog2HistorySlider.getValue() + " f");
            varValue.setText(String.valueOf(mog2VarThresholdSlider.getValue()));
            thresholdValue.setText(maskThresholdSlider.getValue() + " px");
            contourValue.setText(minContourAreaSlider.getValue() + " px2");
            rectValue.setText(maxRectCircumferenceSlider.getValue() + " px");
            kernelValue.setText(toOdd(morphologyKernelSlider.getValue()) + " px");
            openValue.setText(String.valueOf(morphologyOpenSlider.getValue()));
            dilateValue.setText(String.valueOf(morphologyDilateSlider.getValue()));
            associationValue.setText(maxAssociationDistanceSlider.getValue() + " px");
            maxFramesDisappearedValue.setText(String.valueOf(maxFramesDisappearedSlider.getValue()));
            maxVerticalDisplacementValue.setText(maxVerticalDisplacementSlider.getValue() + " px");
            minHorizontalMovementValue.setText(minHorizontalMovementSlider.getValue() + " px");
        };

        Runnable loadSlidersFromApplied = () -> {
            suppressPreview[0] = true;
            mog2HistorySlider.setValue(clampInt(appliedConfig[0].getMog2HistoryFrames(), 30, 1200));
            mog2VarThresholdSlider.setValue(clampInt((int) Math.round(appliedConfig[0].getMog2VarThreshold()), 1, 200));
            maskThresholdSlider.setValue(clampInt((int) Math.round(appliedConfig[0].getNormalizedMaskThreshold()), 0, 255));
            minContourAreaSlider.setValue(clampInt((int) Math.round(appliedConfig[0].getMinContourArea()), 0, 600));
            maxRectCircumferenceSlider.setValue(
                    clampInt((int) Math.round(appliedConfig[0].getMaxRectCircumference()), 20, 500));
            morphologyKernelSlider.setValue(clampInt(appliedConfig[0].getMorphologyKernelSize(), 1, 15));
            morphologyOpenSlider.setValue(clampInt(appliedConfig[0].getMorphologyOpenIterations(), 0, 8));
            morphologyDilateSlider.setValue(clampInt(appliedConfig[0].getMorphologyDilateIterations(), 0, 8));
            maxAssociationDistanceSlider.setValue(
                    clampInt((int) Math.round(appliedConfig[0].getMaxAssociationDistancePixels()), 30, 350));
            maxFramesDisappearedSlider.setValue(clampInt(appliedConfig[0].getMaxFramesDisappeared(), 1, 1000));
            maxVerticalDisplacementSlider.setValue(
                    clampInt((int) Math.round(appliedConfig[0].getMaxVerticalDisplacementPixels()), 0, 500));
            minHorizontalMovementSlider.setValue(
                    clampInt((int) Math.round(appliedConfig[0].getMinHorizontalMovementPixels()), -100, 100));
            detectShadowsCheck.setSelected(appliedConfig[0].isMog2DetectShadows());
            suppressPreview[0] = false;
            updateValueLabels.run();
        };

        Supplier<TrackingConfiguration> buildWorkingConfig = () -> new TrackingConfiguration(
                maxFramesDisappearedSlider.getValue(),
                minContourAreaSlider.getValue(),
                maxRectCircumferenceSlider.getValue(),
                maxVerticalDisplacementSlider.getValue(),
                minHorizontalMovementSlider.getValue(),
                maxAssociationDistanceSlider.getValue(),
                mog2HistorySlider.getValue(),
                mog2VarThresholdSlider.getValue(),
                detectShadowsCheck.isSelected(),
                toOdd(morphologyKernelSlider.getValue()),
                morphologyOpenSlider.getValue(),
                morphologyDilateSlider.getValue(),
                maskThresholdSlider.getValue()).normalized();

        class PreviewRunner {
            private SwingWorker<Mat, Void> worker;
            private TrackingConfiguration queuedConfig;
            private Boolean queuedMask;

            void request(TrackingConfiguration cfg, boolean showMask) {
                if (dialog == null || !dialog.isDisplayable()) {
                    return;
                }
                if (worker != null && !worker.isDone()) {
                    queuedConfig = cfg;
                    queuedMask = showMask;
                    return;
                }
                start(cfg, showMask);
            }

            private void start(TrackingConfiguration cfg, boolean showMask) {
                statusLabel.setText("Rendering preview on current frame...");
                setTunerParameterInputsEnabled.accept(false);
                worker = new SwingWorker<>() {
                    @Override
                    protected Mat doInBackground() {
                        return appService.previewCurrentFrameForTuning(cfg, showMask);
                    }

                    @Override
                    protected void done() {
                        try {
                            Mat previewFrame = get();
                            if (previewFrame != null && !previewFrame.empty()) {
                                videoLabel.setIcon(new ImageIcon(matToBufferedImage(previewFrame)));
                                videoLabel.setText(null);
                                statusLabel.setText("Preview updated.");
                                previewFrame.release();
                            } else {
                                statusLabel.setText("Preview unavailable for this frame.");
                            }
                        } catch (Exception ex) {
                            statusLabel.setText("Preview error: " + ex.getMessage());
                        } finally {
                            if (queuedConfig != null && queuedMask != null) {
                                TrackingConfiguration nextCfg = queuedConfig;
                                boolean nextMask = queuedMask;
                                queuedConfig = null;
                                queuedMask = null;
                                start(nextCfg, nextMask);
                            } else {
                                setTunerParameterInputsEnabled.accept(true);
                            }
                        }
                    }
                };
                worker.execute();
            }

            void cancel() {
                if (worker != null && !worker.isDone()) {
                    worker.cancel(true);
                }
                queuedConfig = null;
                queuedMask = null;
                setTunerParameterInputsEnabled.accept(true);
            }
        }

        PreviewRunner previewRunner = new PreviewRunner();

        Runnable requestPreview = () -> {
            if (suppressPreview[0]) {
                return;
            }
            previewRunner.request(buildWorkingConfig.get(), maskPreviewCheck.isSelected());
        };

        for (JSlider slider : previewSliders) {
            slider.addChangeListener(e -> {
                if (suppressPreview[0]) {
                    return;
                }
                updateValueLabels.run();
                if (!slider.getValueIsAdjusting()) {
                    requestPreview.run();
                }
            });
        }
        detectShadowsCheck.addActionListener(e -> requestPreview.run());
        maskPreviewCheck.addActionListener(e -> requestPreview.run());

        applyButton.addActionListener(e -> {
            TrackingConfiguration updated = buildWorkingConfig.get();
            appService.setTrackingConfiguration(updated);
            appliedConfig[0] = updated;
            mog2ViewButton.setSelected(false);
            appService.setDisplayMOG2Foreground(false);
            refreshCurrentVideoFrame();
            updateCharts();
            paused = true;
            videoPlaying = false;
            videoTimer.stop();
            setPlayButtonPlaying(false);
            setPipelineState("Configured", CHIP_ACTIVE);
            statusLabel.setText("Parameters applied. Playback reset to frame 1.");
        });

        resetButton.addActionListener(e -> {
            loadSlidersFromApplied.run();
            requestPreview.run();
        });

        closeButton.addActionListener(e -> dialog.dispose());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                previewRunner.cancel();
                refreshCurrentVideoFrame();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                previewRunner.cancel();
                refreshCurrentVideoFrame();
            }
        });

        updateValueLabels.run();
        requestPreview.run();
        dialog.setVisible(true);
    }

    private TrackingConfiguration promptForTrackingConfiguration(TrackingConfiguration current) {
        JSpinner maxFramesDisappeared = new JSpinner(new SpinnerNumberModel(current.getMaxFramesDisappeared(), 1, 10000, 1));
        JSpinner minContourArea = new JSpinner(new SpinnerNumberModel(current.getMinContourArea(), 0.0, 100000.0, 0.5));
        JSpinner maxRectCircumference = new JSpinner(new SpinnerNumberModel(current.getMaxRectCircumference(), 1.0, 100000.0, 1.0));
        JSpinner maxVerticalDisplacement = new JSpinner(
                new SpinnerNumberModel(current.getMaxVerticalDisplacementPixels(), 0.0, 100000.0, 1.0));
        JSpinner minHorizontalMovement = new JSpinner(
                new SpinnerNumberModel(current.getMinHorizontalMovementPixels(), -100000.0, 100000.0, 0.5));
        JSpinner maxAssociationDistance = new JSpinner(
                new SpinnerNumberModel(current.getMaxAssociationDistancePixels(), 1.0, 100000.0, 1.0));
        JSpinner mog2History = new JSpinner(new SpinnerNumberModel(current.getMog2HistoryFrames(), 1, 10000, 10));
        JSpinner mog2VarThreshold = new JSpinner(new SpinnerNumberModel(current.getMog2VarThreshold(), 0.01, 1000.0, 0.5));
        JCheckBox detectShadows = new JCheckBox("Enable");
        detectShadows.setSelected(current.isMog2DetectShadows());
        JSpinner morphologyKernelSize = new JSpinner(new SpinnerNumberModel(current.getMorphologyKernelSize(), 1, 99, 2));
        JSpinner morphologyOpenIterations = new JSpinner(new SpinnerNumberModel(current.getMorphologyOpenIterations(), 0, 20, 1));
        JSpinner morphologyDilateIterations = new JSpinner(new SpinnerNumberModel(current.getMorphologyDilateIterations(), 0, 20, 1));
        JSpinner normalizedMaskThreshold = new JSpinner(
                new SpinnerNumberModel(current.getNormalizedMaskThreshold(), 0.0, 255.0, 1.0));

        styleConfigSpinner(maxFramesDisappeared);
        styleConfigSpinner(minContourArea);
        styleConfigSpinner(maxRectCircumference);
        styleConfigSpinner(maxVerticalDisplacement);
        styleConfigSpinner(minHorizontalMovement);
        styleConfigSpinner(maxAssociationDistance);
        styleConfigSpinner(mog2History);
        styleConfigSpinner(mog2VarThreshold);
        styleConfigSpinner(morphologyKernelSize);
        styleConfigSpinner(morphologyOpenIterations);
        styleConfigSpinner(morphologyDilateIterations);
        styleConfigSpinner(normalizedMaskThreshold);
        styleConfigCheckBox(detectShadows);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(SPACE_XS, SPACE_XS, SPACE_XS, SPACE_XS));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, SPACE_XS, SPACE_S);
        gbc.anchor = GridBagConstraints.WEST;

        addConfigRow(form, gbc, "Max Frames Disappeared", maxFramesDisappeared);
        addConfigRow(form, gbc, "Min Contour Area", minContourArea);
        addConfigRow(form, gbc, "Max Rectangle Circumference", maxRectCircumference);
        addConfigRow(form, gbc, "Max Vertical Displacement (px)", maxVerticalDisplacement);
        addConfigRow(form, gbc, "Min Horizontal Movement (px)", minHorizontalMovement);
        addConfigRow(form, gbc, "Max Association Distance (px)", maxAssociationDistance);
        addConfigRow(form, gbc, "MOG2 History Frames", mog2History);
        addConfigRow(form, gbc, "MOG2 Variance Threshold", mog2VarThreshold);
        addConfigRow(form, gbc, "MOG2 Detect Shadows", detectShadows);
        addConfigRow(form, gbc, "Morphology Kernel Size (odd)", morphologyKernelSize);
        addConfigRow(form, gbc, "Morphology Open Iterations", morphologyOpenIterations);
        addConfigRow(form, gbc, "Morphology Dilate Iterations", morphologyDilateIterations);
        addConfigRow(form, gbc, "Mask Threshold (0-255)", normalizedMaskThreshold);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(new LineBorder(new Color(82, 129, 193, 140), 1, true));
        scrollPane.setPreferredSize(new Dimension(550, 420));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(new Color(8, 23, 52));
        scrollPane.setOpaque(false);

        CardPanel dialogCard = createCard("Tracking Configuration", "Tune detection, tracking, and preprocessing metrics");
        dialogCard.add(scrollPane, BorderLayout.CENTER);

        JButton cancelButton = createSecondaryButton("Cancel", null);
        JButton applyButton = createPrimaryButton("Apply", null);
        enforceButtonSize(cancelButton, 110);
        enforceButtonSize(applyButton, 110);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, SPACE_XS, 0));
        actionRow.setOpaque(false);
        actionRow.add(cancelButton);
        actionRow.add(applyButton);

        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(SPACE_M, SPACE_M));
        root.setBorder(new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
        root.add(dialogCard, BorderLayout.CENTER);
        root.add(actionRow, BorderLayout.SOUTH);

        TrackingConfiguration[] result = new TrackingConfiguration[1];
        JDialog dialog = new JDialog(this, "Tracking Configuration", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(root);
        dialog.setSize(640, 640);
        dialog.setMinimumSize(new Dimension(620, 580));
        dialog.setLocationRelativeTo(this);

        applyButton.addActionListener(e -> {
            result[0] = new TrackingConfiguration(
                    ((Number) maxFramesDisappeared.getValue()).intValue(),
                    ((Number) minContourArea.getValue()).doubleValue(),
                    ((Number) maxRectCircumference.getValue()).doubleValue(),
                    ((Number) maxVerticalDisplacement.getValue()).doubleValue(),
                    ((Number) minHorizontalMovement.getValue()).doubleValue(),
                    ((Number) maxAssociationDistance.getValue()).doubleValue(),
                    ((Number) mog2History.getValue()).intValue(),
                    ((Number) mog2VarThreshold.getValue()).doubleValue(),
                    detectShadows.isSelected(),
                    ((Number) morphologyKernelSize.getValue()).intValue(),
                    ((Number) morphologyOpenIterations.getValue()).intValue(),
                    ((Number) morphologyDilateIterations.getValue()).intValue(),
                    ((Number) normalizedMaskThreshold.getValue()).doubleValue()).normalized();
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(applyButton);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
        return result[0];
    }

    private void addConfigRow(JPanel form, GridBagConstraints gbc, String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setFont(FONT_BODY);
        label.setForeground(TEXT_PRIMARY);

        gbc.gridx = 0;
        gbc.weightx = 0.54;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.46;
        form.add(field, gbc);

        gbc.gridy++;
    }

    private void styleConfigSpinner(JSpinner spinner) {
        Font valueFont = FONT_BODY.deriveFont(Font.BOLD, FONT_BODY.getSize2D());
        spinner.setFont(valueFont);
        spinner.setPreferredSize(new Dimension(170, 30));
        spinner.setBackground(Color.WHITE);
        spinner.setForeground(Color.BLACK);
        spinner.setBorder(new RoundedBorder(new Color(100, 147, 214, 150), 10));

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
            textField.setFont(valueFont);
            textField.setBackground(Color.WHITE);
            textField.setForeground(Color.BLACK);
            textField.setCaretColor(Color.BLACK);
            textField.setDisabledTextColor(Color.BLACK);
            textField.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        }
    }

    private void styleConfigCheckBox(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setForeground(TEXT_PRIMARY);
        checkBox.setFont(FONT_BODY);
        checkBox.setFocusPainted(false);
    }

    private JSlider createTuningSlider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, clampInt(value, min, max));
        slider.setOpaque(false);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setFont(FONT_LABEL);
        return slider;
    }

    private JLabel createTuningValueChip(String text) {
        JLabel chip = createChipLabel(text, ACCENT_DEEP);
        chip.setFont(FONT_LABEL);
        chip.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        chip.setPreferredSize(new Dimension(84, 24));
        return chip;
    }

    private void addTuningRow(JPanel form, GridBagConstraints gbc, String labelText, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(SPACE_XS, 0));
        row.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(FONT_BODY);
        label.setForeground(TEXT_PRIMARY);
        label.setPreferredSize(new Dimension(220, 22));

        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);

        form.add(row, gbc);
        gbc.gridy++;
    }

    private void addTuningCheckboxRow(JPanel form, GridBagConstraints gbc, JCheckBox checkBox) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(checkBox, BorderLayout.WEST);
        form.add(row, gbc);
        gbc.gridy++;
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

    private int toOdd(int value) {
        int normalized = Math.max(1, value);
        return normalized % 2 == 0 ? normalized + 1 : normalized;
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
            updateCharts();
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
        updateCharts();
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
        Mat frame = appService.processNextFrameForGUI();
        if (frame != null && !frame.empty()) {
            videoLabel.setIcon(new ImageIcon(matToBufferedImage(frame)));
            updateCharts();
            refreshVideoPositionControls();
            return;
        }

        if (frame == null && !appService.isCaptureActive() && (videoPlaying || paused)) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Complete", CHIP_ACTIVE);
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
                    updateCharts();
                }
            }

            @Override
            protected void done() {
                Mat finalFrame = appService.getLastProcessedFrame();
                if (finalFrame != null && !finalFrame.empty()) {
                    videoLabel.setIcon(new ImageIcon(matToBufferedImage(finalFrame)));
                }
                updateCharts();
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
            updateFrame();
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
                        updateCharts();
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

    private CardPanel createCard(String title, String subtitle) {
        return createCard(title, subtitle, true);
    }

    private CardPanel createCard(String title, String subtitle, boolean showHeading) {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(SPACE_S, SPACE_S));
        card.setBorder(new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
        if (showHeading) {
            JPanel heading = new JPanel();
            heading.setOpaque(false);
            heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(FONT_H2);
            titleLabel.setForeground(TEXT_PRIMARY);

            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(FONT_BODY);
            subtitleLabel.setForeground(TEXT_SECONDARY);

            heading.add(titleLabel);
            heading.add(Box.createVerticalStrut(SPACE_XXS));
            heading.add(subtitleLabel);

            card.add(heading, BorderLayout.NORTH);
        }
        return card;
    }

    private JButton createPrimaryButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        styleButton(button, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        return button;
    }

    private JButton createSecondaryButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        styleButton(button, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        return button;
    }

    private JToggleButton createToggleButton(String text, Icon icon) {
        JToggleButton toggle = new JToggleButton(text, icon);
        styleButton(toggle, PRIMARY_ACTION, Color.WHITE, new Color(27, 84, 228));
        toggle.setSelectedIcon(new AppIcon(AppIcon.Kind.EYE, Color.WHITE));
        toggle.addChangeListener(e -> {
            if (toggle.isSelected()) {
                toggle.setBackground(ACCENT_DEEP);
                toggle.setForeground(Color.WHITE);
            } else {
                toggle.setBackground(PRIMARY_ACTION);
                toggle.setForeground(Color.WHITE);
            }
        });
        return toggle;
    }

    private void styleButton(AbstractButton button, Color background, Color foreground, Color hoverBackground) {
        button.setFont(FONT_BUTTON);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorder(new RoundedBorder(new Color(0, 0, 0, 0), 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(SPACE_XS, SPACE_S, SPACE_XS, SPACE_S));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(SPACE_XS);
        button.setOpaque(true);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.isEnabled() && !(button instanceof JToggleButton && ((JToggleButton) button).isSelected())) {
                    button.setBackground(hoverBackground);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button.isEnabled() && !(button instanceof JToggleButton && ((JToggleButton) button).isSelected())) {
                    button.setBackground(background);
                }
            }
        });
    }

    private void configureIconOnlyButton(AbstractButton button, String tooltip) {
        button.setText("");
        button.setToolTipText(tooltip);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setMargin(new Insets(SPACE_XS, SPACE_XS, SPACE_XS, SPACE_XS));
    }

    private void enforceButtonSize(AbstractButton button, int minWidth) {
        int width = Math.max(minWidth, button.getPreferredSize().width + SPACE_XS);
        int height = Math.max(34, button.getPreferredSize().height + SPACE_XXS);
        Dimension size = new Dimension(width, height);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
    }

    private JLabel createChipLabel(String text, Color bg) {
        JLabel chip = new JLabel(text, SwingConstants.CENTER);
        chip.setFont(FONT_BUTTON);
        chip.setForeground(Color.WHITE);
        chip.setOpaque(true);
        chip.setBackground(bg);
        chip.setBorder(new EmptyBorder(SPACE_XXS, SPACE_S, SPACE_XXS, SPACE_S));
        return chip;
    }

    private static Font resolveFont(String[] candidates, int style, int size) {
        for (String family : candidates) {
            Font font = new Font(family, style, size);
            if (!"Dialog".equalsIgnoreCase(font.getFamily())) {
                return font;
            }
        }
        return new Font("SansSerif", style, size);
    }

    private static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint bg = new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM);
            g2.setPaint(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(62, 127, 255, 52));
            g2.fill(new Ellipse2D.Double(-80, -90, 420, 260));
            g2.setColor(new Color(36, 95, 206, 48));
            g2.fill(new Ellipse2D.Double(getWidth() - 320, -70, 420, 300));
            g2.setColor(new Color(22, 64, 166, 46));
            g2.fill(new Ellipse2D.Double(getWidth() - 260, getHeight() - 180, 380, 260));
            g2.dispose();
        }
    }

    private static class CardPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1,
                    RADIUS_CARD, RADIUS_CARD);
            GradientPaint gp = new GradientPaint(0, 0, GLASS_SURFACE_TOP, 0, getHeight(), GLASS_SURFACE_BOTTOM);
            g2.setPaint(gp);
            g2.fill(shape);

            g2.setColor(BORDER_SOFT);
            g2.draw(shape);

            g2.setColor(new Color(152, 188, 244, 82));
            g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, RADIUS_CARD - 2, RADIUS_CARD - 2));
            g2.dispose();

            super.paintComponent(g);
        }

        @Override
        public boolean isOpaque() {
            return false;
        }
    }

    private static class RoundedBorder extends LineBorder {
        private final int radius;

        RoundedBorder(Color color, int radius) {
            super(color, 1, true);
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(SPACE_XXS, SPACE_S, SPACE_XXS, SPACE_S);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(lineColor);
            g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
            g2.dispose();
        }
    }

    private static class StartupSplashWindow extends JWindow {
        private static final int WIDTH = 760;
        private static final int HEIGHT = 360;
        private static final Color SPLASH_TOP = new Color(3, 10, 24);
        private static final Color SPLASH_BOTTOM = new Color(8, 23, 52);
        private static final Color SPLASH_BORDER = new Color(122, 167, 234, 120);
        private static final Font SPLASH_TITLE_FONT = resolveFont(
                new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.BOLD, 54);
        private static final Font SPLASH_SUB_FONT = resolveFont(
                new String[] { "Avenir Next", "Segoe UI", "Helvetica Neue" }, Font.PLAIN, 17);

        private final Timer closeTimer;
        private final Runnable onComplete;

        StartupSplashWindow(int durationMillis, Runnable onComplete) {
            this.onComplete = onComplete;
            setBackground(new Color(0, 0, 0, 0));
            setSize(WIDTH, HEIGHT);
            setAlwaysOnTop(true);
            setLocationRelativeTo(null);
            setContentPane(new SplashPanel());

            closeTimer = new Timer(durationMillis, e -> {
                dispose();
                if (this.onComplete != null) {
                    this.onComplete.run();
                }
            });
            closeTimer.setRepeats(false);
        }

        void showSplash() {
            setVisible(true);
            closeTimer.start();
        }

        private class SplashPanel extends JPanel {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint bg = new GradientPaint(0, 0, SPLASH_TOP, 0, getHeight(), SPLASH_BOTTOM);
                g2.setPaint(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);

                g2.setColor(SPLASH_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);

                int haloSize = 330;
                g2.setColor(new Color(52, 134, 255, 78));
                g2.fill(new Ellipse2D.Float(-90, -85, haloSize, haloSize * 0.7f));
                g2.setColor(new Color(34, 107, 222, 62));
                g2.fill(new Ellipse2D.Float(getWidth() - 270, -70, 350, 250));

                String title = "Biomaterials Cell Counter";
                String subtitle = "Initializing vision pipeline...";

                FontMetrics fm = g2.getFontMetrics(SPLASH_TITLE_FONT);
                int baseX = (getWidth() - fm.stringWidth(title)) / 2;
                int baseY = getHeight() / 2 - 8;

                g2.setFont(SPLASH_TITLE_FONT);
                g2.setColor(new Color(196, 229, 255, 88));
                g2.drawString(title, baseX + 2, baseY + 2);
                g2.setColor(new Color(236, 247, 255));
                g2.drawString(title, baseX, baseY);

                g2.setFont(SPLASH_SUB_FONT);
                g2.setColor(new Color(178, 208, 242));
                FontMetrics subMetrics = g2.getFontMetrics();
                int subX = (getWidth() - subMetrics.stringWidth(subtitle)) / 2;
                g2.drawString(subtitle, subX, baseY + 42);

                g2.dispose();
            }
        }
    }

    private static class AppIcon implements Icon {
        enum Kind {
            SEARCH,
            BOLT,
            PLAY,
            PAUSE,
            STEP,
            RESET,
            EYE,
            FILE,
            GRID,
            SETTINGS,
            HELP,
            SIMULATOR,
            SLIDERS
        }

        private final Kind kind;
        private final Color color;

        AppIcon(Kind kind, Color color) {
            this.kind = kind;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (kind) {
                case SEARCH -> {
                    g2.drawOval(1, 1, 8, 8);
                    g2.drawLine(8, 8, 13, 13);
                }
                case BOLT -> {
                    Path2D bolt = new Path2D.Float();
                    bolt.moveTo(7, 0);
                    bolt.lineTo(3, 7);
                    bolt.lineTo(7, 7);
                    bolt.lineTo(5, 14);
                    bolt.lineTo(12, 6);
                    bolt.lineTo(8, 6);
                    bolt.closePath();
                    g2.fill(bolt);
                }
                case PLAY -> {
                    Path2D tri = new Path2D.Float();
                    tri.moveTo(3, 2);
                    tri.lineTo(12, 7);
                    tri.lineTo(3, 12);
                    tri.closePath();
                    g2.fill(tri);
                }
                case PAUSE -> {
                    g2.fillRoundRect(3, 2, 3, 10, 1, 1);
                    g2.fillRoundRect(9, 2, 3, 10, 1, 1);
                }
                case STEP -> {
                    Path2D tri = new Path2D.Float();
                    tri.moveTo(2, 2);
                    tri.lineTo(9, 7);
                    tri.lineTo(2, 12);
                    tri.closePath();
                    g2.fill(tri);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(11, 2, 11, 12);
                }
                case RESET -> {
                    g2.drawArc(1, 1, 12, 12, 30, 290);
                    Path2D arrow = new Path2D.Float();
                    arrow.moveTo(11, 0);
                    arrow.lineTo(14, 1);
                    arrow.lineTo(12, 4);
                    arrow.closePath();
                    g2.fill(arrow);
                }
                case EYE -> {
                    g2.drawOval(1, 4, 13, 7);
                    g2.fillOval(6, 6, 3, 3);
                }
                case FILE -> {
                    g2.drawRoundRect(2, 1, 10, 12, 2, 2);
                    g2.drawLine(4, 5, 10, 5);
                    g2.drawLine(4, 8, 10, 8);
                    g2.drawLine(4, 11, 8, 11);
                }
                case GRID -> {
                    g2.drawRoundRect(1, 1, 12, 12, 2, 2);
                    g2.drawLine(5, 1, 5, 13);
                    g2.drawLine(9, 1, 9, 13);
                    g2.drawLine(1, 5, 13, 5);
                    g2.drawLine(1, 9, 13, 9);
                }
                case SETTINGS -> {
                    g2.drawOval(2, 2, 10, 10);
                    g2.fillOval(5, 5, 4, 4);
                    g2.drawLine(7, 0, 7, 2);
                    g2.drawLine(7, 12, 7, 14);
                    g2.drawLine(0, 7, 2, 7);
                    g2.drawLine(12, 7, 14, 7);
                }
                case HELP -> {
                    g2.drawOval(1, 1, 12, 12);
                    g2.drawArc(4, 3, 6, 5, 0, 200);
                    g2.drawLine(7, 8, 7, 9);
                    g2.fillOval(6, 11, 2, 2);
                }
                case SIMULATOR -> {
                    g2.drawRoundRect(1, 2, 12, 10, 2, 2);
                    g2.drawLine(3, 0, 3, 2);
                    g2.drawLine(11, 0, 11, 2);
                    g2.drawLine(1, 5, 13, 5);
                    g2.drawLine(3, 7, 6, 7);
                    g2.drawLine(8, 7, 11, 7);
                }
                case SLIDERS -> {
                    g2.drawLine(1, 3, 13, 3);
                    g2.drawLine(1, 7, 13, 7);
                    g2.drawLine(1, 11, 13, 11);
                    g2.fillRoundRect(3, 1, 3, 4, 2, 2);
                    g2.fillRoundRect(8, 5, 3, 4, 2, 2);
                    g2.fillRoundRect(5, 9, 3, 4, 2, 2);
                }
                default -> {
                }
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
