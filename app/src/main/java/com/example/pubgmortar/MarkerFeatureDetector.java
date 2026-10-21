package com.example.pubgmortar;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Conservative marker candidate detector for a photographed PUBG tactical map.
 * Markers vary with team, colorblind settings and game patch. This is a heuristic,
 * NOT a trained marker classifier. It declines to produce a result if unsure.
 */
public final class MarkerFeatureDetector {
    private MarkerFeatureDetector() {}
    private static final class Candidate {
        final Point p; final double score,diameter;
        Candidate(Point p,double score,double diameter) { this.p=p; this.score=score;this.diameter=diameter; }
    }
    public static final class Batch {
        public final Point playerPhoto;
        public final List<Point> targetsPhoto;
        public final String warning;
        Batch(Point player,List<Point> targets,String warning) {
            playerPhoto=player; targetsPhoto=java.util.Collections.unmodifiableList(targets);
            this.warning=warning;
        }
    }
    public static Batch find(Mat photo, Mat reference, Mat shotToMap, double metersPerMapPixel) {
        Mat image=new Mat(), base=new Mat(), aligned=new Mat(), inv=new Mat();
        Mat diff=new Mat(), diffGray=new Mat(), channel=new Mat(), hsv=new Mat();
        Mat white=new Mat(), colored=new Mat(), changed=new Mat(), valid=new Mat();
        Mat validWarp=new Mat(), kernel=new Mat();
        try {
            photo.copyTo(image);
            reference.copyTo(base);
            inv=shotToMap.inv();
            Imgproc.warpPerspective(base,aligned,inv,image.size(),Imgproc.INTER_LINEAR,
                Core.BORDER_CONSTANT,new Scalar(0,0,0,255));
            Imgproc.cvtColor(image,hsv,Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(hsv,hsv,Imgproc.COLOR_RGB2HSV);
            Core.absdiff(image,aligned,diff);
            // Maximum RGB difference preserves blue/red team pins that grayscale suppresses.
            Core.extractChannel(diff,diffGray,0);
            for(int c=1;c<3;c++) { Core.extractChannel(diff,channel,c);Core.max(diffGray,channel,diffGray); }
            Imgproc.threshold(diffGray,changed,65,255,Imgproc.THRESH_BINARY);
            Mat all=new Mat(base.rows(),base.cols(),org.opencv.core.CvType.CV_8UC1,new Scalar(255));
            Imgproc.warpPerspective(all,validWarp,inv,image.size(),Imgproc.INTER_NEAREST,
                Core.BORDER_CONSTANT,new Scalar(0));
            all.release();
            // avoid thin boundary caused by warp interpolation
            kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5));
            Imgproc.erode(validWarp,valid,kernel);
            Core.bitwise_and(changed,valid,changed);
            Mat colors=new Mat(), whites=new Mat();
            try {
                // Saturated brightly-colored waypoints / colored marker shapes.
                Core.inRange(hsv,new Scalar(0,95,130),new Scalar(179,255,255),colors);
                Core.inRange(hsv,new Scalar(0,0,185),new Scalar(179,72,255),whites);
                Core.bitwise_and(changed,colors,colored);
                Core.bitwise_and(changed,whites,white);
            } finally { colors.release(); whites.release(); }
            // Remove single-pixel noise; do not close blobs into large painted areas.
            Imgproc.morphologyEx(white,white,Imgproc.MORPH_OPEN,kernel);
            Imgproc.morphologyEx(colored,colored,Imgproc.MORPH_OPEN,kernel);
            List<Candidate> player=contours(white,true);
            List<Candidate> target=contours(colored,false);
            // A shared firing position is selected independently of the number of target pins.
            // Similar target scores are expected for team pins, and are NOT ambiguity of the origin.
            Candidate origin=null; List<Candidate> selected=new ArrayList<>();
            double runner=-1;
            for(Candidate p:player) {
                Point mapP=MapFeatureMatcher.project(shotToMap,p.p);
                if(!inside(mapP,reference))continue;
                List<Candidate> eligible=new ArrayList<>();
                for(Candidate t:target) {
                    Point mapT=MapFeatureMatcher.project(shotToMap,t.p);
                    if(!inside(mapT,reference) || t.score<28)continue;
                    double range=Math.hypot(mapP.x-mapT.x,mapP.y-mapT.y)*metersPerMapPixel;
                    if(!Double.isFinite(range)||range<=0 || separation(p,t)<1)continue;
                    boolean duplicate=false;
                    for(Candidate kept:eligible)if(separation(t,kept)<1) { duplicate=true;break; }
                    if(!duplicate)eligible.add(t);
                }
                if(eligible.isEmpty())continue;
                if(origin==null) { origin=p;selected=eligible; }
                else if(separation(p,origin)>=1)runner=Math.max(runner,p.score);
            }
            // Do not choose a different teammate/player icon just to produce four results.
            if(origin==null || origin.score<28 || (runner>0 && origin.score<runner*1.12))return null;
            boolean truncated=selected.size()>4;
            selected=new ArrayList<>(selected.subList(0,Math.min(4,selected.size())));
            // Number by photo position so labels stay stable when confidence scores tie.
            selected.sort(Comparator.comparingDouble((Candidate c)->c.p.y).thenComparingDouble(c->c.p.x));
            List<Point> points=new ArrayList<>();for(Candidate c:selected)points.add(c.p);
            return new Batch(origin.p,points,"목표 번호는 사진 위치 순서이며 팀원 번호가 아닙니다. " +
                (truncated?"후보가 4개를 넘어 신뢰도가 높은 4개만 표시했습니다. ":"") +
                "자동 마커 탐지는 시험 기능입니다. 촬영된 사진의 핑과 결과를 비교하세요.");
        } finally {
            image.release(); base.release(); aligned.release(); inv.release();
            diff.release(); diffGray.release(); channel.release(); hsv.release();
            white.release(); colored.release(); changed.release();valid.release();validWarp.release();kernel.release();
        }
    }
    private static double separation(Candidate a,Candidate b) {
        return Math.hypot(a.p.x-b.p.x,a.p.y-b.p.y)/Math.max(8,(a.diameter+b.diameter)*0.6);
    }
    private static boolean inside(Point p, Mat map) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && p.x>=0 && p.y>=0 &&
            p.x<map.cols() && p.y<map.rows();
    }
    private static List<Candidate> contours(Mat mask,boolean white) {
        List<MatOfPoint> shapes=new ArrayList<>(); Mat hierarchy=new Mat();
        Imgproc.findContours(mask,shapes,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();
        List<Candidate> found=new ArrayList<>();
        for(MatOfPoint shape:shapes) {
            double area=Imgproc.contourArea(shape);
            Rect r=Imgproc.boundingRect(shape);
            double diameter=Math.max(r.width,r.height);
            if(area<13 || area>1650 || diameter<5 || diameter>64 ||
                Math.min(r.width,r.height)<4 ||
                (double)Math.max(r.width,r.height)/Math.min(r.width,r.height)>2.5) {
                shape.release();continue;
            }
            MatOfPoint2f curve=new MatOfPoint2f(shape.toArray());
            MatOfPoint2f poly=new MatOfPoint2f();
            double perimeter=Imgproc.arcLength(curve,true);
            Imgproc.approxPolyDP(curve,poly,0.09*perimeter,true);
            int vertices=poly.rows();
            curve.release();poly.release();
            if(white && (vertices<3 || vertices>7)) {shape.release();continue;}
            if(!white && vertices<3) {shape.release();continue;}
            // White triangles are a player-icon candidate; colored compact shapes a ping candidate.
            double compactness=4*Math.PI*area/(perimeter*perimeter+1);
            double penalty=Math.abs(diameter-(white?16.0:20.0))*.2;
            double score=(white?(vertices<=4?22:12):20)+Math.min(area*.055,15)
                +Math.min(10,compactness*11)-penalty;
            found.add(new Candidate(new Point(r.x+r.width*.5,r.y+r.height*.5),score,diameter));
            shape.release();
        }
        found.sort(Comparator.comparingDouble((Candidate a)->a.score).reversed());
        return found;
    }
}
