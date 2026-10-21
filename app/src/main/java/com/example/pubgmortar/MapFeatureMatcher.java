package com.example.pubgmortar;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** Native-only matching core, shared by Android and the desktop regression runner. */
public final class MapFeatureMatcher {
    private MapFeatureMatcher() {}
    public static final class Features implements AutoCloseable {
        final Mat descriptors = new Mat();
        Point[] points;
        final int width, height;
        Features(int w, int h) { width=w; height=h; }
        public void close() { descriptors.release(); }
    }
    public static final class Match implements AutoCloseable {
        public final Mat homography;
        public final int inliers, candidates;
        Match(Mat h, int n, int c) { homography=h; inliers=n; candidates=c; }
        public void close() { homography.release(); }
    }
    public static Features prepare(Mat gray, boolean reference) {
        return prepare(gray, reference, 1.0, 0);
    }
    private static Features prepare(Mat gray, boolean reference, double tilt, double angle) {
        Features out=new Features(gray.cols(),gray.rows());
        double scale=reference ? 2.0 : Math.min(1.0,2000.0/Math.max(gray.cols(),gray.rows()));
        double rad=Math.toRadians(angle), c=Math.cos(rad), s=Math.sin(rad);
        // Rotate and compress a reference view to simulate an obliquely photographed monitor.
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(double x:new double[]{0,gray.cols()}) for(double y:new double[]{0,gray.rows()}) {
            double xx=scale*(c*x-s*y), yy=scale*(s*x+c*y)/tilt;
            minX=Math.min(minX,xx); maxX=Math.max(maxX,xx);
            minY=Math.min(minY,yy); maxY=Math.max(maxY,yy);
        }
        Mat transform=Mat.eye(3,3,CvType.CV_64F), view=new Mat(), valid=new Mat();
        Mat sourceMask=new Mat(gray.size(),CvType.CV_8U,new Scalar(255));
        Mat inverse=null; MatOfKeyPoint keys=new MatOfKeyPoint();
        CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
        SIFT sift=SIFT.create(6500,3,0.012,12,1.6);
        try {
            transform.put(0,0,scale*c,-scale*s,-minX, scale*s/tilt,scale*c/tilt,-minY, 0,0,1);
            Size size=new Size(Math.ceil(maxX-minX),Math.ceil(maxY-minY));
            Imgproc.warpPerspective(gray,view,transform,size,Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0));
            Imgproc.warpPerspective(sourceMask,valid,transform,size,Imgproc.INTER_NEAREST,Core.BORDER_CONSTANT,new Scalar(0));
            clahe.apply(view,view);
            sift.detectAndCompute(view,valid,keys,out.descriptors);
            inverse=transform.inv();
            KeyPoint[] detected=keys.toArray(); out.points=new Point[detected.length];
            for(int i=0;i<detected.length;i++) out.points[i]=project(inverse,detected[i].pt);
            return out;
        } catch(RuntimeException e) { out.close(); throw e;
        } finally {
            transform.release(); view.release(); valid.release(); sourceMask.release();
            keys.release(); if(inverse!=null)inverse.release();
            sift.clear(); clahe.collectGarbage(); clahe.clear();
        }
    }
    public static Match match(Features shot, Mat reference, boolean tiltedFallback) {
        Match best=null;
        // The ordinary pass is fast; affine views are evaluated only if no map was identified.
        double[] angles=tiltedFallback ? new double[]{0,45,90,135} : new double[]{0};
        for(double angle:angles) {
            try(Features ref=prepare(reference,true,tiltedFallback?1.8:1.0,angle)) {
                Match next=estimate(shot,ref);
                if(next!=null) {
                    if(best==null || next.inliers>best.inliers) {
                        if(best!=null)best.close(); best=next;
                    } else next.close();
                }
            } catch(RuntimeException e) { if(best!=null)best.close(); throw e; }
        }
        return best;
    }
    private static Match estimate(Features shot, Features ref) {
        if(shot.descriptors.rows()<2 || ref.descriptors.rows()<2)return null;
        List<MatOfDMatch> groups=new ArrayList<>();
        DescriptorMatcher matcher=DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        List<DMatch> accepted=new ArrayList<>();
        try {
            matcher.knnMatch(shot.descriptors,ref.descriptors,groups,2);
            for(MatOfDMatch group:groups) {
                DMatch[] pair=group.toArray();
                if(pair.length==2 && pair[0].distance<0.75*pair[1].distance)accepted.add(pair[0]);
            }
        } finally { for(MatOfDMatch group:groups)group.release(); matcher.clear(); }
        // Several photo features must not all vote for the same reference feature.
        accepted.sort(Comparator.comparingDouble(m->m.distance));
        Set<Integer> used=new HashSet<>(); List<Point> from=new ArrayList<>(),to=new ArrayList<>();
        for(DMatch m:accepted)if(used.add(m.trainIdx)) { from.add(shot.points[m.queryIdx]); to.add(ref.points[m.trainIdx]); }
        if(from.size()<16)return null;
        MatOfPoint2f src=new MatOfPoint2f(),dst=new MatOfPoint2f(); Mat mask=new Mat(),h=null;
        try {
            src.fromList(from); dst.fromList(to);
            h=Calib3d.findHomography(src,dst,Calib3d.RANSAC,2.5,mask,5000,0.995);
            if(h.empty() || mask.empty())return null;
            int n=Core.countNonZero(mask);
            if(n<16 || n<from.size()*0.35 || !validProjection(h,shot.width,shot.height))return null;
            Mat inverse=h.inv();
            List<Point> inFrom=new ArrayList<>(),inTo=new ArrayList<>();
            try {
                for(int i=0;i<from.size();i++)if(mask.get(i,0)[0]!=0) {
                    Point back=project(inverse,to.get(i));
                    // Symmetric error rejects tiny reference clusters that fit arbitrary photos.
                    if(distance(back,from.get(i))<=10) { inFrom.add(from.get(i)); inTo.add(to.get(i)); }
                }
            } finally { inverse.release(); }
            if(inFrom.size()<16 || inFrom.size()<from.size()*0.35 ||
                !spread(inFrom,shot.width*shot.height*0.001) || !spread(inTo,16))return null;
            Match result=new Match(h,inFrom.size(),from.size()); h=null; return result;
        } finally { src.release(); dst.release(); mask.release(); if(h!=null)h.release(); }
    }
    private static boolean spread(List<Point> points,double minimumArea) {
        MatOfPoint2f p=convexHull(points);
        try { return Imgproc.contourArea(p)>=minimumArea;
        } finally { p.release(); }
    }
    private static MatOfPoint2f convexHull(List<Point> points) {
        // Monotonic-chain convex hull in reference/photo coordinates.
        List<Point> sorted=new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble((Point p)->p.x).thenComparingDouble(p->p.y));
        List<Point> hull=new ArrayList<>();
        for(Point p:sorted) { while(hull.size()>=2 && cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1); hull.add(p); }
        int lower=hull.size();
        for(int i=sorted.size()-2;i>=0;i--) { Point p=sorted.get(i); while(hull.size()>lower && cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1); hull.add(p); }
        if(hull.size()>1)hull.remove(hull.size()-1);
        MatOfPoint2f result=new MatOfPoint2f(); result.fromList(hull); return result;
    }
    private static double cross(Point a,Point b,Point c) { return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x); }
    private static boolean validProjection(Mat h,int width,int height) {
        Point[] corners={new Point(0,0),new Point(width,0),new Point(width,height),new Point(0,height)};
        double sign=0;
        for(int i=0;i<4;i++) {
            Point p=corners[i]; double z=h.get(2,0)[0]*p.x+h.get(2,1)[0]*p.y+h.get(2,2)[0];
            if(!Double.isFinite(z) || Math.abs(z)<1e-8 || (i>0 && z*sign<=0))return false;
            sign=z; corners[i]=project(h,p);
            if(!Double.isFinite(corners[i].x)||!Double.isFinite(corners[i].y))return false;
        }
        // A non-reflected convex footprint is required; no folds through the camera horizon.
        for(int i=0;i<4;i++)if(cross(corners[i],corners[(i+1)%4],corners[(i+2)%4])<=0)return false;
        return true;
    }
    public static Point project(Mat h,Point p) {
        double z=h.get(2,0)[0]*p.x+h.get(2,1)[0]*p.y+h.get(2,2)[0];
        if(Math.abs(z)<1e-8)return new Point(Double.NaN,Double.NaN);
        return new Point((h.get(0,0)[0]*p.x+h.get(0,1)[0]*p.y+h.get(0,2)[0])/z,
            (h.get(1,0)[0]*p.x+h.get(1,1)[0]*p.y+h.get(1,2)[0])/z);
    }
    private static double distance(Point a,Point b) { return Math.hypot(a.x-b.x,a.y-b.y); }
}
