package com.prolymphname.cellcounter.ui;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.trackingadapter.TrackerAlgorithm;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.util.List;
import java.util.function.Consumer;

import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createCard;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createPrimaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createSecondaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.enforceButtonSize;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.styleConfigCheckBox;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BODY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_H2;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_LABEL;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_M;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_S;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XXS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_PRIMARY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_SECONDARY;

public class InlineSettingsPanel extends CardPanel {
    private final CellCounterApplicationService appService;
    private final Consumer<TuningPreviewFrames> previewConsumer;
    private final Consumer<TrackingConfiguration> applyConsumer;
    private final Runnable closeConsumer;

    private TrackingConfiguration appliedConfiguration;
    private boolean suppressPreview = false;
    private boolean externallyEnabled = true;
    private boolean previewBusy = false;

    private JSpinner mog2HistorySpinner;
    private JSpinner mog2VarThresholdSpinner;
    private JSpinner maskThresholdSpinner;
    private JSpinner minContourAreaSpinner;
    private JSpinner maxRectCircumferenceSpinner;
    private JSpinner morphologyKernelSpinner;
    private JSpinner morphologyOpenSpinner;
    private JSpinner morphologyDilateSpinner;
    private JSpinner maxAssociationDistanceSpinner;
    private JSpinner maxFramesDisappearedSpinner;
    private JSpinner maxVerticalDisplacementSpinner;
    private JSpinner minHorizontalMovementSpinner;
    private JCheckBox detectShadowsCheckBox;
    private JComboBox<TrackerAlgorithm> trackerAlgorithmCombo;
    private JLabel statusLabel;
    private javax.swing.JButton applyButton;
    private javax.swing.JButton resetButton;
    private javax.swing.JButton hideButton;

    private SwingWorker<TuningPreviewFrames, Void> previewWorker;
    private TrackingConfiguration queuedPreviewConfig;

    public InlineSettingsPanel(
            CellCounterApplicationService appService,
            Consumer<TuningPreviewFrames> previewConsumer,
            Consumer<TrackingConfiguration> applyConsumer,
            Runnable closeConsumer) {
        this.appService = appService;
        this.previewConsumer = previewConsumer;
        this.applyConsumer = applyConsumer;
        this.closeConsumer = closeConsumer;
        buildUi();
        setVisible(false);
    }

    public void showPanel(TrackingConfiguration configuration) {
        appliedConfiguration = configuration;
        loadControlsFromConfiguration(configuration);
        setVisible(true);
        requestPreview();
    }

    public void hidePanel() {
        cancelPreview();
        setVisible(false);
        closeConsumer.run();
    }

    public void setInteractiveEnabled(boolean enabled) {
        externallyEnabled = enabled;
        updateInteractiveState();
    }

    public boolean isPanelVisible() {
        return isVisible();
    }

    private void buildUi() {
        setLayout(new BorderLayout(SPACE_S, SPACE_S));
        setBorder(new EmptyBorder(SPACE_XS, SPACE_S, SPACE_XS, SPACE_S));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        JLabel title = new JLabel("Settings");
        title.setFont(FONT_H2);
        title.setForeground(TEXT_PRIMARY);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title);
        header.add(Box.createHorizontalGlue());
        add(header, BorderLayout.NORTH);

        mog2HistorySpinner = createIntegerSpinner(30, 1200, 500, 10);
        mog2VarThresholdSpinner = createIntegerSpinner(1, 200, 50, 1);
        maskThresholdSpinner = createIntegerSpinner(0, 255, 25, 1);
        minContourAreaSpinner = createIntegerSpinner(0, 600, 5, 1);
        maxRectCircumferenceSpinner = createIntegerSpinner(20, 500, 90, 1);
        morphologyKernelSpinner = createIntegerSpinner(1, 15, 3, 2);
        morphologyOpenSpinner = createIntegerSpinner(0, 8, 2, 1);
        morphologyDilateSpinner = createIntegerSpinner(0, 8, 2, 1);
        maxAssociationDistanceSpinner = createIntegerSpinner(30, 350, 175, 1);
        maxFramesDisappearedSpinner = createIntegerSpinner(1, 1000, 10, 1);
        maxVerticalDisplacementSpinner = createIntegerSpinner(0, 500, 40, 1);
        minHorizontalMovementSpinner = createIntegerSpinner(-100, 100, -3, 1);

