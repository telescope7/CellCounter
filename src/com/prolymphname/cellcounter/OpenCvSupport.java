package com.prolymphname.cellcounter;

import org.opencv.core.Core;

public final class OpenCvSupport {
    private static volatile boolean loaded;

    private OpenCvSupport() {
    }

    public static synchronized void loadOpenCv() {
        if (loaded) {
            return;
        }

        UnsatisfiedLinkError systemLoadError = null;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError ex) {
            systemLoadError = ex;
        }

        try {
            loadViaOpenPnpIfPresent();
            loaded = true;
            return;
        } catch (RuntimeException ex) {
            IllegalStateException failure = new IllegalStateException(
                    "Unable to load OpenCV natives via java.library.path or bundled OpenPnP fallback.",
                    ex);
            if (systemLoadError != null) {
                failure.addSuppressed(systemLoadError);
            }
            throw failure;
        }
    }

    private static void loadViaOpenPnpIfPresent() {
        try {
            Class<?> openCvClass = Class.forName("nu.pattern.OpenCV");
            openCvClass.getMethod("loadLocally").invoke(null);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "OpenPnP OpenCV loader class not found (nu.pattern.OpenCV). "
                            + "Ensure Maven dependencies are synced in your IDE.",
                    ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke nu.pattern.OpenCV.loadLocally()", ex);
        }
    }
}
