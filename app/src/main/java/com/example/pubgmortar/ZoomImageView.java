package com.example.pubgmortar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.opencv.core.Point;

/** Simple camera-photo preview with pinch zoom, drag and pixel-coordinate tapping. */
public class ZoomImageView extends View {
    public interface PointListener { void onPoint(Point point); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private Point fire, target;
    private PointListener listener;
    private float zoom = 1f, panX = 0, panY = 0;
    private float downX, downY, lastX, lastY;
    private boolean moved, pinched;
    private long downTime;
    public ZoomImageView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(17, 24, 38));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.max(1f, Math.min(12f, zoom * detector.getScaleFactor()));
                pinched = true; invalidate(); return true;
            }
        });
    }
    public void setImage(Bitmap bitmap) {
        this.bitmap = bitmap; zoom = 1; panX = panY = 0; invalidate();
    }
    public void setMarkers(Point firing, Point targeting) {
        fire = firing; target = targeting; invalidate();
    }
    public void setListener(PointListener listener) { this.listener = listener; }
    private float baseScale() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return 1;
        return Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
    }
    private float scale() { return baseScale()*zoom; }
    private float offsetX() { return (getWidth()-bitmap.getWidth()*scale())*0.5f + panX; }
    private float offsetY() { return (getHeight()-bitmap.getHeight()*scale())*0.5f + panY; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            paint.setColor(Color.WHITE); paint.setTextSize(getResources().getDisplayMetrics().density*16f);
            canvas.drawText("기준 지도 또는 촬영 사진을 불러오세요", 20, getHeight()*0.5f, paint);
            return;
        }
        canvas.save();
        canvas.translate(offsetX(), offsetY()); canvas.scale(scale(), scale());
        paint.setColor(Color.WHITE); canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
        drawPin(canvas, fire, Color.rgb(33, 225, 114), "발사");
        drawPin(canvas, target, Color.rgb(255, 75, 75), "목표");
    }
    private void drawPin(Canvas canvas, Point pt, int color, String text) {
        if (pt == null || bitmap == null) return;
        float d = getResources().getDisplayMetrics().density;
        float x = (float)pt.x*scale()+offsetX(), y=(float)pt.y*scale()+offsetY();
        paint.setColor(Color.BLACK); paint.setStrokeWidth(4*d);
        canvas.drawLine(x-12*d,y,x+12*d,y,paint); canvas.drawLine(x,y-12*d,x,y+12*d,paint);
        paint.setColor(color); paint.setStrokeWidth(2*d);
        canvas.drawCircle(x,y,7*d,paint);
        paint.setColor(Color.BLACK); paint.setTextSize(14*d); paint.setStrokeWidth(3*d);
        paint.setStyle(Paint.Style.STROKE); canvas.drawText(text, x+10*d, y-10*d, paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
        canvas.drawText(text, x+10*d, y-10*d, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=lastX=event.getX(); downY=lastY=event.getY();
                downTime=System.currentTimeMillis(); moved=false; pinched=false; return true;
            case MotionEvent.ACTION_POINTER_DOWN: pinched=true; return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx=event.getX()-lastX, dy=event.getY()-lastY;
                    if (Math.hypot(event.getX()-downX, event.getY()-downY) > 12*getResources().getDisplayMetrics().density) moved=true;
                    if (moved) { panX+=dx; panY+=dy; invalidate(); }
                } else pinched=true;
                lastX=event.getX(); lastY=event.getY(); return true;
            case MotionEvent.ACTION_UP:
                if (!moved && !pinched && bitmap!=null && System.currentTimeMillis()-downTime<750) {
                    double x=(event.getX()-offsetX())/scale(), y=(event.getY()-offsetY())/scale();
                    if (x>=0 && y>=0 && x<bitmap.getWidth() && y<bitmap.getHeight() && listener!=null)
                        listener.onPoint(new Point(x,y));
                }
                return true;
            case MotionEvent.ACTION_CANCEL: return true;
        }
        return true;
    }
}
