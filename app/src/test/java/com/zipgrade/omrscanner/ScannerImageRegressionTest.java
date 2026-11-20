package com.zipgrade.omrscanner;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pass the printable Sheet-X test images through the same Y-plane path as the live camera. */
public class ScannerImageRegressionTest {
    private static final char[] KEY = "ABCDABCDABCDABCD".toCharArray();

    private static final class Frame {
        final int w, h;
        final byte[] plane;

        Frame(int w, int h) {
            this.w = w;
            this.h = h;
            this.plane = new byte[w * h];
        }

        OmrNativeCvEngine.GradeOutcome scan(String template, boolean rotate) {
            return OmrNativeCvEngine.analyzeNv21StreamFrame(plane, w, h, rotate, 0, KEY, template);
        }
    }

    private Frame read(int count, String variant) throws IOException {
        String name = "/sheetx/sheetx_" + count + "q-" + variant + (count == 16 ? ".a4" : "") + ".luma.gz";
        try (InputStream stream = getClass().getResourceAsStream(name)) {
            assertNotNull(name, stream);
            try (DataInputStream data = new DataInputStream(new GZIPInputStream(stream))) {
                Frame frame = new Frame(data.readInt(), data.readInt());
                data.readFully(frame.plane);
                assertEquals(-1, data.read());
                return frame;
            }
        }
    }

    private void assertClean(int count, OmrNativeCvEngine.GradeOutcome result) {
        assertEquals("SHEETX_" + count + "Q", result.detectedTemplateCode);
        assertEquals(6, result.fiducialsLocked);
        assertFalse(result.reviewReasons.toString(), result.needsReview);
        assertEquals("0033166", result.decodedStudentId);
        assertEquals(count, result.correctCount);
        assertEquals(count, result.questions.size());
        for (int i = 0; i < count; i++) assertEquals(String.valueOf(KEY[i]), result.questions.get(i).markedOptions);
    }

    @Test public void canonicalAndFullPageSheetsDecodeWithExplicitAndAutomaticLayouts() throws Exception {
        for (int count : new int[]{10, 16}) {
            Frame image = read(count, "clean");
            assertClean(count, image.scan("SHEETX_" + count + "Q", false));
            assertClean(count, image.scan("AUTO", false));
        }
    }

    @Test public void cameraRotationPreservesTheAnswerOrderAndLeadingZeros() throws Exception {
        Frame portrait = read(10, "clean");
        Frame sensor = new Frame(portrait.h, portrait.w);
        for (int y = 0; y < portrait.h; y++) {
            for (int x = 0; x < portrait.w; x++) {
                sensor.plane[(sensor.h - 1 - x) * sensor.w + y] = portrait.plane[y * portrait.w + x];
            }
        }
        assertClean(10, sensor.scan("SHEETX_10Q", true));
    }

    @Test public void mixedMarksAndDoubleBubbledIdAreKeptForReview() throws Exception {
        for (int count : new int[]{10, 16}) {
            Frame image = read(count, "review");
            for (String mode : new String[]{"SHEETX_" + count + "Q", "AUTO"}) {
                OmrNativeCvEngine.GradeOutcome result = image.scan(mode, false);
                assertEquals("SHEETX_" + count + "Q", result.detectedTemplateCode);
                assertEquals("?033166", result.decodedStudentId);
                assertEquals("A+B", result.questions.get(0).markedOptions);
                assertTrue(result.questions.get(1).isBlank);
                assertEquals("C+D", result.questions.get(2).markedOptions);
                for (int i = 3; i < count; i++) assertEquals(String.valueOf(KEY[i]), result.questions.get(i).markedOptions);
                assertTrue(result.reviewReasons.contains("MULTIPLE_MARKS"));
                assertTrue(result.reviewReasons.contains("BLANK_ANSWER"));
                assertTrue(result.reviewReasons.contains("UNRESOLVED_STUDENT_ID"));
                assertTrue(result.needsReview);
            }
        }
    }

    @Test public void blankPreprintedSheetsRequireReview() throws Exception {
        for (int count : new int[]{10, 16}) {
            Frame image = read(count, "blank");
            OmrNativeCvEngine.GradeOutcome result = image.scan("SHEETX_" + count + "Q", false);
            assertTrue(result.geometryValid);
            assertEquals("NO-ID", result.decodedStudentId);
            assertEquals(count, result.blankCount);
            assertTrue(result.needsReview);
            // A completely unmarked form gives AUTO little evidence to distinguish similar grids.
            assertTrue(image.scan("AUTO", false).needsReview);
        }
    }

    @Test public void removalOfAllSixRegistrationMarksInvalidatesCompletedSheet() throws Exception {
        Frame image = read(10, "clean");
        for (int cy : new int[]{64, 390, 716}) {
            for (int cx : new int[]{64, 536}) {
                for (int y = cy - 12; y <= cy + 12; y++) {
                    java.util.Arrays.fill(image.plane, y * image.w + cx - 12, y * image.w + cx + 13, (byte) 255);
                }
            }
        }
        OmrNativeCvEngine.GradeOutcome result = image.scan("SHEETX_10Q", false);
        assertEquals(0, result.fiducialsLocked);
        assertFalse(result.geometryValid);
        assertTrue(result.reviewReasons.contains("INVALID_GEOMETRY"));
    }

    @Test public void emptyOrTruncatedFrameCannotReplayLastStudent() throws Exception {
        Frame image = read(10, "clean");
        assertClean(10, image.scan("SHEETX_10Q", false));
        for (byte[] incomplete : new byte[][]{null, new byte[0], new byte[image.w * image.h - 1]}) {
            assertThrows(IllegalArgumentException.class, () -> OmrNativeCvEngine.analyzeNv21StreamFrame(
                incomplete, image.w, image.h, false, 0, KEY, "AUTO"));
        }
    }
}
