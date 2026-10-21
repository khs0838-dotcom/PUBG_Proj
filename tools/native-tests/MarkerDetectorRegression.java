import com.example.pubgmortar.MarkerFeatureDetector;
import com.example.pubgmortar.MortarMath;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Exercises the actual native marker detector; pins are synthetic, not game UI fixtures. */
public class MarkerDetectorRegression {
    static final Point[] PINS={new Point(150,150),new Point(450,150),new Point(150,450),new Point(450,450),new Point(300,100)};
    static final Scalar[] COLORS={new Scalar(255,50,50,255),new Scalar(50,255,50,255),new Scalar(50,80,255,255),new Scalar(255,230,30,255),new Scalar(50,255,50,255)};
    static void player(Mat image,int x,int y) {
        MatOfPoint points=new MatOfPoint(new Point(x,y-13),new Point(x-12,y+11),new Point(x+12,y+11));
        try { Imgproc.fillConvexPoly(image,points,new Scalar(255,255,255,255)); } finally { points.release(); }
    }
    static void expect(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) {
        nu.pattern.OpenCV.loadLocally();Core.setNumThreads(2);
        Mat base=new Mat(600,600,CvType.CV_8UC4,new Scalar(45,45,45,255));
        Mat identity=Mat.eye(3,3,CvType.CV_64F);
        int passed=0;
        try {
            for(int count:new int[]{1,2,4,5}) {
                Mat shot=base.clone();
                try {
                    player(shot,300,300);
                    for(int i=0;i<count;i++)Imgproc.circle(shot,PINS[i],10,COLORS[i],-1);
                    MarkerFeatureDetector.Batch found=MarkerFeatureDetector.find(shot,base,identity,10);
                    expect(found!=null,"No detection for "+count+" pins");
                    expect(found.targetsPhoto.size()==Math.min(4,count),"Wrong pin count");
                    expect(Math.hypot(found.playerPhoto.x-300,found.playerPhoto.y-300)<5,"Wrong firing point");
                    for(int i=0;i<found.targetsPhoto.size();i++) {
                        Point pin=found.targetsPhoto.get(i);double best=1e9;
                        for(int j=0;j<count;j++)best=Math.min(best,Math.hypot(pin.x-PINS[j].x,pin.y-PINS[j].y));
                        expect(best<2,"False target");
                        if(count<=4)expect(Math.hypot(pin.x-PINS[i].x,pin.y-PINS[i].y)<2,"Unstable target numbering");
                        for(int j=0;j<i;j++)expect(Math.hypot(pin.x-found.targetsPhoto.get(j).x,pin.y-found.targetsPhoto.get(j).y)>20,"Duplicate pin");
                        double distance=MortarMath.horizontal((pin.x-found.playerPhoto.x)*10,(pin.y-found.playerPhoto.y)*10);
                        expect(distance>700,"Out-of-range targets should be retained and warned about by UI");
                        expect(MortarMath.correctedSetting(distance,20)==distance+10,"Per-target correction");
                    }
                    expect((count>4)==found.warning.contains("4개를 넘어"),"Missing truncation warning");
                    System.out.println("PASS "+count+" pins -> "+found.targetsPhoto.size()+" targets; shared origin; RGB pin colors; range retained");passed++;
                } finally { shot.release(); }
            }
            for(int kind=0;kind<3;kind++) {
                Mat shot=base.clone();
                try {
                    if(kind!=0)player(shot,300,300);
                    if(kind!=1)Imgproc.circle(shot,PINS[0],10,COLORS[0],-1);
                    if(kind==2)player(shot,400,300);
                    expect(MarkerFeatureDetector.find(shot,base,identity,1)==null,"Ambiguous/missing origin/target accepted");
                    System.out.println("PASS reject "+new String[]{"missing origin","missing target","ambiguous origins"}[kind]);passed++;
                } finally { shot.release(); }
            }
            System.out.println("PASSED "+passed+" native multi-target cases (synthetic; not real game pin accuracy)");
        } finally { base.release();identity.release(); }
    }
}
