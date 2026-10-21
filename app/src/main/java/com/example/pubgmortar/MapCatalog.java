package com.example.pubgmortar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** All map references and optional terrain data are packaged into the APK at build time. */
public final class MapCatalog {
    private MapCatalog() {}
    public static final class Entry {
        public final String id, title, imagePath, heightPath;
        public final double worldMeters, heightMetersPerRaw;
        public final boolean heightVerified;
        Entry(String id, String title, String image, String height, double world, double scale, boolean verified) {
            this.id=id; this.title=title; imagePath=image; heightPath=height;
            worldMeters=world; heightMetersPerRaw=scale; heightVerified=verified;
        }
        public Bitmap loadImage(Context context) throws Exception {
            try (InputStream in=context.getAssets().open(imagePath)) {
                Bitmap bmp=BitmapFactory.decodeStream(in);
                if (bmp==null) throw new IllegalStateException("기준 지도 이미지 손상: "+id);
                return bmp;
            }
        }
        public HeightMap loadHeight(Context context) {
            if (heightPath.isEmpty()) return null;
            try (InputStream in=context.getAssets().open(heightPath)) {
                return HeightMap.read(in);
            } catch (Exception ignored) { return null; }
        }
    }
    public static List<Entry> load(Context context) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in=context.getAssets().open("maps/catalog.json")) {
            byte[] buf=new byte[8192]; int n;
            while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        JSONObject catalog=new JSONObject(out.toString("UTF-8"));
        JSONArray maps=catalog.getJSONArray("maps");
        List<Entry> entries=new ArrayList<>();
        for(int i=0;i<maps.length();i++) {
            JSONObject m=maps.getJSONObject(i);
            Entry entry=new Entry(m.getString("id"),m.getString("title"),m.getString("image"),
                m.optString("height", ""),m.getDouble("world_meters"),
                m.optDouble("height_scale_m", 0),m.optBoolean("height_verified", false));
            // CI should verify assets, but also reject incomplete APKs at runtime.
            try (InputStream image=context.getAssets().open(entry.imagePath)) {
                byte[] header=new byte[4];
                if (image.read(header)!=4 || (header[0]&255)!=0xff || (header[1]&255)!=0xd8)
                    throw new IllegalStateException("지도 JPEG 형식 오류: "+entry.id);
            } catch (IOException e) { throw new IOException("APK 내부 지도 누락: "+entry.imagePath,e); }
            if(!entry.heightPath.isEmpty()) {
                try (InputStream height=context.getAssets().open(entry.heightPath)) {
                    byte[] header=new byte[4];
                    if(height.read(header)!=4 || header[0]!='H' || header[1]!='M' || header[2]!='A' || header[3]!='P')
                        throw new IllegalStateException("고도맵 형식 오류: "+entry.heightPath);
                } catch(IOException e) { throw new IOException("APK 내부 고도맵 누락: "+entry.heightPath,e); }
            }
            entries.add(entry);
        }
        if(entries.isEmpty()) throw new IllegalStateException("내장된 실제 PUBG 지도가 없습니다. CI 자료 준비 단계를 확인하세요.");
        return entries;
    }
}
