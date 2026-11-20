package com.zipgrade.omrscanner;

import org.junit.Test;

import static org.junit.Assert.*;

public class OmrDecisionRulesTest {
    @Test public void darkIdTieAndNearTieCannotChooseAStudent() {
        assertEquals('?', OmrDecisionRules.decodeIdDigit(4, 0.9f, 0.9f));
        assertEquals('?', OmrDecisionRules.decodeIdDigit(4, 0.9f, 0.86f));
        assertEquals('4', OmrDecisionRules.decodeIdDigit(4, 0.9f, 0.1f));
    }

    @Test public void blankAndInvalidIdEvidenceRemainUnresolved() {
        assertEquals('?', OmrDecisionRules.decodeIdDigit(0, 0f, 0f));
        assertEquals('?', OmrDecisionRules.decodeIdDigit(3, 0.17f, 0.01f));
        assertEquals('?', OmrDecisionRules.decodeIdDigit(3, Float.NaN, 0f));
    }

    @Test public void decodedIdRemainsTextWithLeadingZeroes() {
        StringBuilder id = new StringBuilder();
        for (int digit : new int[] {0, 0, 1, 2, 3, 4, 5}) {
            id.append(OmrDecisionRules.decodeIdDigit(digit, 0.8f, 0.05f));
        }
        assertEquals("0012345", id.toString());
    }

    @Test public void oneClearMarkRetainsTheExistingClassification() {
        OmrDecisionRules.MarkDecision result = marks(0.02f, 0.8f, 0.03f, 0.01f);
        assertEquals("B", result.markedOptions);
        assertFalse(result.isBlank);
        assertFalse(result.isMultipleMark);
        assertFalse(result.needsReview);
    }

    @Test public void doubleMarksArePreservedAndRequireReview() {
        OmrDecisionRules.MarkDecision result = marks(0.8f, 0.75f, 0.02f, 0.01f);
        assertEquals("A+B", result.markedOptions);
        assertTrue(result.isMultipleMark);
        assertTrue(result.needsReview);
        assertEquals("MULTIPLE_MARKS", result.reviewReason);
    }

    @Test public void allDarkOptionsCannotBecomeBlank() {
        OmrDecisionRules.MarkDecision result = marks(0.8f, 0.8f, 0.8f, 0.8f);
        assertEquals("A+B+C+D", result.markedOptions);
        assertFalse(result.isBlank);
        assertTrue(result.isMultipleMark);
        assertTrue(result.needsReview);
    }

    @Test public void blankAnswersRequireTeacherReview() {
        OmrDecisionRules.MarkDecision result = marks(0.01f, 0.02f, 0.01f, 0.01f);
        assertTrue(result.isBlank);
        assertEquals("", result.markedOptions);
        assertTrue(result.needsReview);
        assertEquals("BLANK_ANSWER", result.reviewReason);
    }

    @Test public void faintSuggestionAndFaintTieCannotFinalizeAutomatically() {
        OmrDecisionRules.MarkDecision suggestion = marks(0.25f, 0.02f, 0.03f, 0.01f);
        assertEquals("A", suggestion.markedOptions);
        assertEquals("UNCERTAIN_MARKS", suggestion.reviewReason);
        OmrDecisionRules.MarkDecision tie = marks(0.25f, 0.25f, 0.02f, 0.01f);
        assertEquals("", tie.markedOptions);
        assertTrue(tie.needsReview);
        assertEquals("UNCERTAIN_MARKS", tie.reviewReason);
    }

    @Test public void invalidDensitiesCannotProduceAnAcceptedAnswer() {
        assertTrue(marks(Float.NaN, 0f, 0f, 0f).needsReview);
        assertTrue(marks(Float.POSITIVE_INFINITY, 0f, 0f, 0f).needsReview);
        assertTrue(marks(-0.1f, 0f, 0f, 0f).needsReview);
        assertTrue(OmrDecisionRules.classifyMarks(new float[0]).needsReview);
    }

    @Test public void explicitTemplateDoesNotSelectAnotherLayoutWithTheSameCount() {
        assertTrue(OmrDecisionRules.templateMatches("SHEETX_16Q", "SHEETX_16Q", 16, 16));
        assertFalse(OmrDecisionRules.templateMatches("SHEETX_16Q", "CAMERA_16Q", 16, 16));
        assertTrue(OmrDecisionRules.templateMatches("CAMERA_16Q", "CAMERA_16Q", 16, 16));
        assertFalse(OmrDecisionRules.templateMatches("CAMERA_16Q", "SHEETX_16Q", 16, 16));
    }

    @Test public void legacyNamesResolveOnlyToTheirSpecificLayout() {
        assertEquals("CAMERA_10Q", OmrDecisionRules.explicitTemplateCode("10Q"));
        assertEquals("CAMERA_16Q", OmrDecisionRules.explicitTemplateCode("TPL_16Q_ID7"));
        assertEquals("SHEETX_50Q", OmrDecisionRules.explicitTemplateCode("TPL_STD_50Q"));
        assertFalse(OmrDecisionRules.templateMatches("UNKNOWN", "SHEETX_16Q", 16, 16));
    }

    @Test public void automaticSelectionHonorsRequestedQuestionCount() {
        assertTrue(OmrDecisionRules.templateMatches("AUTO", "SHEETX_16Q", 16, 16));
        assertFalse(OmrDecisionRules.templateMatches("AUTO", "SHEETX_50Q", 50, 16));
        assertTrue(OmrDecisionRules.templateMatches("GALLERY", "SHEETX_50Q", 50, 0));
        assertFalse(OmrDecisionRules.templateMatches("AUTO", "SHEETX_16Q", 16, 17));
    }

    @Test public void failedOrPartialGeometryDoesNotReportALock() {
        assertFalse(OmrDecisionRules.geometryValid(false, 0));
        assertFalse(OmrDecisionRules.geometryValid(false, 4));
        assertFalse(OmrDecisionRules.geometryValid(true, 0));
        assertFalse(OmrDecisionRules.geometryValid(true, 3));
        assertTrue(OmrDecisionRules.geometryValid(true, 4));
        assertTrue(OmrDecisionRules.geometryValid(true, 6));
    }

    private static OmrDecisionRules.MarkDecision marks(float a, float b, float c, float d) {
        return OmrDecisionRules.classifyMarks(new float[] {a, b, c, d});
    }
}
