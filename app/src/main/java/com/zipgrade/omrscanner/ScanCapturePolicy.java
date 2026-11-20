package com.zipgrade.omrscanner;

/** Small, Android-independent rules for accepting a repeated camera observation. */
public final class ScanCapturePolicy {
    private String candidate = "";
    private int matchingFrames;

    /** Geometry alone is insufficient: identity, template, key and answers must agree. */
    public boolean observe(String signature, boolean eligible) {
        if (!eligible || signature == null || signature.isEmpty()) {
            reset();
            return false;
        }
        if (!signature.equals(candidate)) {
            candidate = signature;
            matchingFrames = 1;
        } else {
            matchingFrames = Math.min(3, matchingFrames + 1);
        }
        return matchingFrames >= 3;
    }

    public void reset() {
        candidate = "";
        matchingFrames = 0;
    }

    public static boolean isPilotLayout(String selected, String detected) {
        return ("SHEETX_10Q".equals(selected) || "SHEETX_16Q".equals(selected)) && selected.equals(detected);
    }

    public static boolean canSaveLayout(String selected, String detected) {
        if (CustomSheetLayout.parseCode(detected) > 0) {
            return "AUTO".equals(selected) || detected.equals(selected);
        }
        return isPilotLayout(selected, detected);
    }

    public static boolean hasCompleteStudentId(String value) {
        if (value == null || value.length() != 7) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }

    /** Preserve older saved keys too: absence of the old flag must never imply a demo key. */
    public static boolean restoreCustomKeyFlag(boolean hasSavedKey, Boolean explicitFlag) {
        return explicitFlag != null ? explicitFlag : hasSavedKey;
    }

    public static String context(String templateCode, int questions, char[] key, String studentId) {
        if (!hasCompleteStudentId(studentId)) throw new IllegalArgumentException("Unresolved student ID");
        if (templateCode == null || templateCode.isEmpty() || questions < 1 || questions > key.length) {
            throw new IllegalArgumentException("Invalid scan context");
        }
        return templateCode + "|" + questions + "|" + new String(key, 0, questions) + "|" + studentId;
    }
}
