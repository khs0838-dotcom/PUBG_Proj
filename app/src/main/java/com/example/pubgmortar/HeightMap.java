package com.example.pubgmortar;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * HMAP binary format: "HMAP", width little-endian int32, height little-endian int32,
 * then row-major little-endian UNSIGNED int16 raster aligned to reference map extent.
 * Pixel values do NOT intrinsically carry real-world elevation units.
 */
public final class HeightMap {
    public final int width, height;
    private final short[] raw;
    private HeightMap(int w, int h, short[] raw) { width = w; height = h; this.raw = raw; }

    public static HeightMap read(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        byte[] magic = new byte[4]; in.readFully(magic);
        if (magic[0] != 'H' || magic[1] != 'M' || magic[2] != 'A' || magic[3] != 'P')
            throw new IOException(".hmap 파일 형식이 아닙니다");
        int w = Integer.reverseBytes(in.readInt());
        int h = Integer.reverseBytes(in.readInt());
        if (w < 2 || h < 2 || (long) w * h > 8_000_000L) throw new IOException("고도맵 크기가 잘못되었습니다");
        byte[] bytes = new byte[w*h*2]; in.readFully(bytes);
        short[] raster = new short[w*h];
        for (int i = 0; i < raster.length; i++) {
            raster[i] = (short) ((bytes[2*i] & 255) | ((bytes[2*i+1] & 255) << 8));
        }
        return new HeightMap(w, h, raster);
    }
    private double rawAt(int x, int y) { return raw[y*width+x] & 0xFFFF; }
    public double sample(double nx, double ny) {
        double fx = Math.max(0, Math.min(width-1, nx * (width-1)));
        double fy = Math.max(0, Math.min(height-1, ny * (height-1)));
        int x = (int) fx, y = (int) fy;
        int x2 = Math.min(width-1, x+1), y2 = Math.min(height-1, y+1);
        double a = fx-x, b = fy-y;
        return rawAt(x,y)*(1-a)*(1-b) + rawAt(x2,y)*a*(1-b)
            + rawAt(x,y2)*(1-a)*b + rawAt(x2,y2)*a*b;
    }
}
