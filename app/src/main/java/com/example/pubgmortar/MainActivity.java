package com.example.pubgmortar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Unofficial experimental PUBG mortar calculator.
 * Camera image -> ORB registration to reference map -> tapping coordinates -> map distance.
 * Elevation is only available with registered/calibrated HMAP or a manual delta-H input.
 */
public class MainActivity extends Activity {
    private static final int PICK_MAP=10, PICK_HMAP=11, PICK_PHOTO=12, CAMERA=13;
    private enum Display { PHOTO, MAP }
    private enum TapMode { FIRE, TARGET, GRID }
    private Display display = Display.PHOTO;
    private TapMode tapMode = TapMode.FIRE;
    private Bitmap reference, photo;
    private HeightMap heights;
    private PhotoMapMatcher.Result alignment;
    private Point fireMap, targetMap, firePhoto, targetPhoto, gridFirst;
    private double manualMetersPerPixel = Double.NaN;
    private String photoFilename;
    private ZoomImageView preview;
    private TextView status, results;
    private EditText mapWidthInput, rawHeightScaleInput, manualHeightInput;
    private boolean opencvReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        opencvReady = OpenCVLoader.initLocal();
        setContentView(buildUi());
        try {
            File f = new File(getFilesDir(), "reference.jpg");
            if (f.exists()) reference = BitmapFactory.decodeFile(f.getAbsolutePath());
            File h = new File(getFilesDir(), "height.hmap");
            if (h.exists()) try (InputStream stream = new FileInputStream(h)) { heights = HeightMap.read(stream); }
            if (reference != null) showMap();
        } catch (Exception e) { setStatus("저장된 자료 읽기 실패: " + e.getMessage()); }
        if (!opencvReady) setStatus("OpenCV 초기화 실패. 지도 자동 인식이 비활성화됩니다.");
        else if (reference == null) setStatus("먼저 전체 기준 지도 이미지를 가져오세요. 이후 사진 촬영 → 자동 정합 → 위치 선택 순서입니다.");
    }

    private int dp(int p) { return (int) (p*getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams lp() { return new LinearLayout.LayoutParams(-1, -2); }
    private TextView text(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(31,41,55));
        t.setPadding(dp(4),dp(6),dp(4),dp(6)); return t;
    }
    private Button button(String title, LinearLayout parent, View.OnClickListener action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(action);
        parent.addView(b, lp()); return b;
    }
    private EditText numeric(String label, String value, LinearLayout parent, boolean signed) {
        TextView title = text(label, 13); parent.addView(title, lp());
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(value); e.setTextSize(17);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
            (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        parent.addView(e, lp()); return e;
    }
    private View buildUi() {
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(12),dp(12),dp(30)); root.setBackgroundColor(Color.WHITE);
        sv.addView(root);
        TextView title=text("PUBG 박격포 계산기 · 실험판", 22); title.setTextColor(Color.rgb(20,54,96)); root.addView(title);
        root.addView(text("PC 지도 사진 자동 정합 + 수평거리 + 고도 보정", 13));
        button("1. 전체 기준 지도 가져오기 (PNG/JPG)",root,v -> selectFile(PICK_MAP,"image/*"));
        button("2. 16비트 고도맵 .hmap 가져오기 (선택)",root,v -> selectFile(PICK_HMAP,"*/*"));
        button("3. 모니터 지도 촬영하기",root,v -> openCamera());
        button("또는 촬영한 사진 가져오기",root,v -> selectFile(PICK_PHOTO,"image/*"));
        button("4. 사진과 전체 지도 자동 정합",root,v -> autoRegister());
        button("사진 보기 / 전체 지도 보기 전환",root,v -> {
            if (display==Display.PHOTO) showMap(); else showPhoto();
        });
        root.addView(text("사진에서 발사점/목표점을 선택하세요. 핀치 확대 가능. 자동 정합 실패 시 전체 지도에서 직접 선택하거나 사진의 100m 격자 2점을 지정해 수동 보정하세요.", 13));
        preview = new ZoomImageView(this);
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(360)));
        preview.setListener(this::onMapTap);
        button("● 발사 위치 선택 (다음 터치)", root, v -> { tapMode=TapMode.FIRE; setStatus("초록색 발사 위치를 지도에서 터치하세요."); });
        button("● 목표 위치 선택 (다음 터치)", root, v -> { tapMode=TapMode.TARGET; setStatus("빨간색 목표 위치를 지도에서 터치하세요."); });
        button("수동 100m 격자 보정: 양끝 2점 터치",root,v -> {
            if (photo==null) { setStatus("사진을 먼저 불러오세요."); return; }
            showPhoto(); tapMode=TapMode.GRID; gridFirst=null;
            setStatus("사진 속 100m 격자의 한쪽 끝을 터치한 다음 다른 쪽 끝을 터치하세요.");
        });
        mapWidthInput = numeric("전체 기준 지도 가로 길이 (m, 에란겔 8000)","8000",root,false);
        rawHeightScaleInput = numeric("고도맵 raw 값 1당 미터값 (검증 전이면 공란)","",root,true);
        manualHeightInput = numeric("수동 고도차 (목표 높이 - 발사 높이, m / 우선 적용)","",root,true);
        button("5. 거리·방위각·고도 보정 계산",root,v -> calculate());
        status=text("",14); status.setTextColor(Color.rgb(39,91,154)); root.addView(status,lp());
        results=text("계산 결과가 표시됩니다.",17); results.setTextColor(Color.rgb(13,34,58)); root.addView(results,lp());
        root.addView(text("※ 고도맵이 없거나 raw→m 변환이 검증되지 않으면 자동 고도 계산 불가. 보정 포물선 모델도 게임 내 실측 검증 전까지 참고용입니다.", 13));
        return sv;
    }

    private void setStatus(String s) { status.setText(s); }
    private void selectFile(int code, String mime) {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(mime);
        startActivityForResult(i,code);
    }
    private void openCamera() {
        try {
            File dir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir==null) throw new IOException("촬영 저장 디렉터리를 사용할 수 없습니다");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("촬영 디렉터리 생성 실패");
            File out=new File(dir,"capture.jpg"); photoFilename=out.getAbsolutePath();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",out);
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent,CAMERA);
        } catch (Exception e) { setStatus("카메라 실행 실패: " + e.getMessage()); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (result!=RESULT_OK) return;
        try {
            if (request==CAMERA) {
                if (photoFilename==null) throw new IOException("촬영 파일 경로 누락");
                loadPhoto(Uri.fromFile(new File(photoFilename)));
            } else {
                if (data==null || data.getData()==null) throw new IOException("선택 파일이 없습니다");
                Uri uri=data.getData();
                if (request==PICK_MAP) {
                    reference=loadScaled(uri,2400); saveBitmap(reference,"reference.jpg");
                    alignment=null; clearPoints(); showMap();
                    setStatus("전체 기준 지도: "+reference.getWidth()+"×"+reference.getHeight()+". 사진 정합 준비 완료.");
                } else if (request==PICK_HMAP) {
                    try (InputStream in=getContentResolver().openInputStream(uri)) { heights=HeightMap.read(in); }
                    try (InputStream src=getContentResolver().openInputStream(uri);
                         FileOutputStream dst=openFileOutput("height.hmap",MODE_PRIVATE)) {
                        byte[] b=new byte[32768]; int n;
                        while ((n=src.read(b))!=-1) dst.write(b,0,n);
                    }
                    setStatus("고도맵 등록: "+heights.width+"×"+heights.height+". 좌표 정합과 고도 단위값 검증이 별도로 필요합니다.");
                } else if (request==PICK_PHOTO) loadPhoto(uri);
            }
        } catch (Exception e) { setStatus("자료 불러오기 실패: "+e.getMessage()); }
    }
    private void loadPhoto(Uri uri) throws IOException {
        photo=loadScaled(uri,2400); alignment=null; manualMetersPerPixel=Double.NaN;
        clearPoints(); showPhoto();
        setStatus("촬영한 지도 로드 완료. ‘사진과 전체 지도 자동 정합’을 누르세요.");
    }
    private Bitmap loadScaled(Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options size=new BitmapFactory.Options(); size.inJustDecodeBounds=true;
        try (InputStream in=getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,size); }
        int sample=1;
        while (Math.max(size.outWidth,size.outHeight)/sample>maxSide) sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=sample;
        Bitmap bmp;
        try (InputStream in=getContentResolver().openInputStream(uri)) { bmp=BitmapFactory.decodeStream(in,null,options); }
        if (bmp==null) throw new IOException("이미지 디코딩에 실패했습니다");
        int orientation=ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in=getContentResolver().openInputStream(uri)) {
            orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {}
        Matrix m=new Matrix();
        if (orientation==ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
        else if (orientation==ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
        else if (orientation==ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        else if (orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL) m.postScale(-1,1);
        else if (orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL) m.postScale(1,-1);
        if (!m.isIdentity()) { Bitmap rotated=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true); bmp=rotated; }
        return bmp;
    }
    private void saveBitmap(Bitmap bmp,String name) throws IOException {
        if (bmp==null) return;
        try (FileOutputStream out=openFileOutput(name,MODE_PRIVATE)) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG,96,out)) throw new IOException("이미지 저장 실패");
        }
    }
    private void clearPoints() { fireMap=targetMap=firePhoto=targetPhoto=null; updateMarkers(); }
    private void showMap() {
        if (reference==null) { setStatus("기준 지도 이미지가 없습니다."); return; }
        display=Display.MAP;
        if (preview!=null) { preview.setImage(reference); updateMarkers(); }
        setStatus("전체 기준 지도 보기. 발사지점/목표 선택 버튼을 누른 뒤 위치를 터치하세요.");
    }
    private void showPhoto() {
        if (photo==null) { setStatus("촬영한 사진이 없습니다."); return; }
        display=Display.PHOTO;
        if (preview!=null) { preview.setImage(photo); updateMarkers(); }
        setStatus("촬영 사진 보기. 자동 정합 시 사진 터치 좌표가 전체 지도 좌표로 변환됩니다.");
    }
    private void updateMarkers() {
        if (preview==null) return;
        preview.setMarkers(display==Display.MAP ? fireMap:firePhoto,
                           display==Display.MAP ? targetMap:targetPhoto);
    }
    private void autoRegister() {
        if (!opencvReady || photo==null || reference==null) {
            setStatus("자동 정합은 OpenCV, 촬영 사진, 전체 기준 지도가 필요합니다."); return;
        }
        setStatus("사진 특징점과 기준 지도 좌표를 비교 중입니다...");
        Bitmap ss=photo, rr=reference;
        new Thread(() -> {
            try {
                PhotoMapMatcher.Result candidate=PhotoMapMatcher.match(ss,rr);
                runOnUiThread(() -> {
                    if (ss!=photo || rr!=reference) return;
                    alignment=candidate; manualMetersPerPixel=Double.NaN; clearPoints(); showPhoto();
                    if (candidate==null) setStatus("자동 정합 실패: 지도만 크게 찍거나 조명을 줄여 다시 시도하세요. 전체 지도 직접 선택 또는 100m 격자 수동 보정도 가능합니다.");
                    else setStatus("정합 성공 (RANSAC 내점 "+candidate.inliers+"/"+candidate.candidates+"). 발사점·목표점을 터치하세요. 인식 결과는 게임 화면으로 검증하세요.");
                });
            } catch (Exception e) { runOnUiThread(() -> setStatus("정합 오류: "+e.getMessage())); }
        }).start();
    }
    private void onMapTap(Point point) {
        if (display==Display.PHOTO && tapMode==TapMode.GRID) {
            if (gridFirst==null) { gridFirst=point; setStatus("첫 번째 100m 기준점 등록. 정확히 100m 떨어진 격자의 두 번째 점을 터치하세요."); }
            else {
                double px=Math.hypot(point.x-gridFirst.x,point.y-gridFirst.y);
                if (px<10) { setStatus("격자 기준점 사이 거리가 너무 짧습니다."); return; }
                manualMetersPerPixel=100/px; alignment=null; clearPoints(); tapMode=TapMode.FIRE;
                gridFirst=null; setStatus(String.format(Locale.KOREA,"100m 격자 보정 완료: %.4fm/픽셀. 발사/목표점을 선택하세요. 고도맵 자동 연동은 지원하지 않습니다.",manualMetersPerPixel));
            }
            return;
        }
        Point mapped=null, shotPoint=null;
        if (display==Display.MAP) mapped=point;
        else {
            shotPoint=point;
            if (alignment!=null) {
                mapped=alignment.mapPoint(point);
                if (!Double.isFinite(mapped.x) || !Double.isFinite(mapped.y) || mapped.x<0 || mapped.y<0 || mapped.x>=reference.getWidth() || mapped.y>=reference.getHeight()) {
                    setStatus("사진 터치 좌표가 기준 지도 범위 밖입니다. 사진/기준 지도 정합을 확인하세요."); return;
                }
            } else if (!Double.isFinite(manualMetersPerPixel)) {
                setStatus("먼저 자동 지도 정합 또는 100m 격자 수동 보정을 수행하세요."); return;
            }
        }
        if (tapMode==TapMode.FIRE) {
            fireMap=mapped; firePhoto=shotPoint; tapMode=TapMode.TARGET;
            setStatus("발사 위치 설정. 다음으로 목표 위치를 선택하세요.");
        } else {
            targetMap=mapped; targetPhoto=shotPoint;
            setStatus("목표 위치 설정. 아래 ‘거리·방위각·고도 보정 계산’을 누르세요.");
            calculate();
        }
        updateMarkers();
    }
    private Double inputNumber(EditText e) {
        String s=e.getText().toString().trim();
        if (s.isEmpty()) return null;
        try { double d=Double.parseDouble(s); return Double.isFinite(d)?d:null; }
        catch (NumberFormatException ex) { return null; }
    }
    private void calculate() {
        Double mapSide=inputNumber(mapWidthInput);
        if (mapSide==null || mapSide<=0) { setStatus("기준 지도의 가로 길이를 양수 미터값으로 입력하세요."); return; }
        double dx,dy,range;
        boolean exactMapCoord = fireMap!=null && targetMap!=null && reference!=null;
        if (exactMapCoord) {
            dx=(targetMap.x-fireMap.x)/reference.getWidth()*mapSide;
            dy=(targetMap.y-fireMap.y)/reference.getWidth()*mapSide;
            range=MortarMath.horizontal(dx,dy);
        } else if (firePhoto!=null && targetPhoto!=null && Double.isFinite(manualMetersPerPixel)) {
            dx=(targetPhoto.x-firePhoto.x)*manualMetersPerPixel;
            dy=(targetPhoto.y-firePhoto.y)*manualMetersPerPixel;
            range=MortarMath.horizontal(dx,dy);
        } else {
            setStatus("발사점과 목표점 2개를 동일한 지도 보기 모드에서 지정하세요."); return;
        }
        if (range<1) { setStatus("두 지점이 너무 가깝습니다."); return; }
        StringBuilder b=new StringBuilder();
        b.append(String.format(Locale.KOREA,"수평 거리: %.1f m\n방위각: %.1f° (북쪽 0°, 시계 방향)\n",range,MortarMath.bearing(dx,dy)));
        if (exactMapCoord) b.append("좌표: 기준 지도 정합 기준\n");
        else b.append("좌표: 100m 격자 수동 보정 (사진 원근 오차 가능)\n");
        Double manual=inputNumber(manualHeightInput);
        Double difference=null;
        String heightOrigin="";
        if (manual!=null) {
            difference=manual; heightOrigin="직접 입력한 고도차";
        } else if (exactMapCoord && heights!=null && inputNumber(rawHeightScaleInput)!=null) {
            double scale=inputNumber(rawHeightScaleInput);
            difference=(heights.sample(targetMap.x/reference.getWidth(),targetMap.y/reference.getHeight())
                   -heights.sample(fireMap.x/reference.getWidth(),fireMap.y/reference.getHeight()))*scale;
            heightOrigin="고도맵 기반 (좌표·raw 단위 교정 필요)";
        }
        if (difference==null) {
            b.append("고도차: 정보 없음\n고도 보정 조준 거리: 계산 보류\n");
            if (heights!=null) b.append("고도맵이 있지만 raw→m 값을 입력하지 않았습니다.\n");
            b.append("수동 고도차를 입력하면 실험적인 보정값을 계산합니다.\n");
        } else {
            b.append(String.format(Locale.KOREA,"고도차(목표-발사): %+.1f m\n",difference));
            b.append(String.format(Locale.KOREA,"높이 포함 직선거리: %.1f m\n",MortarMath.slant(range,difference)));
            double setting=MortarMath.correctedSetting(range,difference,700);
            if (Double.isNaN(setting)) b.append("실험 탄도 모형: 도달 불가 / 계산 불가\n");
            else b.append(String.format(Locale.KOREA,"실험적 박격포 조준 거리: %.1f m\n",setting));
            b.append("고도 출처: ").append(heightOrigin).append('\n');
        }
        if (range<121 || range>700) b.append("주의: 역사적 박격포 121~700m 사거리 밖\n");
        b.append("※ 게임 고도·탄도 검증 전. 실측 조준 수치로 단정하지 마세요.");
        results.setText(b.toString());
    }
}
