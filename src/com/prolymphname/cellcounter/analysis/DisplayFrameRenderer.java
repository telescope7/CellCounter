package com.prolymphname.cellcounter.analysis;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public class DisplayFrameRenderer {
    private static final Scalar NEW_TRACK_COLOR = new Scalar(0, 0, 255);
    private static final Scalar EXISTING_TRACK_COLOR = new Scalar(0, 255, 0);
    private static final Scalar PREVIEW_COLOR = new Scalar(58, 233, 197);

    public Mat renderTrackedFrame(Mat sourceFrame, boolean showMaskView, Mat maskForDisplay, List<TrackedOverlay> overlays) {
        Mat displayOutput;
        if (showMaskView) {
            displayOutput = new Mat();
            Imgproc.cvtColor(maskForDisplay, displayOutput, Imgproc.COLOR_GRAY2BGR);
        } else {
            displayOutput = sourceFrame.clone();
        }

        for (TrackedOverlay overlay : overlays) {
            Scalar color = overlay.newTrack() ? NEW_TRACK_COLOR : EXISTING_TRACK_COLOR;
            Rect bbox = overlay.bbox();
            Point centroid = overlay.centroid();

            if (bbox != null) {
                Imgproc.rectangle(displayOutput, bbox.tl(), bbox.br(), color, 1);
            }
            if (centroid != null) {
                Imgproc.putText(displayOutput, "ID " + overlay.cellId(),
                        new Point(centroid.x - 10, centroid.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.5,
                        color,
                        1);
                Imgproc.circle(displayOutput, centroid, 4, color, 1);
            }
        }
        return displayOutput;
    }

    public Mat renderPreviewFrame(Mat sourceFrame, Mat maskForDisplay, List<Rect> rects, boolean showMaskView) {
        Mat display;
        if (showMaskView) {
            display = new Mat();
            Imgproc.cvtColor(maskForDisplay, display, Imgproc.COLOR_GRAY2BGR);
        } else {
            display = sourceFrame.clone();
        }

        for (Rect rect : rects) {
            Imgproc.rectangle(display, rect.tl(), rect.br(), PREVIEW_COLOR, 1);
            Point centroid = new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
            Imgproc.circle(display, centroid, 2, PREVIEW_COLOR, 1);
        }

        Imgproc.putText(display, "Preview detections: " + rects.size(),
                new Point(12, 24),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.6,
                PREVIEW_COLOR,
                2);
        return display;
    }
}
