package com.zipgrade.omrscanner;

import android.app.AlertDialog;
import android.os.Looper;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GradebookFlowTest {
    private static final char[] KEY = "DCBADCBAAB".toCharArray();

    private SharedPreferences preferences() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("smart_exam_grader_v2", Context.MODE_PRIVATE);
    }

    private void seedLegacyPreferences() {
        assertTrue(preferences().edit().putString("master_key_50", new String(KEY))
            .putInt("q_count", 10).putString("template_mode", "SHEETX_10Q")
            .putString("class_grades_v2", "Earlier student|0000009|8|10|80|11:00").commit());
    }

    private OmrNativeCvEngine.GradeOutcome reading(boolean clean) {
        List<OmrNativeCvEngine.QuestionResult> rows = new ArrayList<>();
        String id = clean ? "0001234" : "?001234";
        StringBuilder signature = new StringBuilder(id).append(':');
        for (int i = 0; i < KEY.length; i++) {
            boolean uncertain = !clean && i == 0;
            String raw = uncertain ? "B+D" : Character.toString(KEY[i]);
            rows.add(new OmrNativeCvEngine.QuestionResult(i + 1, raw, KEY[i], !uncertain, uncertain, false,
                uncertain, uncertain ? "MULTIPLE_MARKS" : null, new float[4], null, null));
            signature.append(raw).append(',');
        }
        return new OmrNativeCvEngine.GradeOutcome(10, clean ? 10 : 9, 0, clean ? 0 : 1, 0, clean ? 100 : 90,
            id, "Sheet-X 10Q", "SHEETX_10Q", true, true, true, true, null, 6, 1, rows,
            null, null, clean ? 7 : 6, signature.toString(), null);
    }

    private void saveThroughButton(MainActivity activity, OmrNativeCvEngine.GradeOutcome scan) {
        // Camera recognition is exercised separately with real image fixtures. Inject its output,
        // then drive the same visible save control, review dialog and storage as a teacher.
        ReflectionHelpers.setField(activity, "lastOutcome", scan);
        Button save = descendants(activity.getWindow().getDecorView(), Button.class).stream()
            .filter(button -> button.getText().toString().equals("Review / save grade")).findFirst().orElseThrow();
        save.performClick();
    }

    private static <T extends View> List<T> descendants(View root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) found.addAll(descendants(group.getChildAt(i), type));
        }
        return found;
    }

    @Test public void flaggedSheetRequiresExplicitReviewAndSurvivesRestartWithoutAnotherGrade() throws Exception {
        seedLegacyPreferences();
        ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup();
        saveThroughButton(first.get(), reading(false));
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        shadowOf(Looper.getMainLooper()).idle();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertFalse(preferences().contains("class_grades_v3"));
        View root = dialog.getWindow().getDecorView();
        List<EditText> edits = descendants(root, EditText.class);
        edits.get(0).setText("٠٠٠١٢٣٤");
        edits.get(1).setText("Compared with original paper");
        List<Spinner> choices = descendants(root, Spinner.class);
        assertEquals(1, choices.size());
        choices.get(0).setSelection(5); // D
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(descendants(root, TextView.class).stream().map(v -> v.getText().toString()).collect(java.util.stream.Collectors.toList()).toString(), dialog.isShowing());

        JSONArray saved = new JSONArray(preferences().getString("class_grades_v3", "[]"));
        assertEquals(2, saved.length()); // includes the legacy summary
        JSONObject grade = saved.getJSONObject(0);
        JSONObject evidence = new JSONObject(grade.getString("evidence"));
        assertEquals("0001234", grade.getString("id"));
        assertEquals(10, grade.getInt("correct"));
        assertEquals("?001234", evidence.getString("rawId"));
        assertEquals("Compared with original paper", evidence.getString("reason"));
        assertEquals("B+D", evidence.getJSONArray("answers").getJSONObject(0).getString("raw"));
        assertEquals("D", evidence.getJSONArray("answers").getJSONObject(0).getString("final"));
        assertEquals("0000009", saved.getJSONObject(1).getString("id"));
        assertEquals("", saved.getJSONObject(1).getString("evidence"));
        first.pause().stop().destroy();

        ActivityController<MainActivity> second = Robolectric.buildActivity(MainActivity.class).setup();
        assertTrue((Boolean) ReflectionHelpers.getField(second.get(), "hasCustomUserKey"));
        // The same paper with a now-clear first answer and ID matches its persisted resolved signature.
        saveThroughButton(second.get(), reading(true));
        assertEquals(saved.toString(), preferences().getString("class_grades_v3", ""));
        assertTrue(preferences().getString("master_key_50", "").startsWith(new String(KEY)));
        second.pause().stop().destroy();
    }
}
