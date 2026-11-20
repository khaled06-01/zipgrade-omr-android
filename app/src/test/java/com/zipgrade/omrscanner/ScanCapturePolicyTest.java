package com.zipgrade.omrscanner;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScanCapturePolicyTest {
    @Test public void savingRequiresAMatchingExplicitPilotLayout() {
        assertTrue(ScanCapturePolicy.isPilotLayout("SHEETX_16Q", "SHEETX_16Q"));
        assertTrue(ScanCapturePolicy.isPilotLayout("SHEETX_10Q", "SHEETX_10Q"));
        assertFalse(ScanCapturePolicy.isPilotLayout("AUTO", "SHEETX_10Q"));
        assertFalse(ScanCapturePolicy.isPilotLayout("SHEETX_10Q", "CAMERA_10Q"));
        assertFalse(ScanCapturePolicy.isPilotLayout("SHEETX_100Q", "SHEETX_100Q"));
    }

    @Test public void geometryLockWithoutStableAnswersNeverAccepts() {
        ScanCapturePolicy policy = new ScanCapturePolicy();
        for (int i = 0; i < 30; i++) assertFalse(policy.observe("id:" + i % 2, true));
    }

    @Test public void threeMatchingEligibleFramesAreRequired() {
        ScanCapturePolicy policy = new ScanCapturePolicy();
        assertFalse(policy.observe("paper-a", true));
        assertFalse(policy.observe("paper-a", true));
        assertTrue(policy.observe("paper-a", true));
        assertTrue(policy.observe("paper-a", true));
    }

    @Test public void changingPaperDoesNotRequireLosingGeometryLock() {
        ScanCapturePolicy policy = new ScanCapturePolicy();
        for (int i = 0; i < 3; i++) policy.observe("paper-a", true);
        assertFalse(policy.observe("paper-b", true));
        assertFalse(policy.observe("paper-b", true));
        assertTrue(policy.observe("paper-b", true));
    }

    @Test public void uncertainOrUnlockedFrameBreaksAgreement() {
        ScanCapturePolicy policy = new ScanCapturePolicy();
        policy.observe("a", true);
        policy.observe("a", true);
        assertFalse(policy.observe("a", false));
        assertFalse(policy.observe("a", true));
        assertFalse(policy.observe("a", true));
        assertTrue(policy.observe("a", true));
    }

    @Test public void resetInvalidatesPendingCapture() {
        ScanCapturePolicy policy = new ScanCapturePolicy();
        policy.observe("a", true);
        policy.observe("a", true);
        policy.reset();
        assertFalse(policy.observe("a", true));
    }

    @Test public void legacySavedKeyIsNeverAssumedToBeDemoKey() {
        assertTrue(ScanCapturePolicy.restoreCustomKeyFlag(true, null));
        assertFalse(ScanCapturePolicy.restoreCustomKeyFlag(false, null));
        assertFalse(ScanCapturePolicy.restoreCustomKeyFlag(true, false));
        assertTrue(ScanCapturePolicy.restoreCustomKeyFlag(true, true));
    }

    @Test public void identifiersPreserveZerosAndRejectPartialOrNonNumericValues() {
        assertTrue(ScanCapturePolicy.hasCompleteStudentId("0001234"));
        for (String bad : new String[] {null, "", "NO-ID", "123?567", "123456", "12345678", "١٢٣٤٥٦٧"}) {
            assertFalse(ScanCapturePolicy.hasCompleteStudentId(bad));
        }
    }

    @Test public void dedupContextIncludesTemplateKeyCountAndUnmodifiedId() {
        char[] key = "ABCDABCDABCDABCD".toCharArray();
        String original = ScanCapturePolicy.context("SHEETX_10Q", 10, key, "0001234");
        assertTrue(original.endsWith("|0001234"));
        assertEquals(original, ScanCapturePolicy.context("SHEETX_10Q", 10, key.clone(), "0001234"));
        assertNotEquals(original, ScanCapturePolicy.context("CAMERA_10Q", 10, key, "0001234"));
        assertNotEquals(original, ScanCapturePolicy.context("SHEETX_10Q", 9, key, "0001234"));
        key[0] = 'D';
        assertNotEquals(original, ScanCapturePolicy.context("SHEETX_10Q", 10, key, "0001234"));
    }
}
