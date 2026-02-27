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
import java.util.Arrays;
import java.util.List;

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
    private JButton saveAnalysisButton;
    private JButton saveFootprintButton;
    private JButton simulatorButton;
    private JButton configurationButton;
    private JToggleButton mog2ViewButton;
    private JSlider playbackRateSlider;

    private Timer videoTimer;

    private final Icon playIcon = new AppIcon(AppIcon.Kind.PLAY, Color.WHITE);
    private final Icon pauseIcon = new AppIcon(AppIcon.Kind.PAUSE, Color.WHITE);

    public CellCounterGUI() {
        this(new CellCounterApplicationService());
    }

    public CellCounterGUI(CellCounterApplicationService appService) {
        this.appService = appService;
        initUI();
    }

    private void initUI() {
        setTitle("Cell Counter | Biomaterials Intelligence");
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

        JLabel subtitle = new JLabel(
                "Production-ready demo UI for migration tracking, kinetic distributions, and export packaging");
        subtitle.setFont(FONT_BODY);
        subtitle.setForeground(TEXT_SECONDARY);

        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        titleGroup.add(title);
        titleGroup.add(Box.createVerticalStrut(SPACE_XXS));
        titleGroup.add(subtitle);

        simulatorButton = createSecondaryButton("Simulator", new AppIcon(AppIcon.Kind.SIMULATOR, Color.WHITE));
        simulatorButton.setFont(FONT_LABEL);
        enforceButtonSize(simulatorButton, 116);

        configurationButton = createSecondaryButton("Configuration", new AppIcon(AppIcon.Kind.SETTINGS, Color.WHITE));
        configurationButton.setFont(FONT_LABEL);
        enforceButtonSize(configurationButton, 134);
        pipelineStateLabel = createChipLabel("Idle", CHIP_IDLE);

        JPanel statusGroup = new JPanel();
        statusGroup.setOpaque(false);
        statusGroup.setLayout(new BoxLayout(statusGroup, BoxLayout.X_AXIS));
        statusGroup.add(simulatorButton);
        statusGroup.add(Box.createHorizontalStrut(SPACE_XS));
        statusGroup.add(configurationButton);
        statusGroup.add(Box.createHorizontalStrut(SPACE_XS));
        statusGroup.add(pipelineStateLabel);

        header.add(titleGroup, BorderLayout.WEST);
        header.add(statusGroup, BorderLayout.EAST);
        return header;
    }

    private JPanel buildControlsCard() {
        CardPanel controlsCard = createCard("Controls Card",
                "Acquisition, playback, and segmentation workflow controls");

        JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, SPACE_XS, 0));
        content.setOpaque(false);

        analyzeButton = createPrimaryButton("Analyze Video", new AppIcon(AppIcon.Kind.SEARCH, Color.WHITE));
        fastButton = createSecondaryButton("Fast Analyze", new AppIcon(AppIcon.Kind.BOLT, Color.WHITE));
        playButton = createPrimaryButton("Play", playIcon);
        frameForwardButton = createSecondaryButton("Step", new AppIcon(AppIcon.Kind.STEP, Color.WHITE));
        resetButton = createSecondaryButton("Reset", new AppIcon(AppIcon.Kind.RESET, Color.WHITE));
        mog2ViewButton = createToggleButton("Mask View", new AppIcon(AppIcon.Kind.EYE, Color.WHITE));
        saveAnalysisButton = createPrimaryButton("Save Analysis", new AppIcon(AppIcon.Kind.FILE, Color.WHITE));
        saveFootprintButton = createSecondaryButton("Save Footprint", new AppIcon(AppIcon.Kind.GRID, Color.WHITE));

        JLabel speedLabel = new JLabel("Playback Speed");
        speedLabel.setFont(FONT_LABEL);
        speedLabel.setForeground(TEXT_SECONDARY);

        playbackRateSlider = new JSlider(10, 500, 100);
        playbackRateSlider.setOpaque(false);
        playbackRateSlider.setPaintTicks(false);
        playbackRateSlider.setPaintLabels(false);
        playbackRateSlider.setFont(FONT_LABEL);
        playbackRateSlider.setPreferredSize(new Dimension(130, 28));
        playbackRateSlider.setMaximumSize(new Dimension(130, 28));

        playbackRateValueLabel = createChipLabel(String.format("%.1fx", DEFAULT_VIDEO_RATE), ACCENT_DEEP);
        playbackRateValueLabel.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));

        enforceButtonSize(analyzeButton, 136);
        enforceButtonSize(fastButton, 136);
        enforceButtonSize(playButton, 108);
        enforceButtonSize(frameForwardButton, 108);
        enforceButtonSize(resetButton, 108);
        enforceButtonSize(mog2ViewButton, 126);
        enforceButtonSize(saveAnalysisButton, 150);
        enforceButtonSize(saveFootprintButton, 156);

        content.add(analyzeButton);
        content.add(fastButton);
        content.add(playButton);
        content.add(frameForwardButton);
        content.add(resetButton);
        content.add(mog2ViewButton);
        content.add(saveAnalysisButton);
        content.add(saveFootprintButton);
        content.add(speedLabel);
        content.add(playbackRateSlider);
        content.add(playbackRateValueLabel);

        controlsCard.add(content, BorderLayout.CENTER);

        return controlsCard;
    }

    private JPanel buildVideoCard() {
        CardPanel videoCard = createCard("Video Card", "High-fidelity live field rendering");

        videoLabel = new JLabel("No video loaded. Click Analyze to begin.", SwingConstants.CENTER);
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

        CardPanel trackCard = createCard("Track Start Card", "Histogram + CDF of initial detections");
        trackCard.add(trackStartTimeChartPanel, BorderLayout.CENTER);
        trackCard.setMinimumSize(new Dimension(360, 260));

        CardPanel speedCard = createCard("Speed Card", "Histogram + CDF of observed instantaneous speeds");
        speedCard.add(speedDistributionChartPanel, BorderLayout.CENTER);
        speedCard.setMinimumSize(new Dimension(360, 260));

        rightColumn.add(trackCard);
        rightColumn.add(speedCard);
        return rightColumn;
    }

    private void bindActions() {
        simulatorButton.addActionListener(e -> SwingUtilities.invokeLater(() -> new CellSimulationGUI().setVisible(true)));
        configurationButton.addActionListener(e -> handleConfigureTracking());
        analyzeButton.addActionListener(e -> handleAnalyzeVideo());
        playButton.addActionListener(e -> handlePlayPauseToggle());
        frameForwardButton.addActionListener(e -> handleFrameForward());
        resetButton.addActionListener(e -> handleResetVideo());
        fastButton.addActionListener(e -> handleFastAnalyze());
        saveAnalysisButton.addActionListener(e -> handleSaveAnalysis());
        saveFootprintButton.addActionListener(e -> handleSaveFootprintData());
        mog2ViewButton.addItemListener(this::handleMOG2Toggle);
        playbackRateSlider.addChangeListener(e -> handlePlaybackRateChange());
    }

    private void setInitialControlState() {
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        playbackRateValueLabel.setText(String.format("%.1fx", DEFAULT_VIDEO_RATE));
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
            setPipelineState("Configured", CHIP_ACTIVE);
        } else {
            setPipelineState("Idle", CHIP_IDLE);
        }

        String message = wasPlaying
                ? "Tracking configuration applied. Playback was paused and reset to frame 1."
                : "Tracking configuration applied.";
        JOptionPane.showMessageDialog(this, message, "Configuration", JOptionPane.INFORMATION_MESSAGE);
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
        NumberAxis yAxisRight = new NumberAxis("Cumulative Probability");
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
            return;
        }

        videoPlaying = false;
        paused = false;
        setPlayButtonPlaying(false);
        setPipelineState("Idle", CHIP_IDLE);

        JOptionPane.showMessageDialog(this, "Error opening or initializing video file.", "Error", JOptionPane.ERROR_MESSAGE);
        videoLabel.setIcon(null);
        videoLabel.setText("No video loaded. Click Analyze to begin.");
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
    }

    private void handlePlaybackRateChange() {
        double rate = sliderToRate();
        playbackRateValueLabel.setText(String.format("%.1fx", rate));

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
            return;
        }

        if (frame == null && !appService.isCaptureActive() && (videoPlaying || paused)) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Complete", CHIP_ACTIVE);
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
            JOptionPane.showMessageDialog(this, "Please load a video first using Analyze.", "No Video",
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
            if (!appService.isCaptureActive()) {
                setPlayButtonPlaying(false);
                videoPlaying = false;
                setPipelineState("Complete", CHIP_ACTIVE);
            }
        }
    }

    private void handleSaveAnalysis() {
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
        chooser.setDialogTitle("Save Analysis Data");
        chooser.setSelectedFile(new File("analysis_results.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                appService.saveAnalysisCsv(file, exportMetadata);
                JOptionPane.showMessageDialog(this, "Analysis saved to " + file.getAbsolutePath(), "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException | IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, "Error saving analysis: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleSaveFootprintData() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            JOptionPane.showMessageDialog(this, "No video has been loaded or analysis performed for footprint data.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] metadata = promptForMetadata();
        if (metadata == null) {
            return;
        }

        ExportMetadata exportMetadata = new ExportMetadata(metadata[0], metadata[1], metadata[2]);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Footprint Data");
        chooser.setSelectedFile(new File("footprint_data.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                appService.saveFootprintCsv(file, exportMetadata);
                JOptionPane.showMessageDialog(this, "Footprint data saved to " + file.getAbsolutePath(), "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException | IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, "Error saving footprint data: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setPlayButtonPlaying(boolean isPlaying) {
        if (isPlaying) {
            playButton.setText("Pause");
            playButton.setIcon(pauseIcon);
        } else {
            playButton.setText("Play");
            playButton.setIcon(playIcon);
        }
    }

    private void setPipelineState(String state, Color color) {
        pipelineStateLabel.setText(state);
        pipelineStateLabel.setOpaque(true);
        pipelineStateLabel.setBackground(color);
        pipelineStateLabel.setForeground(Color.WHITE);
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
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(SPACE_S, SPACE_S));
        card.setBorder(new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));

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
            SIMULATOR
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
                case SIMULATOR -> {
                    g2.drawRoundRect(1, 2, 12, 10, 2, 2);
                    g2.drawLine(3, 0, 3, 2);
                    g2.drawLine(11, 0, 11, 2);
                    g2.drawLine(1, 5, 13, 5);
                    g2.drawLine(3, 7, 6, 7);
                    g2.drawLine(8, 7, 11, 7);
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
