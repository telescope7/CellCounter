package com.prolymphname.cellcounter.analysis;

import com.prolymphname.cellcounter.trackingadapter.TrackingConfiguration;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG2;

import java.util.ArrayList;
import java.util.List;

public class ForegroundDetectionPipeline {
    public DetectionFrameResult detect(
            Mat frameInput,
            Mat referenceFrame,
            BackgroundSubtractorMOG2 subtractor,
            TrackingConfiguration cfg) {
        Mat fgmask = new Mat();
        Mat sourceForBackgroundSubtraction = frameInput;

        if (referenceFrame != null && !referenceFrame.empty()) {
            Mat diff = new Mat();
            Core.absdiff(frameInput, referenceFrame, diff);
            sourceForBackgroundSubtraction = diff;
        }

        try {
            subtractor.apply(sourceForBackgroundSubtraction, fgmask);
        } finally {
            if (sourceForBackgroundSubtraction != frameInput) {
                sourceForBackgroundSubtraction.release();
            }
        }

        int kernelSize = cfg.getMorphologyKernelSize();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(kernelSize, kernelSize));
        try {
            Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_OPEN, kernel, new Point(-1, -1),
                    cfg.getMorphologyOpenIterations());
            Imgproc.morphologyEx(fgmask, fgmask, Imgproc.MORPH_DILATE, kernel, new Point(-1, -1),
                    cfg.getMorphologyDilateIterations());
        } finally {
            kernel.release();
        }

        Mat contourMask = fgmask.clone();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        try {
            Imgproc.findContours(contourMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        } finally {
            contourMask.release();
            hierarchy.release();
        }

        Core.normalize(fgmask, fgmask, 0, 255, Core.NORM_MINMAX);
        Imgproc.threshold(fgmask, fgmask, cfg.getNormalizedMaskThreshold(), 255, Imgproc.THRESH_BINARY);

        List<Rect> rects = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            try {
                if (Imgproc.contourArea(contour) < cfg.getMinContourArea()) {
                    continue;
                }

                Rect rect = Imgproc.boundingRect(contour);
                double circumference = 2.0 * (rect.width + rect.height);
                if (circumference <= cfg.getMaxRectCircumference()) {
                    rects.add(rect);
                }
            } finally {
                contour.release();
            }
        }
        contours.clear();

        return new DetectionFrameResult(fgmask, rects);
    }
}
