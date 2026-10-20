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
     * EXPERIMENTAL game-ballistics approximation, not a PUBG official ballistics model.
     * Ground range (level shot) maxRange ~= 700 m, based on community model.
     * Returns NaN when the assumed projectile trajectory cannot reach the target.
     */
    public static double correctedSetting(double horizontal, double deltaHeight, double maxRange) {
        if (!(horizontal > 0) || !(maxRange > 0)) return Double.NaN;
        double tanBeta = deltaHeight / horizontal;
        double discriminant = maxRange * maxRange - 2 * horizontal * maxRange * tanBeta - horizontal * horizontal;
        if (discriminant < 0) return Double.NaN;
        double result = (horizontal + tanBeta * (maxRange - Math.sqrt(discriminant))) / (1 + tanBeta * tanBeta);
        return result >= 0 && Double.isFinite(result) ? result : Double.NaN;
    }
}
