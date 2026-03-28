package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.application.TrackingQualitySummary;
import com.prolymphname.cellcounter.export.ExportMetadata;
import com.prolymphname.cellcounter.simulation.CellSimulationGUI;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import com.prolymphname.cellcounter.ui.AppIcon;
import com.prolymphname.cellcounter.ui.CardPanel;
import com.prolymphname.cellcounter.ui.ChartRefreshController;
import com.prolymphname.cellcounter.ui.GradientPanel;
import com.prolymphname.cellcounter.ui.InlineSettingsPanel;
import com.prolymphname.cellcounter.ui.RoundedBorder;
import com.prolymphname.cellcounter.ui.StartupSplashWindow;
import com.prolymphname.cellcounter.ui.TuningPreviewFrames;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.enforceButtonSize;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.styleConfigCheckBox;
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
    private static final int STEP_HOLD_INITIAL_DELAY_MS = 260;
    private static final int STEP_HOLD_REPEAT_DELAY_MS = 90;
    private static final Color CHIP_CRITICAL = new Color(184, 79, 90);

    private final CellCounterApplicationService appService;
    private final ChartRefreshController chartRefreshController = new ChartRefreshController();

    private boolean videoPlaying = false;
    private boolean paused = false;

    private JLabel rawVideoLabel;
    private JLabel foregroundVideoLabel;
    private JLabel playbackRateValueLabel;
    private JPanel pipelineStateChip;
    private JLabel pipelineStateLabel;
    private JLabel pipelineStateDotLabel;
    private ChartPanel trackStartTimeChartPanel;
    private ChartPanel speedDistributionChartPanel;

    private JButton analyzeButton;
    private JButton fastButton;
    private JButton playButton;
    private JButton frameForwardButton;
    private JButton resetButton;
    private JButton saveResultsButton;
    private JLabel simulatorLink;
    private JButton tuneDetectionButton;
    private JLabel helpButton;
    private JCheckBox mirrorTrackingCheckBox;
    private JPanel trackingQualityPanel;
    private JLabel trackingConfidenceLabel;
    private JLabel trackingActiveLabel;
    private JLabel trackingWatchLabel;
    private InlineSettingsPanel settingsPanel;
    private JSlider playbackRateSlider;
    private JLabel videoPositionValueLabel;
    private final FrameDisplaySurface rawVideoSurface = new FrameDisplaySurface();
    private final FrameDisplaySurface foregroundVideoSurface = new FrameDisplaySurface();

    private Timer videoTimer;
    private Timer stepKeyRepeatTimer;
    private boolean stepKeyHeld = false;

    private final Icon playIcon = new AppIcon(AppIcon.Kind.PLAY, Color.WHITE);
    private final Icon pauseIcon = new AppIcon(AppIcon.Kind.PAUSE, Color.WHITE);

    private static final class FrameDisplaySurface {
        private JLabel label;
        private BufferedImage reusableImage;
        private ImageIcon reusableIcon;
        private int reusableImageType = -1;
    }

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
        contentSplit.setResizeWeight(0.72);
        contentSplit.setContinuousLayout(true);
        contentSplit.setDividerSize(9);
        body.add(contentSplit, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);

        bindActions();
        bindKeyboardShortcuts();
        setInitialControlState();
        setPipelineState("Waiting for file", CHIP_IDLE);
        setPlayButtonPlaying(false);

        videoTimer = new Timer(33, e -> {
            if (appService.isVideoSuccessfullyInitialized() && videoPlaying && !paused) {
                updateFrame(false);
            }
        });
        stepKeyRepeatTimer = new Timer(STEP_HOLD_REPEAT_DELAY_MS, e -> triggerStepButtonFromKeyboard());
        stepKeyRepeatTimer.setInitialDelay(STEP_HOLD_INITIAL_DELAY_MS);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                releaseStepKeyHold();
                appService.releaseVideo();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                releaseStepKeyHold();
            }
        });

        pack();
        setLocationRelativeTo(null);
        contentSplit.setDividerLocation(0.72);
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

        simulatorLink = createHeaderLink("Simulator", AppIcon.Kind.SIMULATOR, "Open Cell Simulation Studio",
                () -> SwingUtilities.invokeLater(() -> new CellSimulationGUI().setVisible(true)));
        helpButton = createHeaderLink("Help", AppIcon.Kind.HELP, "Open documentation (H)", this::openHelpDocumentation);

        pipelineStateDotLabel = new JLabel("\u25CF");
        pipelineStateDotLabel.setFont(FONT_BUTTON);
        pipelineStateDotLabel.setForeground(Color.WHITE);

        pipelineStateLabel = new JLabel("Waiting for file");
        pipelineStateLabel.setFont(FONT_BUTTON);
        pipelineStateLabel.setForeground(Color.WHITE);

        pipelineStateChip = new JPanel(new FlowLayout(FlowLayout.CENTER, SPACE_XXS, 0));
        pipelineStateChip.setOpaque(true);
        pipelineStateChip.setBackground(CHIP_IDLE);
        pipelineStateChip.setBorder(new EmptyBorder(SPACE_XXS, SPACE_S, SPACE_XXS, SPACE_S));
        pipelineStateChip.add(pipelineStateDotLabel);
        pipelineStateChip.add(pipelineStateLabel);

        JPanel statusGroup = new JPanel();
        statusGroup.setOpaque(false);
        statusGroup.setLayout(new BoxLayout(statusGroup, BoxLayout.X_AXIS));
        statusGroup.add(simulatorLink);
        statusGroup.add(Box.createHorizontalStrut(SPACE_XS));
        statusGroup.add(helpButton);
        statusGroup.add(Box.createHorizontalStrut(SPACE_XS));
        statusGroup.add(pipelineStateChip);

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
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        secondRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        analyzeButton = createPrimaryButton("Open Video", new AppIcon(AppIcon.Kind.SEARCH, Color.WHITE));
        fastButton = createSecondaryButton("Fast Analyze", new AppIcon(AppIcon.Kind.BOLT, Color.WHITE));
        playButton = createPrimaryButton("Play/Analyze", playIcon);
        frameForwardButton = createSecondaryButton("", new AppIcon(AppIcon.Kind.STEP, Color.WHITE));
        resetButton = createSecondaryButton("", new AppIcon(AppIcon.Kind.RESET, Color.WHITE));
        saveResultsButton = createPrimaryButton("Save Results", new AppIcon(AppIcon.Kind.FILE, Color.WHITE));
        analyzeButton.setToolTipText("Open Video (O)");
        fastButton.setToolTipText("Fast Analyze (F)");
        playButton.setToolTipText("Play / Pause (P, Esc to pause)");
        playButton.setHorizontalTextPosition(SwingConstants.LEFT);
        playButton.setIconTextGap(SPACE_XS);
        configureIconOnlyButton(frameForwardButton, "Next Frame (N, hold Space, . or Right Arrow)");
        configureIconOnlyButton(resetButton, "Replay (R)");
        saveResultsButton.setToolTipText("Save Results (S)");

        videoPositionValueLabel = createChipLabel(formatFrameChipText(0, 0), CHIP_IDLE);
        videoPositionValueLabel.setFont(FONT_LABEL);
        videoPositionValueLabel.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        videoPositionValueLabel.setPreferredSize(new Dimension(214, 24));

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

        tuneDetectionButton = createSecondaryButton("Settings", new AppIcon(AppIcon.Kind.SLIDERS, Color.WHITE));
        tuneDetectionButton.setFont(FONT_LABEL);
        tuneDetectionButton.setToolTipText("Show or hide compact settings (T)");

        mirrorTrackingCheckBox = new JCheckBox("Mirror Tracking");
        mirrorTrackingCheckBox.setSelected(appService.isMirrorTrackingInRawEnabled());
        mirrorTrackingCheckBox.setToolTipText("Mirror foreground tracking overlays into the raw video pane.");
        styleConfigCheckBox(mirrorTrackingCheckBox);
        mirrorTrackingCheckBox.setFont(FONT_LABEL);

        enforceButtonSize(analyzeButton, 136);
        enforceButtonSize(fastButton, 136);
        enforceButtonSize(playButton, 146);
        enforceButtonSize(frameForwardButton, 52);
        enforceButtonSize(resetButton, 52);
        enforceButtonSize(saveResultsButton, 146);
        enforceButtonSize(tuneDetectionButton, 152);

        topRow.add(analyzeButton);
        topRow.add(fastButton);
        topRow.add(playButton);
        topRow.add(frameForwardButton);
        topRow.add(resetButton);
        topRow.add(saveResultsButton);

        secondRow.add(videoPositionValueLabel);
        secondRow.add(playbackRateValueLabel);
        secondRow.add(playbackRateSlider);
        secondRow.add(tuneDetectionButton);
        secondRow.add(mirrorTrackingCheckBox);
        secondRow.add(buildTrackingQualityPanel());

        settingsPanel = new InlineSettingsPanel(
                appService,
                previewFrames -> {
                    if (previewFrames == null) {
                        return;
                    }
                    try (TuningPreviewFrames frames = previewFrames) {
                        showPreviewFrames(frames.rawFrame(), frames.foregroundFrame());
                    }
                },
                updated -> {
                    appService.setTrackingConfiguration(updated);
                    refreshCurrentVideoFrame();
                    refreshChartsNow();
                    paused = true;
                    videoPlaying = false;
                    videoTimer.stop();
                    setPlayButtonPlaying(false);
                    refreshPipelineStateForCurrentContext();
                },
                this::handleInlineSettingsClosed);
        settingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(topRow);
        content.add(Box.createVerticalStrut(SPACE_XS));
        content.add(secondRow);
        content.add(Box.createVerticalStrut(SPACE_XS));
        content.add(settingsPanel);
        controlsCard.add(content, BorderLayout.CENTER);
        return controlsCard;
    }

    private JPanel buildTrackingQualityPanel() {
        trackingQualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, SPACE_XXS, 0));
        trackingQualityPanel.setOpaque(false);
        trackingQualityPanel.setBorder(new RoundedBorder(new Color(82, 129, 193, 120), 16));

        JLabel title = new JLabel("Tracking Quality");
        title.setFont(FONT_LABEL);
        title.setForeground(TEXT_SECONDARY);

        trackingConfidenceLabel = createChipLabel("Confidence 0", CHIP_IDLE);
        trackingActiveLabel = createChipLabel("Active 0", CHIP_IDLE);
        trackingWatchLabel = createChipLabel("Watch 0", CHIP_IDLE);

        trackingConfidenceLabel.setFont(FONT_LABEL);
        trackingActiveLabel.setFont(FONT_LABEL);
        trackingWatchLabel.setFont(FONT_LABEL);

        trackingQualityPanel.add(title);
        trackingQualityPanel.add(trackingConfidenceLabel);
        trackingQualityPanel.add(trackingActiveLabel);
        trackingQualityPanel.add(trackingWatchLabel);
        trackingQualityPanel.setToolTipText(buildTrackingQualityPanelTooltip(TrackingQualitySummary.empty()));
        return trackingQualityPanel;
    }

    private JPanel buildVideoCard() {
        CardPanel videoCard = createCard("", "", false);

        rawVideoLabel = createVideoDisplayLabel("No video loaded. Click Open Video to begin.");
        rawVideoSurface.label = rawVideoLabel;

        foregroundVideoLabel = createVideoDisplayLabel("Foreground view will appear after video load.");
        foregroundVideoSurface.label = foregroundVideoLabel;

        JPanel dualVideoPanel = new JPanel(new GridLayout(1, 2, SPACE_S, 0));
        dualVideoPanel.setOpaque(false);
        dualVideoPanel.add(createVideoPane("Raw Input + Tracks", rawVideoLabel));
        dualVideoPanel.add(createVideoPane("Foreground + Tracks", foregroundVideoLabel));

        videoCard.add(dualVideoPanel, BorderLayout.CENTER);
        return videoCard;
    }

    private JPanel buildAnalyticsColumn() {
        JPanel rightColumn = new JPanel(new GridLayout(2, 1, 0, SPACE_M));
        rightColumn.setOpaque(false);

        trackStartTimeChartPanel = createCombinedChart(new double[] {}, "Track Start Distribution", "Time (sec)", "Count", 1.0);
        speedDistributionChartPanel = createCombinedChart(new double[] {}, "Speed Distribution", "Speed (px/s)", "Count", 5.0);

        CardPanel trackCard = createCard("", "", false);
        trackCard.add(trackStartTimeChartPanel, BorderLayout.CENTER);
        trackCard.setMinimumSize(new Dimension(280, 200));

        CardPanel speedCard = createCard("", "", false);
        speedCard.add(speedDistributionChartPanel, BorderLayout.CENTER);
        speedCard.setMinimumSize(new Dimension(280, 200));

        rightColumn.add(trackCard);
        rightColumn.add(speedCard);
        return rightColumn;
    }

    private JLabel createVideoDisplayLabel(String emptyText) {
        JLabel label = new JLabel(emptyText, SwingConstants.CENTER);
        label.setFont(FONT_BODY);
        label.setForeground(new Color(215, 230, 250));
        label.setOpaque(true);
        label.setBackground(new Color(7, 19, 40));
        label.setPreferredSize(new Dimension(520, 500));
        return label;
    }

    private JPanel createVideoPane(String title, JLabel label) {
        JPanel wrapper = new JPanel(new BorderLayout(SPACE_XXS, SPACE_XXS));
        wrapper.setOpaque(false);

        JLabel heading = new JLabel(title);
        heading.setFont(FONT_LABEL);
        heading.setForeground(TEXT_SECONDARY);
        wrapper.add(heading, BorderLayout.NORTH);

        JPanel frame = new JPanel(new BorderLayout());
        frame.setOpaque(true);
        frame.setBackground(new Color(7, 19, 40));
        frame.setBorder(new LineBorder(new Color(82, 129, 193, 140), 1, true));
        frame.add(label, BorderLayout.CENTER);
        wrapper.add(frame, BorderLayout.CENTER);
        return wrapper;
    }

    private JLabel createHeaderLink(String text, AppIcon.Kind iconKind, String tooltip, Runnable action) {
        JLabel linkLabel = new JLabel("<html><u>" + text + "</u></html>");
        linkLabel.setFont(FONT_LABEL);
        linkLabel.setForeground(ACCENT);
        linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        linkLabel.setIcon(new AppIcon(iconKind, ACCENT));
        linkLabel.setIconTextGap(SPACE_XXS);
        linkLabel.setToolTipText(tooltip);
        linkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (linkLabel.isEnabled()) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                linkLabel.setForeground(TEXT_PRIMARY);
                linkLabel.setIcon(new AppIcon(iconKind, TEXT_PRIMARY));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                linkLabel.setForeground(ACCENT);
                linkLabel.setIcon(new AppIcon(iconKind, ACCENT));
            }
        });
        return linkLabel;
    }

    private void bindActions() {
        tuneDetectionButton.addActionListener(e -> handleTuneDetection());
        analyzeButton.addActionListener(e -> handleAnalyzeVideo());
        playButton.addActionListener(e -> handlePlayPauseToggle());
        frameForwardButton.addActionListener(e -> handleFrameForward());
        resetButton.addActionListener(e -> handleResetVideo());
        fastButton.addActionListener(e -> handleFastAnalyze());
        saveResultsButton.addActionListener(e -> handleSaveResults());
        mirrorTrackingCheckBox.addItemListener(e -> handleMirrorTrackingToggle());
        playbackRateSlider.addChangeListener(e -> handlePlaybackRateChange());
    }

    private void bindKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        if (rootPane == null) {
            return;
        }
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        Action pressStepAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleSpaceStepPressed();
            }
        };
        Action releaseStepAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleSpaceStepReleased();
            }
        };
        Action singleStepAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleFrameForward();
                }
            }
        };
        Action togglePlayPauseAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handlePlayPauseToggle();
                }
            }
        };
        Action pauseOnlyAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut() && videoPlaying && !paused) {
                    handlePlayPauseToggle();
                }
            }
        };
        Action openVideoAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleAnalyzeVideo();
                }
            }
        };
        Action fastAnalyzeAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleFastAnalyze();
                }
            }
        };
        Action resetAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleResetVideo();
                }
            }
        };
        Action saveResultsAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleSaveResults();
                }
            }
        };
        Action tuneDetectionAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    handleTuneDetection();
                }
            }
        };
        Action helpAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (shouldHandleGlobalShortcut()) {
                    openHelpDocumentation();
                }
            }
        };

        registerSpaceStepBindings(
                inputMap,
                actionMap,
                pressStepAction,
                releaseStepAction);
        registerSpaceStepBindings(
                frameForwardButton.getInputMap(JComponent.WHEN_FOCUSED),
                frameForwardButton.getActionMap(),
                pressStepAction,
                releaseStepAction);

        registerWindowShortcut(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0, true),
                "step-single-period", singleStepAction);
        registerWindowShortcut(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),
                "step-single-right", singleStepAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_N, "step-single-n", singleStepAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_P, "toggle-play-p", togglePlayPauseAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_K, "toggle-play-k", togglePlayPauseAction);
        registerWindowShortcut(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true),
                "pause-only", pauseOnlyAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_O, "open-video", openVideoAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_F, "fast-analyze", fastAnalyzeAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_R, "reset-video", resetAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_S, "save-results", saveResultsAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_T, "tune-detection", tuneDetectionAction);
        registerLetterShortcut(inputMap, actionMap, KeyEvent.VK_H, "open-help", helpAction);
    }

    private void registerSpaceStepBindings(
            InputMap inputMap,
            ActionMap actionMap,
            Action pressStepAction,
            Action releaseStepAction) {
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "step-space-press");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "step-space-release");
        actionMap.put("step-space-press", pressStepAction);
        actionMap.put("step-space-release", releaseStepAction);
    }

    private void registerWindowShortcut(InputMap inputMap, ActionMap actionMap, KeyStroke keyStroke, String actionKey, Action action) {
        inputMap.put(keyStroke, actionKey);
        actionMap.put(actionKey, action);
    }

    private void registerLetterShortcut(
            InputMap inputMap,
            ActionMap actionMap,
            int keyCode,
            String actionKey,
            Action action) {
        registerWindowShortcut(inputMap, actionMap, KeyStroke.getKeyStroke(keyCode, 0, true), actionKey, action);
        registerWindowShortcut(
                inputMap,
                actionMap,
                KeyStroke.getKeyStroke(keyCode, InputEvent.SHIFT_DOWN_MASK, true),
                actionKey + "-shift",
                action);
    }

    private void setInitialControlState() {
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        playbackRateValueLabel.setText(formatPlaybackSpeedText(DEFAULT_VIDEO_RATE));
        chartRefreshController.configureForFps(appService.getFps());
        refreshVideoPositionControls();
        refreshTrackingQualitySummary();
    }

    private void handleTuneDetection() {
        if (settingsPanel != null && settingsPanel.isPanelVisible()) {
            settingsPanel.hidePanel();
            return;
        }

        if (videoPlaying && !paused) {
            videoTimer.stop();
            videoPlaying = false;
            paused = true;
            setPlayButtonPlaying(false);
            setPipelineState("Paused", CHIP_WARNING);
        }
        if (settingsPanel != null) {
            settingsPanel.showPanel(appService.getTrackingConfiguration());
            revalidate();
            repaint();
            setPipelineState("Settings", CHIP_WARNING);
        }
    }

    private void handleSpaceStepPressed() {
        if (stepKeyHeld || !shouldHandleSpaceStepShortcut()) {
            return;
        }
        stepKeyHeld = true;
        triggerStepButtonFromKeyboard();
        if (stepKeyRepeatTimer != null) {
            stepKeyRepeatTimer.restart();
        }
    }

    private void handleSpaceStepReleased() {
        releaseStepKeyHold();
    }

    private boolean shouldHandleSpaceStepShortcut() {
        if (frameForwardButton == null || !frameForwardButton.isEnabled() || !shouldHandleGlobalShortcut()) {
            return false;
        }
        return true;
    }

    private boolean shouldHandleGlobalShortcut() {
        if (!isActive()) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof JTextField
                || focusOwner instanceof JTextArea
                || focusOwner instanceof JPasswordField
                || focusOwner instanceof JFormattedTextField
                || focusOwner instanceof JComboBox<?>) {
            return false;
        }
        return true;
    }

    private void triggerStepButtonFromKeyboard() {
        if (!stepKeyHeld || frameForwardButton == null || !frameForwardButton.isEnabled()) {
            releaseStepKeyHold();
            return;
        }
        frameForwardButton.doClick(0);
    }

    private void releaseStepKeyHold() {
        stepKeyHeld = false;
        if (stepKeyRepeatTimer != null) {
            stepKeyRepeatTimer.stop();
        }
    }

    private void refreshCurrentVideoFrame() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            return;
        }
        showAnalysisFrames(appService.getLastProcessedFrame(), appService.getLastForegroundDisplayFrame());
    }

    private void refreshVideoPositionControls() {
        if (videoPositionValueLabel == null) {
            return;
        }

        if (!appService.isVideoSuccessfullyInitialized()) {
            videoPositionValueLabel.setText(formatFrameChipText(0, 0));
            return;
        }

        int total = Math.max(1, appService.getFrameCount());
        int currentFrameIndex = Math.max(0, appService.getCurrentFrameNumber() - 1);
        int current = clampInt(currentFrameIndex, 0, total - 1);
        videoPositionValueLabel.setText(formatFrameChipText(current + 1, total));
    }

    private String formatFrameChipText(int current, int total) {
        return "Frame: " + current + "/" + total;
    }

    private String formatPlaybackSpeedText(double rate) {
        return String.format("Playback Speed: %.1fx", rate);
    }

    private void refreshTrackingQualitySummary() {
        updateTrackingQualityPanel(appService.getTrackingQualitySummary());
    }

    private void updateTrackingQualityPanel(TrackingQualitySummary summary) {
        if (trackingConfidenceLabel == null || summary == null) {
            return;
        }

        trackingConfidenceLabel.setText("Confidence " + summary.confidencePercent());
        trackingConfidenceLabel.setBackground(resolveConfidenceChipColor(summary.confidencePercent()));
        trackingConfidenceLabel.setToolTipText(buildConfidenceTooltip(summary));

        trackingActiveLabel.setText("Active " + summary.activeTracks());
        trackingActiveLabel.setBackground(summary.activeTracks() > 0 ? CHIP_ACTIVE : CHIP_IDLE);
        trackingActiveLabel.setToolTipText("<html>Tracks currently being maintained on the live frame.<br>"
                + "This count comes from the same overlay state used for the foreground diagnostics pane.</html>");

        trackingWatchLabel.setText("Watch " + summary.watchTracks());
        trackingWatchLabel.setBackground(summary.watchTracks() > 0 ? CHIP_WARNING : CHIP_IDLE);
        trackingWatchLabel.setToolTipText("<html>Tracks that need attention right now because they are missed or flagged<br>"
                + "with current occlusion / collision risk.</html>");

        if (trackingQualityPanel != null) {
            trackingQualityPanel.setToolTipText(buildTrackingQualityPanelTooltip(summary));
        }
    }

    private Color resolveConfidenceChipColor(int confidencePercent) {
        if (confidencePercent >= 75) {
            return CHIP_PLAYING;
        }
        if (confidencePercent >= 50) {
            return CHIP_WARNING;
        }
        return CHIP_CRITICAL;
    }

    private String buildConfidenceTooltip(TrackingQualitySummary summary) {
        return "<html>Composite confidence score for currently active tracks.<br>"
                + "It rewards horizontal span across the frame, track maturity, and clean continuity.<br>"
                + "Longer-lived tracks with larger observed span carry more weight than short/new tracks,<br>"
                + "while active misses plus occlusion/collision risk reduce the score.<br>"
                + "High-confidence tracks: " + summary.highConfidenceTracks() + "</html>";
    }

    private String buildTrackingQualityPanelTooltip(TrackingQualitySummary summary) {
        return "<html>High-level tracking quality summary for the current frame.<br>"
                + "Confidence: " + summary.confidencePercent() + " / 100<br>"
                + "Active tracks: " + summary.activeTracks() + "<br>"
                + "High-confidence tracks: " + summary.highConfidenceTracks() + "<br>"
                + "Watch tracks: " + summary.watchTracks() + "<br>"
                + "Occlusion risk tracks: " + summary.occlusionRiskTracks() + "</html>";
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
        chartPanel.setPreferredSize(new Dimension(300, 150));
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
            playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
            updateVideoTimerDelay(DEFAULT_VIDEO_RATE);
            syncChartRefreshIntervalWithVideo();
            setPipelineState("Ready", CHIP_ACTIVE);

            Mat firstFrame = appService.getLastProcessedFrame();
            if (firstFrame != null && !firstFrame.empty()) {
                showAnalysisFrames(firstFrame, appService.getLastForegroundDisplayFrame());
            } else {
                clearVideoDisplays("Unable to render first frame.", "Foreground view unavailable.");
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
        setPipelineState("Waiting for file", CHIP_IDLE);
        refreshVideoPositionControls();

        JOptionPane.showMessageDialog(this, "Error opening or initializing video file.", "Error", JOptionPane.ERROR_MESSAGE);
        clearVideoDisplays("No video loaded. Click Open Video to begin.", "Foreground view unavailable.");
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
        playbackRateSlider.setValue(rateToSlider(DEFAULT_VIDEO_RATE));
        updateVideoTimerDelay(DEFAULT_VIDEO_RATE);

        appService.resetAnalysisForCurrentVideo();
        Mat firstFrameAfterReset = appService.getLastProcessedFrame();

        if (firstFrameAfterReset != null && !firstFrameAfterReset.empty()) {
            showAnalysisFrames(firstFrameAfterReset, appService.getLastForegroundDisplayFrame());
        } else {
            clearVideoDisplays("Unable to render frame after reset.", "Foreground view unavailable.");
            JOptionPane.showMessageDialog(this, "Failed to prepare video for display after reset.", "Reset Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        setPipelineState("Ready", CHIP_ACTIVE);
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
            showAnalysisFrames(frame, appService.getLastForegroundDisplayFrame());
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
                        showAnalysisFrames(latestFrame, appService.getLastForegroundDisplayFrame());
                    }
                    latestFrame.release();
                    refreshChartsIfDue();
                }
            }

            @Override
            protected void done() {
                Mat finalFrame = appService.getLastProcessedFrame();
                if (finalFrame != null && !finalFrame.empty()) {
                    showAnalysisFrames(finalFrame, appService.getLastForegroundDisplayFrame());
                }
                refreshChartsNow();
                refreshVideoPositionControls();
                setPipelineState("Ready", CHIP_ACTIVE);
                JOptionPane.showMessageDialog(CellCounterGUI.this, "Fast Analysis Complete.", "Done",
                        JOptionPane.INFORMATION_MESSAGE);
                paused = false;
            }
        };
        worker.execute();
    }

    private void handleMirrorTrackingToggle() {
        appService.setMirrorTrackingInRawEnabled(mirrorTrackingCheckBox.isSelected());
        refreshCurrentVideoFrame();
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
        if (pipelineStateLabel != null) {
            pipelineStateLabel.setText("Status: " + state);
            pipelineStateLabel.setForeground(Color.WHITE);
            pipelineStateLabel.setToolTipText("Current application state: " + state);
        }
        if (pipelineStateDotLabel != null) {
            pipelineStateDotLabel.setForeground(Color.WHITE);
            pipelineStateDotLabel.setToolTipText("Current application state: " + state);
        }
        if (pipelineStateChip != null) {
            pipelineStateChip.setBackground(color);
            pipelineStateChip.setToolTipText("Current application state: " + state);
        }
    }

    private void handleInlineSettingsClosed() {
        refreshCurrentVideoFrame();
        refreshPipelineStateForCurrentContext();
        revalidate();
        repaint();
    }

    private void refreshPipelineStateForCurrentContext() {
        if (!appService.isVideoSuccessfullyInitialized()) {
            setPipelineState("Waiting for file", CHIP_IDLE);
            return;
        }
        if (videoPlaying && !paused) {
            setPipelineState("Playing", CHIP_PLAYING);
            return;
        }
        if (!appService.isCaptureActive()) {
            setPipelineState("Complete", CHIP_ACTIVE);
            return;
        }
        if (paused) {
            setPipelineState("Paused", CHIP_WARNING);
            return;
        }
        setPipelineState("Ready", CHIP_ACTIVE);
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
        if (saveResultsButton != null) {
            saveResultsButton.setEnabled(enabled);
        }
        if (helpButton != null) {
            helpButton.setEnabled(enabled);
        }
        if (simulatorLink != null) {
            simulatorLink.setEnabled(enabled);
        }
        if (tuneDetectionButton != null) {
            tuneDetectionButton.setEnabled(enabled);
        }
        if (mirrorTrackingCheckBox != null) {
            mirrorTrackingCheckBox.setEnabled(enabled);
        }
        if (playbackRateSlider != null) {
            playbackRateSlider.setEnabled(enabled);
        }
        if (settingsPanel != null) {
            settingsPanel.setInteractiveEnabled(enabled);
        }
    }

    private int rateToSlider(double rate) {
        return (int) Math.round(rate * 100.0);
    }

    private double sliderToRate() {
        return playbackRateSlider.getValue() / 100.0;
    }

    private void showPreviewFrames(Mat rawPreviewFrame, Mat foregroundPreviewFrame) {
        if (appService.isMirrorTrackingInRawEnabled()) {
            showFrame(rawVideoSurface, rawPreviewFrame);
        }
        showFrame(foregroundVideoSurface, foregroundPreviewFrame);
    }

    private void showAnalysisFrames(Mat rawFrame, Mat foregroundFrame) {
        showFrame(rawVideoSurface, rawFrame);
        showFrame(foregroundVideoSurface, foregroundFrame);
        refreshTrackingQualitySummary();
    }

    private void clearVideoDisplays(String rawText, String foregroundText) {
        clearFrame(rawVideoSurface, rawText);
        clearFrame(foregroundVideoSurface, foregroundText);
        updateTrackingQualityPanel(TrackingQualitySummary.empty());
    }

    private void clearFrame(FrameDisplaySurface surface, String text) {
        if (surface.label == null) {
            return;
        }
        surface.label.setIcon(null);
        surface.label.setText(text);
        surface.label.repaint();
    }

    private void showFrame(FrameDisplaySurface surface, Mat mat) {
        if (surface == null || surface.label == null || mat == null || mat.empty()) {
            return;
        }
        BufferedImage image = matToBufferedImage(surface, mat);
        if (image == null) {
            return;
        }
        if (surface.reusableIcon == null || surface.reusableIcon.getImage() != image) {
            surface.reusableIcon = new ImageIcon(image);
        }
        if (surface.label.getIcon() != surface.reusableIcon) {
            surface.label.setIcon(surface.reusableIcon);
        }
        surface.label.setText(null);
        surface.label.repaint();
    }

    private BufferedImage matToBufferedImage(FrameDisplaySurface surface, Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }

        int type = mat.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        int width = Math.max(1, mat.cols());
        int height = Math.max(1, mat.rows());

        if (surface.reusableImage == null
                || surface.reusableImage.getWidth() != width
                || surface.reusableImage.getHeight() != height
                || surface.reusableImageType != type) {
            surface.reusableImage = new BufferedImage(width, height, type);
            surface.reusableImageType = type;
        }

        byte[] data = ((DataBufferByte) surface.reusableImage.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return surface.reusableImage;
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
