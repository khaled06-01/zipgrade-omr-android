package com.zipgrade.omrscanner;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Image fixtures are whole A4 pages rasterized from the app's exported PDF by Poppler. */
public class PrintedPageRegressionTest {
    private OmrNativeCvEngine.GradeOutcome scan(String variant, int override, String template) throws Exception {
        return scan(variant, override, template, 0);
    }

    private OmrNativeCvEngine.GradeOutcome scan(String variant, int override, String template, int tilt) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/custom-v1/custom-37q-" + variant + ".luma.gz")) {
            assertNotNull(input);
            try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
                int width = data.readInt(), height = data.readInt();
                byte[] luma = new byte[width * height];
                data.readFully(luma);
                if (tilt != 0) luma = rotatePage(luma, width, height, tilt);
                char[] key = new char[100];
                for (int i = 0; i < key.length; i++) key[i] = (char) ('A' + i % 4);
                return OmrNativeCvEngine.analyzeNv21StreamFrame(luma, width, height, false, override, key, template);
            }
        }
    }

    private static byte[] rotatePage(byte[] source, int width, int height, int degrees) {
        double angle = Math.toRadians(degrees);
        double sin = Math.sin(angle), cos = Math.cos(angle);
        double midX = (width - 1) * 0.5, midY = (height - 1) * 0.5;
        byte[] target = new byte[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = (int) Math.round(midX + cos * (x - midX) - sin * (y - midY));
                int sy = (int) Math.round(midY + sin * (x - midX) + cos * (y - midY));
                target[y * width + x] = sx >= 0 && sx < width && sy >= 0 && sy < height
                        ? source[sy * width + sx] : (byte) 255;
            }
        }
        return target;
    }

    @Test public void tiltedPaperRequiresTheSameCountCodeAndReadsAllMarks() throws Exception {
        for (int degrees : new int[]{-8, 8}) {
            OmrNativeCvEngine.GradeOutcome out = scan("filled", 0, "AUTO", degrees);
            assertEquals("CUSTOM_V1_37", out.detectedTemplateCode);
            assertEquals(6, out.fiducialsLocked);
            assertEquals("0033166", out.decodedStudentId);
            assertEquals(37, out.correctCount);
            assertFalse(out.reviewReasons.toString(), out.needsReview);
            assertFalse(scan("filled", 16, "SHEETX_16Q", degrees).templateSelectionValid);
        }
    }

    @Test public void blankPaperReportsItsCountBeforeAnyAnswersAreFilled() throws Exception {
        OmrNativeCvEngine.GradeOutcome out = scan("blank", 0, "AUTO");
        assertEquals("CUSTOM_V1_37", out.detectedTemplateCode);
        assertEquals(37, out.totalQuestions);
        assertEquals(37, out.blankCount);
        assertEquals("NO-ID", out.decodedStudentId);
        assertTrue(out.templateSelectionValid);
        assertTrue(out.needsReview);
    }

    @Test public void markedPaperOverridesAStaleAutoCountAndKeepsItsLeadingZeroId() throws Exception {
        OmrNativeCvEngine.GradeOutcome out = scan("filled", 16, "AUTO");
        assertEquals("CUSTOM_V1_37", out.detectedTemplateCode);
        assertEquals("0033166", out.decodedStudentId);
        assertEquals(37, out.correctCount);
        assertFalse(out.reviewReasons.toString(), out.needsReview);
        OmrNativeCvEngine.GradeOutcome mismatch = scan("filled", 16, CustomSheetLayout.code(16));
        assertFalse(mismatch.templateSelectionValid);
        assertTrue(mismatch.questions.isEmpty());
    }
}
