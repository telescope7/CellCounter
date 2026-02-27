package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import org.opencv.core.Core;

import javax.swing.*;
import java.awt.*;

public final class CellCounterApp {
    private CellCounterApp() {
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        configureLookAndFeel();

        if (args.length == 2 || args.length == 5) {
            runHeadless(args);
            return;
        }

        SwingUtilities.invokeLater(CellCounterApp::showSplashAndLaunchGui);
    }

    private static void runHeadless(String[] args) {
        String inputVideo = args[0];
        String outputPrefix = args[1];
        String cellType = args.length >= 3 ? args[2] : "";
        String substrate = args.length >= 4 ? args[3] : "";
        String flow = args.length >= 5 ? args[4] : "";

        HeadlessProcessor processor = new HeadlessProcessor(new CellCounterApplicationService());
        processor.run(inputVideo, outputPrefix, cellType, substrate, flow);
    }

    private static void configureLookAndFeel() {
        try {
            boolean nimbusFound = false;
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    nimbusFound = true;
                    break;
                }
            }
            if (!nimbusFound) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException e) {
            System.err.println("Failed to set Look and Feel: " + e.getMessage());
        }
    }

    private static void showSplashAndLaunchGui() {
        JWindow splash = new JWindow();
        JPanel splashPanel = new JPanel(new BorderLayout());
        splashPanel.setBackground(new Color(230, 230, 250));
        splashPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 128), 2));
        JLabel splashLabel = new JLabel("<html><center>Cell Counter<br/><i>Infiltrate Bio</i></center></html>",
                SwingConstants.CENTER);
        splashLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        splashLabel.setForeground(new Color(0, 0, 128));
        splashPanel.add(splashLabel, BorderLayout.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        splashPanel.add(progressBar, BorderLayout.SOUTH);
        splash.getContentPane().add(splashPanel);
        splash.setSize(400, 150);
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        new Timer(2000, ae -> {
            splash.setVisible(false);
            splash.dispose();
            new CellCounterGUI(new CellCounterApplicationService()).setVisible(true);
        }) {
            private static final long serialVersionUID = -6312010934618202078L;

            {
                setRepeats(false);
            }
        }.start();
    }
}
