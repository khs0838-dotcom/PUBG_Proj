import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.calib3d.Calib3d;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** Previous ORB settings, retained only as the regression comparison baseline. */
final class LegacyOrb {
    static boolean recognizes(Mat shot,Mat reference) {
        Mat s=new Mat(),r=new Mat(),sd=new Mat(),rd=new Mat(),mask=new Mat(),h=null,empty=new Mat();
        MatOfKeyPoint sk=new MatOfKeyPoint(),rk=new MatOfKeyPoint();
        MatOfPoint2f src=new MatOfPoint2f(),dst=new MatOfPoint2f();
        List<MatOfDMatch> groups=new ArrayList<>();
        ORB orb=ORB.create(5000); DescriptorMatcher dm=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(2,new Size(8,8));
        double ps=Math.min(1,1500.0/Math.max(shot.cols(),shot.rows()));
        try {
            Imgproc.resize(shot,s,new Size((int)(shot.cols()*ps),(int)(shot.rows()*ps)));
            reference.copyTo(r);clahe.apply(s,s);clahe.apply(r,r);
            orb.detectAndCompute(s,empty,sk,sd);orb.detectAndCompute(r,empty,rk,rd);
            if(sd.empty()||rd.rows()<2)return false;
            dm.knnMatch(sd,rd,groups,2);KeyPoint[] a=sk.toArray(),b=rk.toArray();
            List<Point> from=new ArrayList<>(),to=new ArrayList<>();
            for(MatOfDMatch group:groups) {DMatch[] m=group.toArray();if(m.length==2&&m[0].distance<.74*m[1].distance) {
                Point p=a[m[0].queryIdx].pt;from.add(new Point(p.x/ps,p.y/ps));to.add(b[m[0].trainIdx].pt);
            }}
            if(from.size()<18)return false;
            src.fromList(from);dst.fromList(to);h=Calib3d.findHomography(src,dst,Calib3d.RANSAC,9,mask);
            if(h.empty()||mask.empty())return false;
            int n=Core.countNonZero(mask);return n>=25 && n>=from.size()*.28;
        } finally {
            s.release();r.release();sd.release();rd.release();mask.release();empty.release();sk.release();rk.release();src.release();dst.release();
            if(h!=null)h.release();for(MatOfDMatch group:groups)group.release();orb.clear();dm.clear();clahe.collectGarbage();clahe.clear();
        }
    }
}
