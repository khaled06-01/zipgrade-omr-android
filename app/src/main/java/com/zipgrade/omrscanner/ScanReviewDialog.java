package com.zipgrade.omrscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ScanReviewDialog {
    private ScanReviewDialog() {}

    public static void show(Activity activity, OmrNativeCvEngine.GradeOutcome out, char[] key,
                            Consumer<ReviewedScan> onReviewed) {
        if (!out.geometryValid || !out.templateSelectionValid) {
            Toast.makeText(activity, "Check the layout and align the sheet markers, then scan again.", Toast.LENGTH_LONG).show();
            return;
        }
        final char[] keySnapshot = key.clone();
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);

        EditText id = new EditText(activity);
        id.setSingleLine();
        id.setInputType(InputType.TYPE_CLASS_NUMBER);
        id.setHint("Seven-digit ID");
        if (ScanCapturePolicy.hasCompleteStudentId(out.decodedStudentId)) id.setText(out.decodedStudentId);
        labeled(form, "Student ID on the paper (detected: " + out.decodedStudentId + ")", id);

        Map<Integer, Spinner> inputs = new LinkedHashMap<>();
        for (OmrNativeCvEngine.QuestionResult row : out.questions) {
            if (!row.needsReview) continue;
            String[] choices = {"Choose final answer", "Accept " + row.markedOptions, "A", "B", "C", "D", "Blank"};
            Spinner picker = new Spinner(activity);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, choices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            picker.setAdapter(adapter);
            picker.setMinimumHeight(padding * 3);
            labeled(form, "Q" + row.questionNumber + " • Detected " + row.markedOptions, picker);
            inputs.put(row.questionNumber, picker);
        }
        EditText reason = new EditText(activity);
        reason.setHint("What did you confirm or change?");
        labeled(form, "Review reason", reason);
        TextView error = new TextView(activity);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Review sheet").setView(scroll)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Confirm", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            Map<Integer, String> decisions = new LinkedHashMap<>();
            for (OmrNativeCvEngine.QuestionResult row : out.questions) {
                Spinner picker = inputs.get(row.questionNumber);
                if (picker == null) continue;
                int position = picker.getSelectedItemPosition();
                if (position == 0) {
                    error.setText("Choose a final answer for Q" + row.questionNumber);
                    picker.requestFocus();
                    return;
                }
                String chosen = position == 1 ? row.markedOptions : position == 6 ? "BLANK" : (String) picker.getSelectedItem();
                decisions.put(row.questionNumber, chosen);
            }
            ReviewedScan result;
            try {
                result = ReviewedScan.confirm(out, keySnapshot, asciiDigits(id.getText().toString()),
                    decisions, reason.getText().toString(), System.currentTimeMillis());
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                return;
            }
            dialog.dismiss();
            onReviewed.accept(result);
        }));
        dialog.show();
    }

    static String asciiDigits(String id) {
        StringBuilder result = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            int digit = Character.digit(id.charAt(i), 10);
            if (digit < 0) return id;
            result.append((char) ('0' + digit));
        }
        return result.toString();
    }

    private static void labeled(LinearLayout form, String label, View input) {
        TextView title = new TextView(form.getContext());
        title.setText(label);
        input.setId(View.generateViewId());
        title.setLabelFor(input.getId());
        form.addView(title);
        form.addView(input);
    }
}
