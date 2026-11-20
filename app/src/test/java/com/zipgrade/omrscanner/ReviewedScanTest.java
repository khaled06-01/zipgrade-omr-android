package com.zipgrade.omrscanner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedScanTest {
    private static final char[] KEY = {'A', 'B', 'C'};

    private static OmrNativeCvEngine.QuestionResult row(int n, String marks, String flag) {
        return new OmrNativeCvEngine.QuestionResult(n, marks, KEY[n - 1], marks.equals(String.valueOf(KEY[n - 1])),
            marks.contains("+"), marks.equals("BLANK"), flag != null, flag, new float[4], null, null);
    }

    private static OmrNativeCvEngine.GradeOutcome fixture(boolean clean, boolean validGeometry, boolean validLayout) {
        return new OmrNativeCvEngine.GradeOutcome(3, clean ? 3 : 1, 0, clean ? 0 : 1, clean ? 0 : 1,
            clean ? 100 : 33, clean ? "0001234" : "000?234", "Example", "SHEET_TEST", validLayout, true,
            false, validGeometry, null, validGeometry ? 6 : 0, 2,
            Arrays.asList(row(1, "A", null),
                row(2, clean ? "B" : "B+D", clean ? null : "MULTIPLE_MARKS"),
                row(3, clean ? "C" : "BLANK", clean ? null : "BLANK_ANSWER")),
            null, null, clean ? 7 : 6, clean ? "0001234:A,B,C," : "000?234:A,B+D,-,", null);
    }

    @Test public void reviewCorrectsIdentityAndAnswersWithoutChangingRawScan() {
        OmrNativeCvEngine.GradeOutcome raw = fixture(false, true, true);
        Map<Integer, String> answers = new HashMap<>();
        answers.put(2, "B"); answers.put(3, "BLANK");
        ReviewedScan result = ReviewedScan.confirm(raw, KEY, "0001234", answers, "Verified erasure on paper", 42L);
        assertEquals("0001234:A,B,-,", result.finalAnswerSignature());
        assertEquals("000?234", result.rawStudentId);
        assertEquals(2, result.correctCount);
        assertEquals(67, result.scorePercent);
        assertEquals("B+D", raw.questions.get(1).markedOptions);
        assertEquals(42, result.reviewedAtEpochMs);
    }

    @Test public void acceptingDetectedDoubleAndBlankGivesNoMarksForEither() {
        Map<Integer, String> answers = new HashMap<>();
        answers.put(2, "B+D"); answers.put(3, "BLANK");
        ReviewedScan result = ReviewedScan.confirm(fixture(false, true, true), KEY, "0001234", answers, "Checked paper", 42L);
        assertEquals(1, result.correctCount);
        assertEquals("0001234:A,B+D,-,", result.finalAnswerSignature());
    }

    @Test public void partialReviewCannotFinalize() {
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.confirm(fixture(false, true, true),
            KEY, "0001234", Collections.singletonMap(2, "B"), "Checked", 42L));
    }

    @Test public void cannotAutomaticallyAcceptFlaggedReadings() {
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.accepted(fixture(false, true, true), KEY));
    }

    @Test public void rejectsChangedKeyInvalidGeometryLayoutIdentityAndEmptyReason() {
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.accepted(fixture(true,true,true), "CBA".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.confirm(fixture(true,false,true), KEY, "0001234", Collections.emptyMap(), "Checked", 42L));
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.confirm(fixture(true,true,false), KEY, "0001234", Collections.emptyMap(), "Checked", 42L));
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.confirm(fixture(true,true,true), KEY, "000?234", Collections.emptyMap(), "Checked", 42L));
        assertThrows(IllegalArgumentException.class, () -> ReviewedScan.confirm(fixture(true,true,true), KEY, "0001234", Collections.emptyMap(), "   ", 42L));
    }

    @Test public void cleanSignatureMatchesScannerAndArabicInputPreservesZeros() {
        OmrNativeCvEngine.GradeOutcome raw = fixture(true, true, true);
        assertEquals(raw.answerSignature, ReviewedScan.accepted(raw, KEY).finalAnswerSignature());
        assertEquals("0001234", ScanReviewDialog.asciiDigits("٠٠٠١٢٣٤"));
    }
}
