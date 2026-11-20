package com.zipgrade.omrscanner;

/** Android-independent decisions; thresholds match the existing detector, not a calibration. */
public final class OmrDecisionRules {
    private OmrDecisionRules() {}

    public static final class MarkDecision {
        public final String markedOptions;
        public final boolean isBlank;
        public final boolean isMultipleMark;
        public final boolean needsReview;
        public final String reviewReason;

        private MarkDecision(String markedOptions, String reviewReason) {
            this.markedOptions = markedOptions;
            this.isBlank = markedOptions.isEmpty();
            this.isMultipleMark = markedOptions.contains("+");
            this.needsReview = reviewReason != null;
            this.reviewReason = reviewReason;
        }
    }

    /** A dark ID bubble still needs separation from the runner-up. */
    public static char decodeIdDigit(int bestDigit, float bestDensity, float secondDensity) {
        if (bestDigit < 0 || bestDigit > 9 || !validDensity(bestDensity)
                || !validDensity(secondDensity)) return '?';
        return bestDensity >= 0.18f && bestDensity - secondDensity >= 0.07f
                ? (char) ('0' + bestDigit) : '?';
    }

    public static MarkDecision classifyMarks(float[] densities) {
        if (densities == null || densities.length != 4) {
            return new MarkDecision("", "INVALID_MARK_DENSITIES");
        }
        float max = 0f, second = 0f, min = 1f;
        int bestIndex = 0;
        for (int i = 0; i < densities.length; i++) {
            float density = densities[i];
            if (!validDensity(density)) return new MarkDecision("", "INVALID_MARK_DENSITIES");
            min = Math.min(min, density);
            if (density > max) {
                second = max;
                max = density;
                bestIndex = i;
            } else if (density > second) {
                second = density;
            }
        }

        // There is no unmarked baseline when every option is dark. Never call it blank.
        // Uniform shading can produce this too, so the result always requires review.
        if (min >= 0.34f) return new MarkDecision("A+B+C+D", "MULTIPLE_MARKS");

        StringBuilder marked = new StringBuilder();
        for (int i = 0; i < densities.length; i++) {
            if (densities[i] >= 0.34f && densities[i] - min >= 0.14f
                    && densities[i] >= max * 0.58f) {
                if (marked.length() > 0) marked.append('+');
                marked.append((char) ('A' + i));
            }
        }
        if (marked.length() > 1) return new MarkDecision(marked.toString(), "MULTIPLE_MARKS");
        if (marked.length() == 1) return new MarkDecision(marked.toString(), null);

        // Preserve the legacy faint-mark suggestion but do not silently finalize it.
        if (max >= 0.18f && max - second >= 0.08f && max >= min * 2f) {
            return new MarkDecision(String.valueOf((char) ('A' + bestIndex)), "UNCERTAIN_MARKS");
        }
        return new MarkDecision("", max >= 0.18f ? "UNCERTAIN_MARKS" : "BLANK_ANSWER");
    }

    private static boolean validDensity(float density) {
        return !Float.isNaN(density) && !Float.isInfinite(density) && density >= 0f && density <= 1f;
    }

    /** null means heuristic selection; unknown explicit names remain explicit and cannot match. */
    public static String explicitTemplateCode(String hint) {
        if (hint == null || "AUTO".equals(hint) || "CAMERA".equals(hint)
                || "GALLERY".equals(hint) || "CUSTOM".equals(hint)) return null;
        if ("10Q".equals(hint) || "TPL_10Q_ID7".equals(hint)) return "CAMERA_10Q";
        if ("16Q".equals(hint) || "TPL_16Q_ID7".equals(hint)) return "CAMERA_16Q";
        if (hint.startsWith("TPL_STD_")) return "SHEETX_" + hint.substring("TPL_STD_".length());
        return hint;
    }

    public static boolean templateMatches(String hint, String candidateCode,
            int candidateQuestionCount, int requestedQuestionCount) {
        String explicitCode = explicitTemplateCode(hint);
        if (explicitCode != null) return explicitCode.equals(candidateCode);
        return requestedQuestionCount <= 0 || candidateQuestionCount == requestedQuestionCount;
    }

    public static boolean geometryValid(boolean locked, int lockedCount) {
        return locked && (lockedCount == 4 || lockedCount == 6);
    }
}