        detectShadowsCheckBox = new JCheckBox("Enable MOG2 shadows");
        detectShadowsCheckBox.setSelected(false);
        styleConfigCheckBox(detectShadowsCheckBox);

        trackerAlgorithmCombo = new JComboBox<>(TrackerAlgorithm.values());
        trackerAlgorithmCombo.setFont(FONT_LABEL);
        trackerAlgorithmCombo.setPreferredSize(new Dimension(120, 26));

        JPanel groups = new JPanel(new GridBagLayout());
        groups.setOpaque(false);
        GridBagConstraints groupGbc = new GridBagConstraints();
        groupGbc.gridx = 0;
        groupGbc.gridy = 0;
        groupGbc.weightx = 1.0;
        groupGbc.fill = GridBagConstraints.HORIZONTAL;
        groupGbc.insets = new Insets(0, 0, 0, SPACE_XS);
        groups.add(createSettingsGroup("Segmentation", List.of(
                new RowSpec("History", mog2HistorySpinner),
                new RowSpec("Variance", mog2VarThresholdSpinner),
                new RowSpec("Mask Threshold", maskThresholdSpinner),
                new RowSpec("Min Contour", minContourAreaSpinner),
                new RowSpec("Max Circumf.", maxRectCircumferenceSpinner))), groupGbc);

        groupGbc.gridx++;
        groups.add(createSettingsGroup("Morphology", List.of(
                new RowSpec("Kernel (odd)", morphologyKernelSpinner),
                new RowSpec("Open Iter.", morphologyOpenSpinner),
                new RowSpec("Dilate Iter.", morphologyDilateSpinner),
                new RowSpec("Shadows", detectShadowsCheckBox))), groupGbc);

        groupGbc.gridx++;
        groupGbc.insets = new Insets(0, 0, 0, 0);
        groups.add(createSettingsGroup("Tracking", List.of(
                new RowSpec("Association", maxAssociationDistanceSpinner),
                new RowSpec("Disappear", maxFramesDisappearedSpinner),
                new RowSpec("Max Vertical", maxVerticalDisplacementSpinner),
                new RowSpec("Min Horiz.", minHorizontalMovementSpinner),
                new RowSpec("Algorithm", trackerAlgorithmCombo))), groupGbc);

        add(groups, BorderLayout.CENTER);

        statusLabel = new JLabel("Adjust values to preview on the current frame.");
        statusLabel.setFont(FONT_BODY);
        statusLabel.setForeground(TEXT_SECONDARY);

        applyButton = createPrimaryButton("Apply", null);
        resetButton = createSecondaryButton("Reset", null);
        hideButton = createSecondaryButton("Hide", null);
        enforceButtonSize(applyButton, 92);
        enforceButtonSize(resetButton, 92);
        enforceButtonSize(hideButton, 92);

        JPanel actionRow = new JPanel(new BorderLayout(SPACE_M, 0));
        actionRow.setOpaque(false);
        actionRow.add(statusLabel, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, SPACE_XS, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(resetButton);
        buttonRow.add(hideButton);
        buttonRow.add(applyButton);
        actionRow.add(buttonRow, BorderLayout.EAST);

        add(actionRow, BorderLayout.SOUTH);
        registerListeners();
        updateInteractiveState();
    }

    private CardPanel createSettingsGroup(String title, List<RowSpec> rows) {
        CardPanel group = createCard("", "", false);
        group.setBorder(new EmptyBorder(SPACE_S, SPACE_S, SPACE_S, SPACE_S));

        JLabel heading = new JLabel(title);
        heading.setFont(FONT_LABEL);
        heading.setForeground(TEXT_PRIMARY);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, SPACE_XXS, 0);

