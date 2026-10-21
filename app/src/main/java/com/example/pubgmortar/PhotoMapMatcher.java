package com.example.pubgmortar;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

/** SIFT + validated perspective transform, with affine reference views for tilted photos. */
public final class PhotoMapMatcher {
    private PhotoMapMatcher() {}
    public static final class Result {
        public final Mat shotToMap;
        public final int inliers, candidates;
        Result(Mat h,int n,int c) { shotToMap=h; inliers=n; candidates=c; }
        public Point mapPoint(Point p) { return MapFeatureMatcher.project(shotToMap,p); }
    }
    static MapFeatureMatcher.Features prepareShot(Bitmap shot) {
        Mat gray=gray(shot);
        try { return MapFeatureMatcher.prepare(gray,false); } finally { gray.release(); }
    }
    static Result match(MapFeatureMatcher.Features shot, Bitmap reference, boolean tilted) {
        Mat gray=gray(reference);
        try {
            MapFeatureMatcher.Match result=MapFeatureMatcher.match(shot,gray,tilted);
            return result==null?null:new Result(result.homography,result.inliers,result.candidates);
        } finally { gray.release(); }
    }
    private static Mat gray(Bitmap bitmap) {
        Mat rgba=new Mat(), gray=new Mat();
        try { Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY); return gray;
        } catch(RuntimeException e) { gray.release(); throw e;
        } finally { rgba.release(); }
    }
    public static Result match(Bitmap shot, Bitmap reference) {
        if(shot==null || reference==null)return null;
        try(MapFeatureMatcher.Features features=prepareShot(shot)) {
            Result result=match(features,reference,false);
            return result!=null?result:match(features,reference,true);
        }
    }
}
