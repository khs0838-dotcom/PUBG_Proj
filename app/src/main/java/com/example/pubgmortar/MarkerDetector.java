package com.example.pubgmortar;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

/** Android bitmap adapter for multi-target marker detection. */
public final class MarkerDetector {
    private MarkerDetector() {}
    public static MarkerFeatureDetector.Batch find(Bitmap photo,Bitmap reference,
            PhotoMapMatcher.Result mapping,double metersPerMapPixel) {
        Mat image=new Mat(),base=new Mat();
        try {
            Utils.bitmapToMat(photo,image);Utils.bitmapToMat(reference,base);
            return MarkerFeatureDetector.find(image,base,mapping.shotToMap,metersPerMapPixel);
        } finally { image.release();base.release(); }
    }
}