        for (RowSpec row : rows) {
            content.add(createCompactRow(row.label(), row.component()), gbc);
            gbc.gridy++;
        }

        group.add(heading, BorderLayout.NORTH);
        group.add(content, BorderLayout.CENTER);
        return group;
    }

    private JPanel createCompactRow(String labelText, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(SPACE_XS, 0));
        row.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_SECONDARY);
        label.setPreferredSize(new Dimension(92, 22));

        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    private JSpinner createIntegerSpinner(int min, int max, int value, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        styleSpinner(spinner);
        return spinner;
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(FONT_LABEL);
        spinner.setPreferredSize(new Dimension(82, 24));
        spinner.setMaximumSize(new Dimension(82, 24));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        JFormattedTextField textField = editor.getTextField();
        textField.setColumns(4);
        textField.setFont(FONT_LABEL);
        textField.setBackground(Color.WHITE);
        textField.setForeground(Color.BLACK);
        textField.setCaretColor(Color.BLACK);
        NumberFormat format = ((JSpinner.NumberEditor) spinner.getEditor()).getFormat();
        format.setGroupingUsed(false);
    }

    private void registerListeners() {
        List<JSpinner> spinners = List.of(
                mog2HistorySpinner,
                mog2VarThresholdSpinner,
                maskThresholdSpinner,
                minContourAreaSpinner,
                maxRectCircumferenceSpinner,
                morphologyKernelSpinner,
                morphologyOpenSpinner,
                morphologyDilateSpinner,
                maxAssociationDistanceSpinner,
                maxFramesDisappearedSpinner,
                maxVerticalDisplacementSpinner,
                minHorizontalMovementSpinner);

        for (JSpinner spinner : spinners) {
            spinner.addChangeListener(e -> requestPreview());
        }
        detectShadowsCheckBox.addActionListener(e -> requestPreview());
        trackerAlgorithmCombo.addActionListener(e -> requestPreview());

        applyButton.addActionListener(e -> {
            TrackingConfiguration updated = buildWorkingConfiguration();
            appliedConfiguration = updated;
            applyConsumer.accept(updated);
            statusLabel.setText("Settings applied.");
        });
        resetButton.addActionListener(e -> {
            if (appliedConfiguration != null) {
                loadControlsFromConfiguration(appliedConfiguration);
                requestPreview();
            }
        });
        hideButton.addActionListener(e -> hidePanel());
    }

    private void requestPreview() {
        if (suppressPreview || !isVisible() || !externallyEnabled) {
            return;
        }
        TrackingConfiguration requestedConfig = buildWorkingConfiguration();
        if (previewWorker != null && !previewWorker.isDone()) {
            queuedPreviewConfig = requestedConfig;
            return;
        }
        startPreview(requestedConfig);
    }

    private void startPreview(TrackingConfiguration configuration) {
        previewBusy = true;
        updateInteractiveState();
        statusLabel.setText("Rendering preview...");
        previewWorker = new SwingWorker<>() {
            @Override
            protected TuningPreviewFrames doInBackground() {
                return appService.previewCurrentFramePairForTuning(configuration);
            }

            @Override
            protected void done() {
                try {
                    TuningPreviewFrames previewFrames = get();
                    if (previewFrames != null) {
                        previewConsumer.accept(previewFrames);
                        statusLabel.setText("Preview updated.");
                    } else {
                        statusLabel.setText("Preview unavailable.");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Preview error: " + ex.getMessage());
                } finally {
                    if (queuedPreviewConfig != null) {
                        TrackingConfiguration nextConfiguration = queuedPreviewConfig;
                        queuedPreviewConfig = null;
                        startPreview(nextConfiguration);
                    } else {
                        previewBusy = false;
                        updateInteractiveState();
                    }
                }
            }
        };
        previewWorker.execute();
    }

    private void cancelPreview() {
        if (previewWorker != null && !previewWorker.isDone()) {
            previewWorker.cancel(true);
        }
        previewWorker = null;
        queuedPreviewConfig = null;
        previewBusy = false;
        updateInteractiveState();
    }

    private void updateInteractiveState() {
        boolean enabled = externallyEnabled && !previewBusy;
        List<JComponent> inputs = List.of(
                mog2HistorySpinner,
                mog2VarThresholdSpinner,
                maskThresholdSpinner,
                minContourAreaSpinner,
                maxRectCircumferenceSpinner,
                morphologyKernelSpinner,
                morphologyOpenSpinner,
                morphologyDilateSpinner,
                maxAssociationDistanceSpinner,
                maxFramesDisappearedSpinner,
                maxVerticalDisplacementSpinner,
                minHorizontalMovementSpinner,
                detectShadowsCheckBox,
                trackerAlgorithmCombo,
                applyButton,
                resetButton,
                hideButton);
        for (JComponent component : inputs) {
            if (component == hideButton) {
                component.setEnabled(externallyEnabled);
            } else {
                component.setEnabled(enabled);
            }
        }
    }

    private void loadControlsFromConfiguration(TrackingConfiguration configuration) {
        suppressPreview = true;
        mog2HistorySpinner.setValue(configuration.getMog2HistoryFrames());
        mog2VarThresholdSpinner.setValue((int) Math.round(configuration.getMog2VarThreshold()));
        maskThresholdSpinner.setValue((int) Math.round(configuration.getNormalizedMaskThreshold()));
        minContourAreaSpinner.setValue((int) Math.round(configuration.getMinContourArea()));
        maxRectCircumferenceSpinner.setValue((int) Math.round(configuration.getMaxRectCircumference()));
        morphologyKernelSpinner.setValue(toOdd(configuration.getMorphologyKernelSize()));
        morphologyOpenSpinner.setValue(configuration.getMorphologyOpenIterations());
        morphologyDilateSpinner.setValue(configuration.getMorphologyDilateIterations());
        maxAssociationDistanceSpinner.setValue((int) Math.round(configuration.getMaxAssociationDistancePixels()));
        maxFramesDisappearedSpinner.setValue(configuration.getMaxFramesDisappeared());
        maxVerticalDisplacementSpinner.setValue((int) Math.round(configuration.getMaxVerticalDisplacementPixels()));
        minHorizontalMovementSpinner.setValue((int) Math.round(configuration.getMinHorizontalMovementPixels()));
        detectShadowsCheckBox.setSelected(configuration.isMog2DetectShadows());
        trackerAlgorithmCombo.setSelectedItem(configuration.getTrackerAlgorithm());
        suppressPreview = false;
    }

    private TrackingConfiguration buildWorkingConfiguration() {
        return new TrackingConfiguration(
                ((Number) maxFramesDisappearedSpinner.getValue()).intValue(),
                ((Number) minContourAreaSpinner.getValue()).intValue(),
                ((Number) maxRectCircumferenceSpinner.getValue()).intValue(),
                ((Number) maxVerticalDisplacementSpinner.getValue()).intValue(),
                ((Number) minHorizontalMovementSpinner.getValue()).intValue(),
                ((Number) maxAssociationDistanceSpinner.getValue()).intValue(),
                ((Number) mog2HistorySpinner.getValue()).intValue(),
                ((Number) mog2VarThresholdSpinner.getValue()).intValue(),
                detectShadowsCheckBox.isSelected(),
                toOdd(((Number) morphologyKernelSpinner.getValue()).intValue()),
                ((Number) morphologyOpenSpinner.getValue()).intValue(),
                ((Number) morphologyDilateSpinner.getValue()).intValue(),
                ((Number) maskThresholdSpinner.getValue()).intValue(),
                (TrackerAlgorithm) trackerAlgorithmCombo.getSelectedItem()).normalized();
    }

    private int toOdd(int value) {
        int normalized = Math.max(1, value);
        return normalized % 2 == 0 ? normalized + 1 : normalized;
    }

    private record RowSpec(String label, JComponent component) {
    }
}
