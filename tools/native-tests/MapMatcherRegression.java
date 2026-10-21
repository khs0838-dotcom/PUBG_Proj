import com.example.pubgmortar.MapFeatureMatcher;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/** Runs the actual production Java matcher against reproducible synthetic camera views. */
public class MapMatcherRegression {
    public static void main(String[] args) {
        nu.pattern.OpenCV.loadLocally();
        Core.setNumThreads(2);
        Core.setRNGSeed(12345);
        Mat reference=Imgcodecs.imread(args[0],Imgcodecs.IMREAD_GRAYSCALE);
        Mat wrong=Imgcodecs.imread(args[1],Imgcodecs.IMREAD_GRAYSCALE);
        if(reference.empty()||wrong.empty())throw new AssertionError("Missing fixture maps");
        int passed=0, baselinePassed=0;
        try {
            double[][] cases={{1,0,0},{2,0.12,0},{4,0.20,0},{6,0.15,0},{3,0.42,0},{3,0.20,25}};
            for(double[] test:cases) {
                double zoom=test[0],tilt=test[1];
                int side=(int)(reference.cols()/zoom);
                int x=(reference.cols()-side)/2,y=(reference.rows()-side)/2;
                MatOfPoint2f src=new MatOfPoint2f(new Point(x,y),new Point(x+side,y),new Point(x+side,y+side),new Point(x,y+side));
                MatOfPoint2f dst=new MatOfPoint2f(new Point(100+tilt*650,100),new Point(1100,120+tilt*250),new Point(1100-tilt*200,1000),new Point(100,970-tilt*200));
                if(test[2]!=0) {
                    Point[] rotated=dst.toArray(); double angle=Math.toRadians(test[2]);
                    for(Point p:rotated) {
                        double xx=p.x-600,yy=p.y-550;
                        p.x=600+0.85*(Math.cos(angle)*xx-Math.sin(angle)*yy);
                        p.y=550+0.85*(Math.sin(angle)*xx+Math.cos(angle)*yy);
                    }
                    dst.fromArray(rotated);
                }
                Mat truth=Imgproc.getPerspectiveTransform(src,dst),shot=new Mat();
                // A dark camera surround, interpolated low-res crop and perspective distortion.
                Imgproc.warpPerspective(reference,shot,truth,new Size(1200,1100),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(20));
                Mat inverse=truth.inv();
                try(MapFeatureMatcher.Features features=MapFeatureMatcher.prepare(shot,false)) {
                    boolean legacy=LegacyOrb.recognizes(shot,reference);
                    if(legacy)baselinePassed++;
                    long start=System.nanoTime();
                    MapFeatureMatcher.Match result=MapFeatureMatcher.match(features,reference,false);
                    boolean fallback=result==null;
                    // Exercise affine-view fallback explicitly in the strongest perspective case.
                    if(tilt>0.4 && result!=null) { result.close(); result=null; fallback=true; }
                    if(fallback)result=MapFeatureMatcher.match(features,reference,true);
                    if(result==null)throw new AssertionError("No match: zoom="+zoom+", tilt="+tilt);
                    try {
                        double maxError=0;
                        // Validate localization at all four visible map corners, not just feature count.
                        for(Point p:dst.toArray()) {
                            Point actual=MapFeatureMatcher.project(result.homography,p),expected=MapFeatureMatcher.project(inverse,p);
                            maxError=Math.max(maxError,Math.hypot(actual.x-expected.x,actual.y-expected.y));
                        }
                        if(maxError>3)throw new AssertionError("Bad localization: "+maxError);
                        MapFeatureMatcher.Match impostor=MapFeatureMatcher.match(features,wrong,false);
                        if(impostor!=null) { impostor.close(); throw new AssertionError("Wrong map matched"); }
                        System.out.printf("PASS zoom=%.0fx tilt=%.2f rotation=%.0fdeg inliers=%d maxError=%.2fpx fallback=%s legacyORB=%s time=%.1fs%n",zoom,tilt,test[2],result.inliers,maxError,fallback,legacy,(System.nanoTime()-start)/1e9);
                        passed++;
                    } finally { result.close(); }
                } finally { src.release();dst.release();truth.release();inverse.release();shot.release(); }
            }
            Mat blank=new Mat(900,1200,CvType.CV_8U,new Scalar(100));
            try(MapFeatureMatcher.Features features=MapFeatureMatcher.prepare(blank,false)) {
                MapFeatureMatcher.Match result=MapFeatureMatcher.match(features,reference,false);
                if(result!=null) { result.close();throw new AssertionError("Blank image matched"); }
                System.out.println("PASS blank image rejected");passed++;
            } finally { blank.release(); }
            System.out.println("Legacy ORB accepted "+baselinePassed+" / "+cases.length+" positive cases");
            System.out.println("PASSED "+passed+" native regression cases (synthetic, not real camera accuracy)");
        } finally { reference.release();wrong.release(); }
    }
}
