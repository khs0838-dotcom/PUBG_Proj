package com.example.pubgmortar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Photo-only screen: never asks the player to locate map or height files. */
public class MainActivity extends Activity {
    private static final int CAMERA=13;
    private TextView status, results;
    private ZoomImageView preview;
    private List<MapCatalog.Entry> maps;
    private String photoFilename;
    private Bitmap photo;
    private int captureGeneration;
    private boolean opencvReady;
    private String opencvProblem="검사 전";
    private String mapProblem="검사 전";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(buildUi());
        try {
            opencvReady=OpenCVLoader.initLocal();
            opencvProblem=opencvReady?"정상":"OpenCVLoader.initLocal() = false (네이티브 라이브러리 확인 필요)";
        } catch (Throwable e) {
            opencvReady=false;
            opencvProblem=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        }
        try {
            maps=MapCatalog.load(this);
            int heights=0;
            for (MapCatalog.Entry entry: maps) if (!entry.heightPath.isEmpty()) heights++;
            mapProblem="정상 ("+maps.size()+"개 지도 / "+heights+"개 과거 고도 데이터)";
        } catch (Exception e) {
            maps=null;
            mapProblem="실패: "+e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        }
        status.setText(healthReport());
        if (opencvReady && maps!=null && !maps.isEmpty()) {
            preview.post(this::openCamera);
        }
    }

    private int dp(int d) {return (int)(d*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String value, int size, int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;
    }
    private View buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(16),dp(12),dp(30));root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);
        root.addView(label("PUBG 박격포 · 사진 자동 계산",23,Color.rgb(17,45,80)));
        root.addView(label("모니터의 전술 지도를 촬영하면 지도·거리·마커를 자동 분석합니다.\n지도를 최대한 정면에서 찍고, 현재 위치 아이콘과 목표 핑을 모두 표시하세요.",14,Color.DKGRAY));
        Button shutter=new Button(this);shutter.setAllCaps(false);shutter.setText("📷  지도 다시 촬영하기");
        shutter.setOnClickListener(v->openCamera());root.addView(shutter);
        status=label("지도 자료 확인 중...",15,Color.rgb(25,91,145));root.addView(status);
        preview=new ZoomImageView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,dp(355)));
        results=label("촬영 후 여기에 계산 결과가 표시됩니다.",18,Color.rgb(12,45,70));root.addView(results);
        root.addView(label("주의: 핑·내 위치 아이콘 식별은 아직 실전 정확도가 검증되지 않은 시험 기능입니다. 여러 마커가 겹치거나 지도 특징이 적으면 수치를 추측하지 않고 인식 실패로 표시합니다.\n구버전 지형 고도맵은 최신 PUBG 고도와 다를 수 있으며, 박격포 탄도 보정값은 공식 검증값이 아닙니다.",12,Color.GRAY));
        return scroll;
    }
    private String healthReport() {
        return "앱 자료 점검\n• 기준 지도: "+mapProblem+"\n• OpenCV: "+opencvProblem+
            "\n\n"+((maps!=null && !maps.isEmpty() && opencvReady) ?
            "준비 완료. 촬영 후 자동 계산합니다. (고도는 해당 맵에 자료가 있을 때만)" :
            "이 APK는 촬영을 실행할 수 없습니다. GitHub Actions의 자료 준비 및 APK 검증 로그를 확인하세요.");
    }
    private void openCamera() {
        if(maps==null || maps.isEmpty() || !opencvReady) {
            status.setText(healthReport());return;
        }
        try {
            File dir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if(dir==null || (!dir.exists()&&!dir.mkdirs())) throw new IOException("촬영 경로를 만들 수 없습니다");
            File out=new File(dir,"mortar_camera_"+System.currentTimeMillis()+".jpg");
            photoFilename=out.getAbsolutePath();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",out);
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent,CAMERA);
        } catch(Exception e) {status.setText("카메라 실행 실패: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=CAMERA) return;
        if(result!=RESULT_OK) {status.setText("촬영이 취소되었습니다. 다시 촬영하기를 눌러주세요.");return;}
        try {
            photo=loadPhoto(Uri.fromFile(new File(photoFilename)));
            preview.setImage(photo);
            preview.setMarkers(null,null);
            results.setText("인식 중...");
            recognizePhoto(photo);
        } catch(Exception e) {status.setText("촬영 이미지 읽기 실패: "+e.getMessage());}
    }
    private Bitmap loadPhoto(Uri uri) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)) {BitmapFactory.decodeStream(in,null,bounds);}
        int sample=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/sample>2200)sample*=2;
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;
        Bitmap image;
        try(InputStream in=getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,opt);}
        if(image==null)throw new IOException("이미지 디코딩 실패");
        int o=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            o=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,o);
        }catch(Exception ignored){}
        Matrix matrix=new Matrix();
        if(o==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);
        if(o==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);
        if(o==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);
        if(!matrix.isIdentity())image=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
        return image;
    }
    private void recognizePhoto(Bitmap shot) {
        final int generation=++captureGeneration;
        status.setText("촬영 사진을 내장 지도와 대조하는 중입니다. 기종에 따라 수 초 이상 걸릴 수 있습니다...");
        new Thread(()->{
            MapRecognizer.Selection found=null;
            try {
                found=MapRecognizer.detect(getApplicationContext(),shot,maps);
                if(found==null) {
                    postFailure(generation,"지도 자동 인식 실패: 반사·흔들림을 줄이고 도로·건물 등 지형 특징과 웨이포인트가 함께 보이게 촬영하세요. 과도하게 확대된 저해상도 지도는 인식이 어려울 수 있습니다.");return;
                }
                MapCatalog.Entry m=found.entry;
                Bitmap ref=found.reference;
                double mPerPixel=m.worldMeters/ref.getWidth();
                MarkerDetector.Pair markers=MarkerDetector.find(shot,ref,found.transform,mPerPixel);
                if(markers==null) {
                    String mapName=m.title;
                    postFailure(generation,"지도 인식: "+mapName+"\n현재 위치 또는 목표 핑 자동 인식 실패. 게임 지도에 두 아이콘을 표시하고 다시 촬영하세요. 다른 색상의 마커·게임 버전은 지원되지 않을 수 있습니다.");
                    return;
                }
                Point fire=found.transform.mapPoint(markers.playerPhoto);
                Point target=found.transform.mapPoint(markers.targetPhoto);
                double dx=(target.x-fire.x)*mPerPixel,dy=(target.y-fire.y)*mPerPixel;
                double horizontal=MortarMath.horizontal(dx,dy);
                double angle=MortarMath.bearing(dx,dy);
                if(!(horizontal>0 && horizontal<1600)) {
                    postFailure(generation,"마커 좌표가 신뢰할 수 있는 범위를 벗어났습니다. 다시 촬영하세요.");return;
                }
                StringBuilder lines=new StringBuilder();
                lines.append(m.title).append("\n")
                    .append(String.format(Locale.KOREA,"수평거리 %.1f m\n방위각 %.1f°\n",horizontal,angle));
                if(horizontal<121 || horizontal>700) lines.append("참고: 구버전 박격포 유효 사거리(121–700m) 밖\n");
                HeightMap h=m.loadHeight(getApplicationContext());
                if(h==null || !(m.heightMetersPerRaw>0)) {
                    lines.append("고도차: 검증된 자료 없음 (자동 계산 보류)\n");
                    lines.append("고도 보정 조준 거리: 표시 안 함\n");
                } else {
                    double heightDelta=(h.sample(target.x/ref.getWidth(),target.y/ref.getHeight())
                        -h.sample(fire.x/ref.getWidth(),fire.y/ref.getHeight()))*m.heightMetersPerRaw;
                    lines.append(String.format(Locale.KOREA,"고도차: %+.1f m (%s)\n",heightDelta,
                        m.heightVerified?"검증됨":"과거 지형 기준·미검증"));
                    double setting=MortarMath.correctedSetting(horizontal,heightDelta);
                    if(Double.isFinite(setting)) {
                        lines.append(String.format(Locale.KOREA,"고도 보정 참고거리: %.1f m\n",setting));
                        lines.append("보정식: 수평거리 + |고도차| × 0.5 (실제 탄착 미검증)\n");
                        if(setting>700) lines.append("참고: 보정 참고거리가 최대 설정거리 700m 초과\n");
                    }
                    else lines.append("조준거리 모델: 계산 불가\n");
                }
                lines.append("정합 내점 수: ").append(found.transform.inliers).append('\n');
                lines.append(markers.warning);
                Point mark1=markers.playerPhoto,mark2=markers.targetPhoto;
                String output=lines.toString();
                runOnUiThread(()->{
                    if(generation!=captureGeneration)return;
                    preview.setMarkers(mark1,mark2);
                    status.setText("자동 분석 완료 — 마커 인식 결과를 촬영 화면과 비교하세요.");
                    results.setText(output);
                });
            } catch(Exception e) {
                postFailure(generation,"자동 분석 오류: "+e.getMessage());
            } finally {
                if(found!=null){found.transform.shotToMap.release();found.reference.recycle();}
            }
        },"pubg-map-recognizer").start();
    }
    private void postFailure(int generation,String message) {
        runOnUiThread(()->{
            if(generation!=captureGeneration)return;
            status.setText(message);results.setText("거리·고도 계산 결과 없음\n불확실한 위치를 임의로 계산하지 않았습니다.");
        });
    }
}
