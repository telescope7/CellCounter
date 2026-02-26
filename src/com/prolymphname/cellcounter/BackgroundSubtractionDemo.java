package com.prolymphname.cellcounter;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.highgui.HighGui;

import java.util.List;

class BackgroundSubtraction {
    public void run(String[] args) {
        String input = "/Users/mthomas/test.m4v";  // change to actual video path
        boolean useMOG2 = true;

        BackgroundSubtractor backSub = useMOG2 ?
                Video.createBackgroundSubtractorMOG2(100, 50, false) :
                Video.createBackgroundSubtractorKNN();

        VideoCapture capture = new VideoCapture(input);
        if (!capture.isOpened()) {
            System.err.println("Unable to open: " + input);
            System.exit(0);
        }

        HighGui.namedWindow("Enhanced Detection", HighGui.WINDOW_NORMAL);

        Mat frame = new Mat();
        Mat prevGray = new Mat();
        Mat gray = new Mat();
        Mat claheOut = new Mat();
        Mat highPass = new Mat();
        Mat fgMask = new Mat();
        Mat diff = new Mat();
        Mat motion = new Mat();
        Mat morph = new Mat();
        Mat fgMaskColor = new Mat();
        Mat combined = new Mat();

        while (capture.read(frame)) {
            if (frame.empty()) break;

            // Convert to grayscale
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            // CLAHE contrast enhancement
            Imgproc.createCLAHE(2.0, new Size(8, 8)).apply(gray, claheOut);

            // High-pass filtering
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(claheOut, blurred, new Size(0, 0), 3);
            Core.addWeighted(claheOut, 1.5, blurred, -0.5, 0, highPass);

            // Frame differencing
            if (!prevGray.empty()) {
                Core.absdiff(highPass, prevGray, diff);
                Imgproc.threshold(diff, motion, 25, 255, Imgproc.THRESH_BINARY);
            }
            highPass.copyTo(prevGray);

            // Background subtraction
            backSub.apply(frame, fgMask);


            // Morphological filtering
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.morphologyEx(fgMask, morph, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(morph, morph, Imgproc.MORPH_CLOSE, kernel);

            // Convert mask to 3-channel image
            Imgproc.cvtColor(morph, fgMaskColor, Imgproc.COLOR_GRAY2BGR);

            // Annotate original frame
            Imgproc.rectangle(frame, new Point(10, 2), new Point(100, 20), new Scalar(255, 255, 255), -1);
            String frameNumber = String.format("%d", (int) capture.get(Videoio.CAP_PROP_POS_FRAMES));
            Imgproc.putText(frame, frameNumber, new Point(15, 15), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0));

            // Combine original and processed mask
            Core.hconcat(List.of(frame, fgMaskColor), combined);
            HighGui.imshow("Enhanced Detection", combined);

            int key = HighGui.waitKey(30);
            if (key == 'q' || key == 27) break;
        }

        capture.release();
        HighGui.destroyAllWindows();
    }
}

public class BackgroundSubtractionDemo {
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        new BackgroundSubtraction().run(args);
    }
}
