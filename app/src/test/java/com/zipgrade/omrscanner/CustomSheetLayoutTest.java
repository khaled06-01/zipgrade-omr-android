package com.zipgrade.omrscanner;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustomSheetLayoutTest {
    private static float[] marker(int n, float paper, float ink) {
        float[] samples = new float[CustomSheetLayout.CELL_COUNT];
        for (int i = 0; i < samples.length; i++)
            samples[i] = CustomSheetLayout.isDark(n, i) ? ink : paper;
        return samples;
    }

    @Test public void everyCountRoundTripsUnderThreeLightingRanges() {
        for (int count = 1; count <= 100; count++) {
            assertEquals(count, CustomSheetLayout.parseCode(CustomSheetLayout.code(count)));
            assertEquals(count, CustomSheetLayout.decode(marker(count, 0, 1)));
            assertEquals(count, CustomSheetLayout.decode(marker(count, 0.08f, 0.38f)));
            assertEquals(count, CustomSheetLayout.decode(marker(count, 0.25f, 0.80f)));
        }
        for (String bad : new String[]{null, "", "CUSTOM_V1_0", "CUSTOM_V1_01", "CUSTOM_V1_101",
            "CUSTOM_V1_-1", "CUSTOM_V1_9Q", "CUSTOM_V2_13", "SHEETX_10Q"}) {
            assertEquals(0, CustomSheetLayout.parseCode(bad));
        }
    }

    @Test public void oneCorruptOrGrayCellAlwaysRefusesTheCount() {
        for (int n = 1; n <= 100; n++) {
            float[] samples = marker(n, 0.1f, 0.85f);
            for (int i = 0; i < samples.length; i++) {
                float expected = samples[i];
                samples[i] = expected == 0.1f ? 0.85f : 0.1f;
                assertEquals("flipped cell " + i + " at " + n, -1, CustomSheetLayout.decode(samples));
                samples[i] = 0.5f;
                assertEquals("gray cell " + i + " at " + n, -1, CustomSheetLayout.decode(samples));
                samples[i] = expected;
            }
        }
    }

    @Test public void absentStripAndInvalidSamplesNeverBecomeAValidSize() {
        assertEquals(0, CustomSheetLayout.decode(new float[CustomSheetLayout.CELL_COUNT]));
        assertEquals(0, CustomSheetLayout.decode(marker(12, 0.1f, 0.2f)));
        float[] samples = marker(12, 0, 1);
        samples[12] = Float.NaN;
        assertEquals(-1, CustomSheetLayout.decode(samples));
        assertEquals(-1, CustomSheetLayout.decode(null));
        assertEquals(-1, CustomSheetLayout.decode(new float[9]));
    }

    @Test public void allAnswerDisksStayInsideMarkersAndNeverOverlapIdOrOtherAnswers() {
        final int count = 100;
        final float diameter = 2 * CustomSheetLayout.ANSWER_RADIUS;
        for (int i = 0; i < count * 4; i++) {
            float x = CustomSheetLayout.answerX(count, i / 4 + 1, i % 4);
            float y = CustomSheetLayout.answerY(count, i / 4 + 1);
            assertTrue(x - diameter / 2 > CustomSheetLayout.fiducialX(0));
            assertTrue(x + diameter / 2 < CustomSheetLayout.fiducialX(1));
            assertTrue(y - diameter / 2 > CustomSheetLayout.idY(9) + CustomSheetLayout.ID_RADIUS);
            assertTrue(y + diameter / 2 < CustomSheetLayout.fiducialY(4));
            for (int j = 0; j < i; j++) {
                float dx = x - CustomSheetLayout.answerX(count, j / 4 + 1, j % 4);
                float dy = y - CustomSheetLayout.answerY(count, j / 4 + 1);
                assertTrue(dx * dx + dy * dy > diameter * diameter);
            }
        }
        assertTrue(CustomSheetLayout.cellX(0) - CustomSheetLayout.CELL_SIZE / 2 >
            CustomSheetLayout.fiducialX(0) + CustomSheetLayout.MARKER_HALF_SIZE);
        assertTrue(CustomSheetLayout.cellX(CustomSheetLayout.CELL_COUNT - 1) + CustomSheetLayout.CELL_SIZE / 2 <
            CustomSheetLayout.fiducialX(1) - CustomSheetLayout.MARKER_HALF_SIZE);
    }

    @Test public void rowAndCountBoundsAreEnforcedAtCreation() {
        for (int n = 1; n <= 100; n++) {
            int count = n;
            CustomSheetLayout.answerX(count, count, 3);
            assertThrows(IllegalArgumentException.class, () -> CustomSheetLayout.answerY(count, count + 1));
        }
        assertThrows(IllegalArgumentException.class, () -> CustomSheetLayout.code(0));
        assertThrows(IllegalArgumentException.class, () -> CustomSheetLayout.code(101));
    }
}
