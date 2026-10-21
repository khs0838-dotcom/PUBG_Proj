package com.example.pubgmortar;

/** Coordinates are measured in metres; elevations are target minus firing position. */
public final class MortarMath {
    private MortarMath() {}

    public static double horizontal(double dx, double dy) { return Math.hypot(dx, dy); }

    /** 0° = North (up), 90° = East (right) on a north-up map. */
    public static double bearing(double dx, double dy) {
        return (Math.toDegrees(Math.atan2(dx, -dy)) + 360.0) % 360.0;
    }

    /** Hypotenuse is not equal to the mortar elevation-adjusted setting. */
    public static double slant(double horizontal, double deltaHeight) {
        return Math.hypot(horizontal, deltaHeight);
    }

    /**
     * Battlemap.gg's published heuristic: horizontal + abs(deltaHeight) * 0.5.
     * This is a reference setting, not a validated trajectory or reachability model.
     * Range limits are checked separately so out-of-range results remain visible.
     */
    public static double correctedSetting(double horizontal, double deltaHeight) {
        if (!(horizontal >= 0) || !Double.isFinite(horizontal) || !Double.isFinite(deltaHeight))
            return Double.NaN;
        double result = horizontal + Math.abs(deltaHeight) * 0.5;
        return result >= 0 && Double.isFinite(result) ? result : Double.NaN;
    }
}
