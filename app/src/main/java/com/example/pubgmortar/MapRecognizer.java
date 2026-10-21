package com.example.pubgmortar;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.List;

/** Match one camera photo against every bundled map. Never guess a map if ambiguous. */
public final class MapRecognizer {
    private MapRecognizer() {}
    public static final class Selection {
        public final MapCatalog.Entry entry;
        public final PhotoMapMatcher.Result transform;
        public final Bitmap reference;
        public final int secondBest;
        Selection(MapCatalog.Entry e, PhotoMapMatcher.Result t, Bitmap b, int runnerUp) {
            entry=e; transform=t; reference=b; secondBest=runnerUp;
        }
    }
    public static Selection detect(Context context, Bitmap shot, List<MapCatalog.Entry> maps) throws Exception {
        PhotoMapMatcher.Result best=null;
        MapCatalog.Entry bestMap=null;
        Bitmap bestBitmap=null;
        int second=0;
        try(MapFeatureMatcher.Features features=PhotoMapMatcher.prepareShot(shot)) {
            for(int pass=0;pass<2;pass++) {
                for(MapCatalog.Entry candidate:maps) {
                    Bitmap ref=candidate.loadImage(context);
                    PhotoMapMatcher.Result r;
                    try { r=PhotoMapMatcher.match(features, ref, pass==1); }
                    catch(RuntimeException e) { ref.recycle(); throw e; }
                    if(r!=null && (best==null || r.inliers>best.inliers)) {
                        if(best!=null) { second=Math.max(second,best.inliers); best.shotToMap.release(); }
                        if(bestBitmap!=null) bestBitmap.recycle();
                        best=r; bestMap=candidate; bestBitmap=ref;
                    } else {
                        if(r!=null) { second=Math.max(second,r.inliers); r.shotToMap.release(); }
                        ref.recycle();
                    }
                }
                if(best!=null)break;
            }
        } catch(Exception e) {
            if(best!=null)best.shotToMap.release();
            if(bestBitmap!=null)bestBitmap.recycle();
            throw e;
        }
        if(best==null) return null;
        // If two maps match equally well, there is no defensible map identification.
        if(best.inliers<16 || (second>=16 && best.inliers<second*1.25)) {
            best.shotToMap.release(); bestBitmap.recycle(); return null;
        }
        return new Selection(bestMap,best,bestBitmap,second);
    }
}
