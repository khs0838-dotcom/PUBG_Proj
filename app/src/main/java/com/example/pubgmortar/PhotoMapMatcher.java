package com.example.pubgmortar;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

/** OpenCV ORB + RANSAC. A homography is valid only if enough inliers survive. */
public final class PhotoMapMatcher {
    private PhotoMapMatcher() {}
    public static final class Result {
        public final Mat shotToMap;
        public final int inliers, candidates;
        Result(Mat h, int inliers, int candidates) {
            this.shotToMap = h; this.inliers = inliers; this.candidates = candidates;
        }
        public Point mapPoint(Point p) {
            double a = shotToMap.get(0,0)[0], b = shotToMap.get(0,1)[0], c = shotToMap.get(0,2)[0];
            double d = shotToMap.get(1,0)[0], e = shotToMap.get(1,1)[0], f = shotToMap.get(1,2)[0];
            double g = shotToMap.get(2,0)[0], h = shotToMap.get(2,1)[0], i = shotToMap.get(2,2)[0];
            double z = g*p.x + h*p.y + i;
            if (Math.abs(z) < 1e-8) return new Point(Double.NaN, Double.NaN);
            return new Point((a*p.x+b*p.y+c)/z, (d*p.x+e*p.y+f)/z);
        }
    }

    public static Result match(Bitmap shot, Bitmap reference) {
        if (shot == null || reference == null) return null;
        // Feature detection is expensive on full-resolution camera photos.
        double ps = Math.min(1.0, 1500.0 / Math.max(shot.getWidth(), shot.getHeight()));
        double rs = Math.min(1.0, 1500.0 / Math.max(reference.getWidth(), reference.getHeight()));
        Bitmap sb = Bitmap.createScaledBitmap(shot, (int)(shot.getWidth()*ps), (int)(shot.getHeight()*ps), true);
        Bitmap rb = Bitmap.createScaledBitmap(reference, (int)(reference.getWidth()*rs), (int)(reference.getHeight()*rs), true);
        Mat sRgba = new Mat(), rRgba = new Mat(), sGray = new Mat(), rGray = new Mat();
        MatOfKeyPoint sKeys = new MatOfKeyPoint(), rKeys = new MatOfKeyPoint();
        Mat sDesc = new Mat(), rDesc = new Mat();
        try {
            Utils.bitmapToMat(sb, sRgba); Utils.bitmapToMat(rb, rRgba);
            Imgproc.cvtColor(sRgba, sGray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.cvtColor(rRgba, rGray, Imgproc.COLOR_RGBA2GRAY);
            // CLAHE improves matching on photos of washed-out monitors.
            Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8,8)).apply(sGray, sGray);
            Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8,8)).apply(rGray, rGray);
            ORB orb = ORB.create(5000);
            orb.detectAndCompute(sGray, new Mat(), sKeys, sDesc);
            orb.detectAndCompute(rGray, new Mat(), rKeys, rDesc);
            if (sDesc.empty() || rDesc.empty()) return null;
            List<MatOfDMatch> knn = new ArrayList<>();
            DescriptorMatcher dm = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
            dm.knnMatch(sDesc, rDesc, knn, 2);
            KeyPoint[] kpS = sKeys.toArray(), kpR = rKeys.toArray();
            List<Point> from = new ArrayList<>(), to = new ArrayList<>();
            for (MatOfDMatch group : knn) {
                org.opencv.core.DMatch[] m = group.toArray();
                if (m.length == 2 && m[0].distance < 0.74f*m[1].distance) {
                    from.add(new Point(kpS[m[0].queryIdx].pt.x / ps, kpS[m[0].queryIdx].pt.y / ps));
                    to.add(new Point(kpR[m[0].trainIdx].pt.x / rs, kpR[m[0].trainIdx].pt.y / rs));
                }
                group.release();
            }
            if (from.size() < 18) return null;
            MatOfPoint2f src = new MatOfPoint2f(), dst = new MatOfPoint2f();
            src.fromList(from); dst.fromList(to);
            Mat mask = new Mat();
            Mat h = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 9.0, mask);
            int inliers = Core.countNonZero(mask);
            src.release(); dst.release(); mask.release();
            if (h.empty() || inliers < 15 || inliers < Math.max(15, (int)(from.size()*0.28))) {
                h.release(); return null;
            }
            return new Result(h, inliers, from.size());
        } finally {
            sRgba.release(); rRgba.release(); sGray.release(); rGray.release();
            sKeys.release(); rKeys.release(); sDesc.release(); rDesc.release();
            if (sb != shot) sb.recycle(); if (rb != reference) rb.recycle();
        }
    }
}
