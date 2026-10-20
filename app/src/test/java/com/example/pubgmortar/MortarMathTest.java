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
        assertEquals(350, MortarMath.correctedSetting(350, 0, 700), 1e-7);
        assertTrue(MortarMath.correctedSetting(350, 20, 700) > 350);
        assertTrue(MortarMath.correctedSetting(350, -20, 700) < 350);
        assertTrue(Double.isNaN(MortarMath.correctedSetting(800, 0, 700)));
    }
}
