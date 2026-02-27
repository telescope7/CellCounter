package com.prolymphname.cellcounter;

import com.prolymphname.cellcounter.application.CellCounterApplicationService;
import org.opencv.core.Core;

import javax.swing.*;

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

        SwingUtilities.invokeLater(() -> new CellCounterGUI(new CellCounterApplicationService()).setVisible(true));
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

}
