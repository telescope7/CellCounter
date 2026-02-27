package com.prolymphname.cellcounter.simulation;

import org.opencv.core.Core;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class CellSimulationGUI extends JFrame {
    private static final int SPACE_XXS = 4;
    private static final int SPACE_XS = 8;
    private static final int SPACE_S = 12;
    private static final int SPACE_M = 16;
    private static final int SPACE_L = 24;
    private static final int RADIUS_CARD = 20;

    private static final Color BG_TOP = new Color(3, 10, 24);
    private static final Color BG_BOTTOM = new Color(8, 23, 52);
    private static final Color CARD_TOP = new Color(15, 33, 66, 232);
    private static final Color CARD_BOTTOM = new Color(10, 24, 50, 222);
    private static final Color BORDER_SOFT = new Color(122, 167, 234, 90);
    private static final Color TEXT_PRIMARY = new Color(235, 245, 255);
    private static final Color TEXT_SECONDARY = new Color(175, 203, 236);
    private static final Color PRIMARY_ACTION = new Color(38, 102, 255);

    private static final Font FONT_H1 = resolveFont(new String[]{"Avenir Next", "Segoe UI", "Helvetica Neue"}, Font.BOLD, 24);
    private static final Font FONT_H2 = resolveFont(new String[]{"Avenir Next", "Segoe UI", "Helvetica Neue"}, Font.BOLD, 16);
    private static final Font FONT_BODY = resolveFont(new String[]{"Avenir Next", "Segoe UI", "Helvetica Neue"}, Font.PLAIN, 13);
    private static final Font FONT_LABEL = resolveFont(new String[]{"Avenir Next", "Segoe UI", "Helvetica Neue"}, Font.BOLD, 12);

    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JSpinner cellCountSpinner;
    private final JSpinner delaySpinner;
    private final JSpinner fpsSpinner;
    private final JSpinner lengthSpinner;
    private final JSpinner cellRadiusSpinner;
    private final JSpinner velocitySpinner;
    private final JSpinner seedSpinner;
    private final JTextField outputBaseField;
    private final JTextField outputDirectoryField;

    private final JButton browseButton;
    private final JButton generateButton;

    private final CellSimulationGenerator generator = new CellSimulationGenerator();

    public CellSimulationGUI() {
        super("Cell Simulation Studio");

        CellSimulationParameters defaults = CellSimulationParameters.defaults();
        widthSpinner = new JSpinner(new SpinnerNumberModel(defaults.width(), 32, 4096, 10));
        heightSpinner = new JSpinner(new SpinnerNumberModel(defaults.height(), 32, 4096, 10));
        cellCountSpinner = new JSpinner(new SpinnerNumberModel(defaults.cellCount(), 1, 100000, 10));
        delaySpinner = new JSpinner(new SpinnerNumberModel(defaults.delaySeconds(), 0, 3600, 1));
        fpsSpinner = new JSpinner(new SpinnerNumberModel(defaults.fps(), 1, 240, 1));
        lengthSpinner = new JSpinner(new SpinnerNumberModel(defaults.lengthSeconds(), 1, 36000, 1));
        cellRadiusSpinner = new JSpinner(new SpinnerNumberModel(defaults.cellRadiusPx(), 1, 200, 1));
        velocitySpinner = new JSpinner(new SpinnerNumberModel(defaults.baseVelocityPxPerFrame(), 0.01, 500.0, 0.1));
        seedSpinner = new JSpinner(new SpinnerNumberModel(defaults.randomSeed(), Long.MIN_VALUE, Long.MAX_VALUE, 1L));
        outputBaseField = new JTextField(defaults.outputBaseName());
        outputDirectoryField = new JTextField(defaults.outputDirectory().toAbsolutePath().toString());

        browseButton = new JButton("Browse");
        generateButton = new JButton("Generate Ground Truth");

        initUi();
        bindActions();
    }

    private void initUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 620));
        setPreferredSize(new Dimension(980, 700));

        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(SPACE_L, SPACE_L));
        root.setBorder(new EmptyBorder(SPACE_L, SPACE_L, SPACE_L, SPACE_L));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildFormCard(), BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(SPACE_M, SPACE_M));
        header.setOpaque(false);

        JLabel title = new JLabel("Cell Simulation Studio");
        title.setFont(FONT_H1);
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Generate synthetic rolling-cell videos and ground-truth CSVs for tracking validation");
        subtitle.setFont(FONT_BODY);
        subtitle.setForeground(TEXT_SECONDARY);

        styleButton(generateButton, true);
        generateButton.setPreferredSize(new Dimension(208, 36));

        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        titleGroup.add(title);
        titleGroup.add(Box.createVerticalStrut(SPACE_XXS));
        titleGroup.add(subtitle);

        JPanel actionGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionGroup.setOpaque(false);
        actionGroup.add(generateButton);

        header.add(titleGroup, BorderLayout.WEST);
        header.add(actionGroup, BorderLayout.EAST);
        return header;
    }

    private JPanel buildFormCard() {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(SPACE_S, SPACE_S));
        card.setBorder(new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));

        JLabel title = new JLabel("Simulation Parameters");
        title.setFont(FONT_H2);
        title.setForeground(TEXT_PRIMARY);
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        styleInput(widthSpinner);
        styleInput(heightSpinner);
        styleInput(cellCountSpinner);
        styleInput(delaySpinner);
        styleInput(fpsSpinner);
        styleInput(lengthSpinner);
        styleInput(cellRadiusSpinner);
        styleInput(velocitySpinner);
        styleInput(seedSpinner);
        styleTextField(outputBaseField);
        styleTextField(outputDirectoryField);
        styleButton(browseButton, false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, SPACE_XS, SPACE_M);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, gbc, "Width", widthSpinner);
        addRow(form, gbc, "Height", heightSpinner);
        addRow(form, gbc, "Cell Count", cellCountSpinner);
        addRow(form, gbc, "Delay Before First Cell (s)", delaySpinner);
        addRow(form, gbc, "Frames Per Second", fpsSpinner);
        addRow(form, gbc, "Length (s)", lengthSpinner);
        addRow(form, gbc, "Cell Radius (px)", cellRadiusSpinner);
        addRow(form, gbc, "Base Velocity (px/frame)", velocitySpinner);
        addRow(form, gbc, "Random Seed", seedSpinner);
        addRow(form, gbc, "Output Base Name", outputBaseField);

        JPanel outputPathRow = new JPanel(new BorderLayout(SPACE_XS, 0));
        outputPathRow.setOpaque(false);
        outputPathRow.add(outputDirectoryField, BorderLayout.CENTER);
        outputPathRow.add(browseButton, BorderLayout.EAST);
        addRow(form, gbc, "Output Directory", outputPathRow);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(null);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        card.add(formScroll, BorderLayout.CENTER);
        return card;
    }

    private void addRow(JPanel form, GridBagConstraints gbc, String labelText, JComponent component) {
        JLabel label = new JLabel(labelText);
        label.setFont(FONT_LABEL);
        label.setForeground(TEXT_PRIMARY);

        gbc.gridx = 0;
        gbc.weightx = 0.45;
        form.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.55;
        gbc.insets = new Insets(0, 0, SPACE_XS, 0);
        form.add(component, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, SPACE_XS, SPACE_M);
    }

    private void bindActions() {
        browseButton.addActionListener(e -> chooseOutputDirectory());
        generateButton.addActionListener(e -> generateSimulation());
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(outputDirectoryField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void generateSimulation() {
        CellSimulationParameters params = buildParameters();
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");

        SwingWorker<CellSimulationGenerator.SimulationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CellSimulationGenerator.SimulationResult doInBackground() throws Exception {
                return generator.generate(params, (currentFrame, totalFrames) -> {
                });
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                generateButton.setText("Generate Ground Truth");
                try {
                    CellSimulationGenerator.SimulationResult result = get();
                    JOptionPane.showMessageDialog(
                            CellSimulationGUI.this,
                            "Simulation complete.\n\n"
                                    + "Video: " + result.videoPath().toAbsolutePath() + "\n"
                                    + "Legacy CSV: " + result.legacyCsvPath().toAbsolutePath() + "\n"
                                    + "Events CSV: " + result.eventsCsvPath().toAbsolutePath() + "\n"
                                    + "Trajectory CSV: " + result.trajectoryCsvPath().toAbsolutePath() + "\n"
                                    + "Manifest JSON: " + result.manifestJsonPath().toAbsolutePath(),
                            "Simulation Generated",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
                    JOptionPane.showMessageDialog(
                            CellSimulationGUI.this,
                            "Simulation failed: " + message,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private CellSimulationParameters buildParameters() {
        return new CellSimulationParameters(
                ((Number) widthSpinner.getValue()).intValue(),
                ((Number) heightSpinner.getValue()).intValue(),
                ((Number) cellCountSpinner.getValue()).intValue(),
                ((Number) delaySpinner.getValue()).intValue(),
                ((Number) fpsSpinner.getValue()).intValue(),
                ((Number) lengthSpinner.getValue()).intValue(),
                ((Number) cellRadiusSpinner.getValue()).intValue(),
                ((Number) velocitySpinner.getValue()).doubleValue(),
                ((Number) seedSpinner.getValue()).longValue(),
                outputBaseField.getText().trim(),
                Path.of(outputDirectoryField.getText().trim())).normalized();
    }

    private void styleInput(JSpinner spinner) {
        spinner.setFont(FONT_BODY);
        spinner.setBorder(new RoundedBorder(new Color(99, 147, 224, 130), 12));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField textField = defaultEditor.getTextField();
            textField.setFont(FONT_BODY);
            textField.setForeground(Color.BLACK);
            textField.setBackground(Color.WHITE);
            textField.setBorder(new EmptyBorder(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS));
        }
    }

    private void styleTextField(JTextField field) {
        field.setFont(FONT_BODY);
        field.setForeground(Color.BLACK);
        field.setBackground(Color.WHITE);
        field.setBorder(new RoundedBorder(new Color(99, 147, 224, 130), 12));
    }

    private void styleButton(AbstractButton button, boolean primary) {
        button.setFont(FONT_LABEL);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new RoundedBorder(new Color(0, 0, 0, 0), 12));
        button.setMargin(new Insets(SPACE_XS, SPACE_S, SPACE_XS, SPACE_S));
        if (primary) {
            button.setBackground(PRIMARY_ACTION);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(new Color(17, 42, 80, 228));
            button.setForeground(new Color(214, 231, 255));
        }
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        SwingUtilities.invokeLater(() -> new CellSimulationGUI().setVisible(true));
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

            g2.setColor(new Color(62, 127, 255, 48));
            g2.fill(new Ellipse2D.Double(-120, -90, 480, 300));
            g2.setColor(new Color(22, 64, 166, 44));
            g2.fill(new Ellipse2D.Double(getWidth() - 300, getHeight() - 240, 420, 320));
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
            GradientPaint gp = new GradientPaint(0, 0, CARD_TOP, 0, getHeight(), CARD_BOTTOM);
            g2.setPaint(gp);
            g2.fill(shape);

            g2.setColor(BORDER_SOFT);
            g2.draw(shape);
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
            return new Insets(SPACE_XXS, SPACE_XS, SPACE_XXS, SPACE_XS);
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
}
