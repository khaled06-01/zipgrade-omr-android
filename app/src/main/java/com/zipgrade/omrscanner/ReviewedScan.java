package com.zipgrade.omrscanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Immutable local assessment. Keeps what the detector saw even when the operator changes it. */
public final class ReviewedScan {
    public final String rawStudentId, studentId, templateCode, reason, keySnapshot;
    public final long reviewedAtEpochMs;
    public final int correctCount, totalQuestions, scorePercent;
    private final List<Answer> answers;
    private final List<String> reviewReasons;

    private static final class Answer {
        final String raw, value, flag;
        final float[] densities;

        Answer(OmrNativeCvEngine.QuestionResult row, String value) {
            raw = row.markedOptions;
            this.value = value;
            flag = row.reviewReason;
            densities = row.densities == null ? new float[0] : row.densities.clone();
        }
    }

    public static ReviewedScan accepted(OmrNativeCvEngine.GradeOutcome out, char[] key) {
        if (out.needsReview) throw new IllegalArgumentException("Resolve the flagged readings first");
        return new ReviewedScan(out, key, out.decodedStudentId, Collections.emptyMap(), "", System.currentTimeMillis());
    }

    static ReviewedScan confirm(OmrNativeCvEngine.GradeOutcome out, char[] key, String id,
                                Map<Integer, String> decisions, String reason, long now) {
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("Enter a reason for this review");
        return new ReviewedScan(out, key, id, decisions, reason.trim(), now);
    }

    private ReviewedScan(OmrNativeCvEngine.GradeOutcome out, char[] key, String id,
                         Map<Integer, String> decisions, String explanation, long now) {
        if (!out.geometryValid || !out.templateSelectionValid)
            throw new IllegalArgumentException("Scan again with the correct layout and visible markers");
        if (!ScanCapturePolicy.hasCompleteStudentId(id))
            throw new IllegalArgumentException("Enter all seven digits of the student ID");
        if (out.totalQuestions < 1 || key == null || key.length < out.totalQuestions || out.questions.size() != out.totalQuestions)
            throw new IllegalArgumentException("Incomplete scan or answer key");
        rawStudentId = out.decodedStudentId;
        studentId = id;
        templateCode = out.detectedTemplateCode;
        keySnapshot = new String(key, 0, out.totalQuestions);
        reason = explanation;
        reviewedAtEpochMs = now;
        totalQuestions = out.totalQuestions;
        reviewReasons = Collections.unmodifiableList(new ArrayList<>(out.reviewReasons));
        List<Answer> result = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < totalQuestions; i++) {
            OmrNativeCvEngine.QuestionResult row = out.questions.get(i);
            if (row.questionNumber != i + 1 || key[i] != row.correctOption || key[i] < 'A' || key[i] > 'D')
                throw new IllegalArgumentException("The answer key changed. Scan again.");
            String value = row.markedOptions;
            if (row.needsReview) {
                value = decisions.get(row.questionNumber);
                if (value == null) throw new IllegalArgumentException("Choose a final answer for question " + row.questionNumber);
                if (!value.equals(row.markedOptions) && !value.equals("BLANK") && !value.matches("[A-D]"))
                    throw new IllegalArgumentException("Invalid final answer");
            }
            result.add(new Answer(row, value));
            if (value.equals(String.valueOf(key[i]))) correct++;
        }
        correctCount = correct;
        scorePercent = Math.round(correct * 100f / totalQuestions);
        answers = Collections.unmodifiableList(result);
    }

    public String finalAnswerSignature() {
        StringBuilder signature = new StringBuilder(studentId).append(':');
        for (Answer answer : answers)
            signature.append("BLANK".equals(answer.value) ? "-" : answer.value).append(',');
        return signature.toString();
    }

    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("schema", 1).put("template", templateCode).put("key", keySnapshot);
            json.put("rawId", rawStudentId).put("studentId", studentId);
            json.put("reason", reason).put("recordedAtMs", reviewedAtEpochMs).put("reviewFlags", new JSONArray(reviewReasons));
            json.put("correct", correctCount).put("total", totalQuestions).put("percent", scorePercent);
            JSONArray rows = new JSONArray();
            for (Answer answer : answers) {
                JSONArray densities = new JSONArray();
                for (float density : answer.densities)
                    densities.put(Float.isFinite(density) ? (Object) (double) density : JSONObject.NULL);
                rows.put(new JSONObject().put("raw", answer.raw).put("final", answer.value)
                    .put("flag", answer.flag).put("densities", densities));
            }
            return json.put("answers", rows).toString();
        } catch (JSONException ex) {
            throw new IllegalStateException("Could not record the scan", ex);
        }
    }

    public static String describe(String json) {
        try {
            JSONObject value = new JSONObject(json);
            StringBuilder text = new StringBuilder("Student ID: ").append(value.getString("studentId"))
                .append("\nRead from sheet: ").append(value.optString("rawId", "unknown"))
                .append("\nTemplate: ").append(value.getString("template"))
                .append("\nScore: ").append(value.getInt("correct")).append('/').append(value.getInt("total"))
                .append("\nRecorded: ").append(java.text.DateFormat.getDateTimeInstance().format(new Date(value.getLong("recordedAtMs"))));
            if (!value.optString("reason").isEmpty()) text.append("\nReview reason: ").append(value.getString("reason"));
            JSONArray rows = value.getJSONArray("answers");
            String key = value.getString("key");
            text.append("\n\nQuestion    Detected → Final    Key");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                text.append("\n").append(i + 1).append("    ").append(row.getString("raw"))
                    .append(" → ").append(row.getString("final")).append("    ").append(key.charAt(i));
            }
            return text.toString();
        } catch (JSONException | IndexOutOfBoundsException ex) {
            return "This scan’s saved details could not be read.";
        }
    }
}
