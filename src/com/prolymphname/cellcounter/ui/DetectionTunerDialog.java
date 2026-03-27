package com.prolymphname.cellcounter.ui;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import com.prolymphname.cellcounter.trackingadapter.TrackerAlgorithm;
import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createCard;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createChipLabel;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createPrimaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.createSecondaryButton;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.enforceButtonSize;
import static com.prolymphname.cellcounter.ui.CellCounterComponentFactory.styleConfigCheckBox;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.ACCENT_DEEP;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_BODY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.FONT_LABEL;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_M;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.SPACE_XS;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_PRIMARY;
import static com.prolymphname.cellcounter.ui.CellCounterUiTheme.TEXT_SECONDARY;

public class DetectionTunerDialog {
    private final JFrame owner;
    private final CellCounterApplicationService appService;
    private final TrackingConfiguration initialConfiguration;
    private final Consumer<TuningPreviewFrames> previewConsumer;
    private final Consumer<TrackingConfiguration> applyConsumer;
    private final Runnable closeConsumer;

    public DetectionTunerDialog(
            JFrame owner,
            CellCounterApplicationService appService,
            TrackingConfiguration initialConfiguration,
            Consumer<TuningPreviewFrames> previewConsumer,
            Consumer<TrackingConfiguration> applyConsumer,
            Runnable closeConsumer) {
        this.owner = owner;
        this.appService = appService;
        this.initialConfiguration = initialConfiguration;
        this.previewConsumer = previewConsumer;
        this.applyConsumer = applyConsumer;
        this.closeConsumer = closeConsumer;
    }

    public void open() {
        TrackingConfiguration[] appliedConfig = new TrackingConfiguration[] { initialConfiguration };

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

        JComboBox<TrackerAlgorithm> trackerAlgorithmCombo = new JComboBox<>(TrackerAlgorithm.values());
        trackerAlgorithmCombo.setSelectedItem(appliedConfig[0].getTrackerAlgorithm());
        trackerAlgorithmCombo.setFont(FONT_LABEL);
        trackerAlgorithmCombo.setToolTipText("Tracker assignment algorithm used during analysis.");
        trackerAlgorithmCombo.setOpaque(false);
        trackerAlgorithmCombo.setPreferredSize(new Dimension(220, 28));

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

        JLabel statusLabel = new JLabel("Adjust sliders and release to preview raw and foreground tracking on the current frame.");
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
        addTuningComponentRow(form, gbc, "Tracker Algorithm", trackerAlgorithmCombo);
        addTuningCheckboxRow(form, gbc, detectShadowsCheck);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(new LineBorder(new java.awt.Color(82, 129, 193, 140), 1, true));
        scrollPane.setPreferredSize(new Dimension(700, 420));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(new java.awt.Color(8, 23, 52));
        scrollPane.setOpaque(false);

        CardPanel dialogCard = createCard("Settings", "Interactive segmentation preview for paused frame", true);
        dialogCard.add(scrollPane, BorderLayout.CENTER);

        javax.swing.JButton applyButton = createPrimaryButton("Apply Parameters", null);
        javax.swing.JButton resetButton = createSecondaryButton("Reset Sliders", null);
        javax.swing.JButton closeButton = createSecondaryButton("Close", null);
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

        JDialog dialog = new JDialog(owner, "Settings", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(root);
        dialog.setSize(820, 640);
        dialog.setMinimumSize(new Dimension(780, 580));
        dialog.setLocationRelativeTo(owner);

        final boolean[] suppressPreview = new boolean[] { false };

        List<JSlider> previewSliders = List.of(
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
            trackerAlgorithmCombo.setEnabled(enabled);
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
            trackerAlgorithmCombo.setSelectedItem(appliedConfig[0].getTrackerAlgorithm());
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
                maskThresholdSlider.getValue(),
                (TrackerAlgorithm) trackerAlgorithmCombo.getSelectedItem()).normalized();

        class PreviewRunner {
            private SwingWorker<TuningPreviewFrames, Void> worker;
            private TrackingConfiguration queuedConfig;

            void request(TrackingConfiguration cfg) {
                if (!dialog.isDisplayable()) {
                    return;
                }
                if (worker != null && !worker.isDone()) {
                    queuedConfig = cfg;
                    return;
                }
                start(cfg);
            }

            private void start(TrackingConfiguration cfg) {
                statusLabel.setText("Rendering raw and foreground previews on the current frame...");
                setTunerParameterInputsEnabled.accept(false);
                worker = new SwingWorker<>() {
                    @Override
                    protected TuningPreviewFrames doInBackground() {
                        return appService.previewCurrentFramePairForTuning(cfg);
                    }

                    @Override
                    protected void done() {
                        try {
                            TuningPreviewFrames previewFrames = get();
                            if (previewFrames != null) {
                                previewConsumer.accept(previewFrames);
                                statusLabel.setText("Preview updated.");
                            } else {
                                statusLabel.setText("Preview unavailable for this frame.");
                            }
                        } catch (Exception ex) {
                            statusLabel.setText("Preview error: " + ex.getMessage());
                        } finally {
                            if (queuedConfig != null) {
                                TrackingConfiguration nextCfg = queuedConfig;
                                queuedConfig = null;
                                start(nextCfg);
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
                setTunerParameterInputsEnabled.accept(true);
            }
        }

        PreviewRunner previewRunner = new PreviewRunner();

        Runnable requestPreview = () -> {
            if (suppressPreview[0]) {
                return;
            }
            previewRunner.request(buildWorkingConfig.get());
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

        applyButton.addActionListener(e -> {
            TrackingConfiguration updated = buildWorkingConfig.get();
            appliedConfig[0] = updated;
            applyConsumer.accept(updated);
            statusLabel.setText("Parameters applied.");
        });

        resetButton.addActionListener(e -> {
            loadSlidersFromApplied.run();
            requestPreview.run();
        });

        closeButton.addActionListener(e -> dialog.dispose());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                previewRunner.cancel();
                closeConsumer.run();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                previewRunner.cancel();
                closeConsumer.run();
            }
        });

        updateValueLabels.run();
        requestPreview.run();
        dialog.setVisible(true);
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
        chip.setBorder(new EmptyBorder(4, SPACE_XS, 4, SPACE_XS));
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

    private void addTuningComponentRow(JPanel form, GridBagConstraints gbc, String labelText, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(SPACE_XS, 0));
        row.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(FONT_BODY);
        label.setForeground(TEXT_PRIMARY);
        label.setPreferredSize(new Dimension(220, 22));

        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);

        form.add(row, gbc);
        gbc.gridy++;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int toOdd(int value) {
        int normalized = Math.max(1, value);
        return normalized % 2 == 0 ? normalized + 1 : normalized;
    }
}
