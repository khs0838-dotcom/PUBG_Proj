package com.example.pubgmortar;
import org.junit.Test;
import static org.junit.Assert.*;

public class MortarMathTest {
    @Test public void distanceAndBearing() {
        assertEquals(500.0, MortarMath.horizontal(300, 400), 1e-7);
        assertEquals(0.0, MortarMath.bearing(0, -10), 1e-7);
        assertEquals(90.0, MortarMath.bearing(10, 0), 1e-7);
        assertEquals(180.0, MortarMath.bearing(0, 10), 1e-7);
    }
    @Test public void altitudeCorrection() {
        assertEquals(350, MortarMath.correctedSetting(350, 0), 1e-7);
        assertEquals(360, MortarMath.correctedSetting(350, 20), 1e-7);
        assertEquals(360, MortarMath.correctedSetting(350, -20), 1e-7);
        assertEquals(465.95, MortarMath.correctedSetting(457.9, 16.1), 1e-7);
        assertEquals(465.95, MortarMath.correctedSetting(457.9, -16.1), 1e-7);
    }
    @Test public void correctionDoesNotClampOutOfRangeSettings() {
        assertEquals(710, MortarMath.correctedSetting(690, 40), 1e-7);
        assertEquals(800, MortarMath.correctedSetting(800, 0), 1e-7);
    }
    @Test public void invalidCorrectionInputs() {
        assertTrue(Double.isNaN(MortarMath.correctedSetting(-1, 20)));
        assertTrue(Double.isNaN(MortarMath.correctedSetting(Double.NaN, 20)));
        assertTrue(Double.isNaN(MortarMath.correctedSetting(350, Double.NaN)));
        assertTrue(Double.isNaN(MortarMath.correctedSetting(Double.POSITIVE_INFINITY, 20)));
        assertTrue(Double.isNaN(MortarMath.correctedSetting(350, Double.NEGATIVE_INFINITY)));
    }
}
