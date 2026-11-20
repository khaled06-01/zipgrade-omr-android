package com.zipgrade.omrscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity.IntentForResult;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CustomQuestionFlowTest {
    private SharedPreferences preferences() {
        return RuntimeEnvironment.getApplication()
            .getSharedPreferences("smart_exam_grader_v2", Context.MODE_PRIVATE);
    }

    private static <T extends View> List<T> views(View root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(views(group.getChildAt(i), type));
        }
        return result;
    }

    private static void click(MainActivity activity, String text) {
        for (Button button : views(activity.getWindow().getDecorView(), Button.class)) {
            if (text.contentEquals(button.getText())) {
                button.performClick();
                shadowOf(Looper.getMainLooper()).idle();
                return;
            }
        }
        fail("Missing button: " + text);
    }

    private static AlertDialog openPicker(MainActivity activity) {
        click(activity, "Create answer sheet");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static void confirmCount(AlertDialog dialog, String text) {
        EditText input = views(dialog.getWindow().getDecorView(), EditText.class).get(0);
        input.setText(text);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void createNonPresetSheetRetainsCountThroughDocumentPickerRecreation() throws Exception {
        String key = "BDCBADABCA";
        preferences().edit().putInt("q_count", 10).putString("template_mode", "SHEETX_10Q")
            .putString("master_key_50", key).putBoolean("has_custom_key", true).commit();
        ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup();
        AlertDialog dialog = openPicker(first.get());
        for (String invalid : new String[] {"", "0", "101"}) {
            confirmCount(dialog, invalid);
            assertTrue("Invalid count should stay in the picker", dialog.isShowing());
            assertNull(shadowOf(first.get()).getNextStartedActivityForResult());
        }
        confirmCount(dialog, "٣٧");
        IntentForResult request = shadowOf(first.get()).getNextStartedActivityForResult();
        assertNotNull("A valid Arabic-digit count should launch the document picker", request);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.getAction());
        assertEquals("application/pdf", request.intent.getType());
        assertTrue(request.intent.getStringExtra(Intent.EXTRA_TITLE).contains("37"));
        first.pause().stop().destroy();

        ActivityController<MainActivity> restarted = Robolectric.buildActivity(MainActivity.class).setup();
        File pdf = File.createTempFile("teacher-sheet-", ".pdf", restarted.get().getCacheDir());
        restarted.get().onActivityResult(request.requestCode, Activity.RESULT_OK,
            new Intent().setData(Uri.fromFile(pdf)));
        shadowOf(Looper.getMainLooper()).idle();
        byte[] header = new byte[5];
        try (FileInputStream input = new FileInputStream(pdf)) {
            assertEquals(header.length, input.read(header));
        }
        assertEquals("%PDF-", new String(header, StandardCharsets.US_ASCII));
        assertEquals(37, preferences().getInt("q_count", -1));
        assertEquals("CUSTOM_V1_37", preferences().getString("template_mode", ""));
        assertTrue(preferences().getString("master_key_50", "").startsWith(key));

        confirmCount(openPicker(restarted.get()), "82");
        IntentForResult canceled = shadowOf(restarted.get()).getNextStartedActivityForResult();
        restarted.get().onActivityResult(canceled.requestCode, Activity.RESULT_CANCELED, null);
        assertEquals(37, preferences().getInt("q_count", -1));
        assertEquals("CUSTOM_V1_37", preferences().getString("template_mode", ""));
        restarted.pause().stop().destroy();
    }

    private static OmrNativeCvEngine.GradeOutcome cleanReading(int count, char[] key) {
        return cleanReading(count, key, "CUSTOM_V1_" + count);
    }

    private static OmrNativeCvEngine.GradeOutcome cleanReading(int count, char[] key, String code) {
        String id = "0001037";
        StringBuilder signature = new StringBuilder(id + ":");
        List<OmrNativeCvEngine.QuestionResult> answers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String answer = String.valueOf(key[i]);
            answers.add(new OmrNativeCvEngine.QuestionResult(i + 1, answer, key[i], true,
                false, false, false, null, new float[4], null, null));
            signature.append(answer).append(',');
        }
        return new OmrNativeCvEngine.GradeOutcome(count, count, 0, 0, 0, 100, id,
            code, code, true, true, false, true,
            null, 6, 1, answers, null, null, 7, signature.toString(), null);
    }

    @Test public void autoIgnoresLegacyGuessUntilTeacherSelectsItsLayout() throws Exception {
        preferences().edit().putString("template_mode", "AUTO").putInt("q_count", 37)
            .putBoolean("has_custom_key", false).commit();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        char[] key = ReflectionHelpers.getField(activity, "masterKey");
        char[] originalKey = key.clone();
        char[] guessedAnswers = "DDDDDDDDDD".toCharArray();
        OmrNativeCvEngine.GradeOutcome guess = cleanReading(10, guessedAnswers, "SHEETX_10Q");
        ReflectionHelpers.callInstanceMethod(activity, "applyOutcomeToScoreUi",
            ReflectionHelpers.ClassParameter.from(OmrNativeCvEngine.GradeOutcome.class, guess),
            ReflectionHelpers.ClassParameter.from(boolean.class, true));
        TextView percent = ReflectionHelpers.getField(activity, "scorePercentText");
        TextView status = ReflectionHelpers.getField(activity, "scoreFractionText");
        TextView idLine = ReflectionHelpers.getField(activity, "studentIdText");
        assertEquals("—", percent.getText().toString());
        assertEquals(activity.getString(R.string.align_count_strip), status.getText().toString());
        assertEquals("", idLine.getText().toString());
        assertEquals(Integer.valueOf(37), ReflectionHelpers.getField(activity, "activeQuestionCount"));
        ReflectionHelpers.callInstanceMethod(activity, "copyCurrentPaperToMasterKey");
        assertArrayEquals(originalKey, key);
        click(activity, "Review / save grade");
        assertEquals(0, new JSONArray(preferences().getString("class_grades_v3", "[]")).length());

        ReflectionHelpers.callInstanceMethod(activity, "selectTemplateMode",
            ReflectionHelpers.ClassParameter.from(String.class, "SHEETX_10Q"),
            ReflectionHelpers.ClassParameter.from(int.class, 10),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        ReflectionHelpers.callInstanceMethod(activity, "applyOutcomeToScoreUi",
            ReflectionHelpers.ClassParameter.from(OmrNativeCvEngine.GradeOutcome.class, guess),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        assertEquals("100%", percent.getText().toString());
        assertTrue(idLine.getText().toString().contains("0001037"));
        assertEquals(Integer.valueOf(10), ReflectionHelpers.getField(activity, "activeQuestionCount"));
        controller.pause().stop().destroy();
    }

    @Test public void autoAcceptsCountFromPaperWhileExplicitDifferentCountBlocksSave() throws Exception {
        // Start with an edited ten-question key. AUTO must still handle a generated 37Q reading.
        String storedKey = "DDCBABCDAA";
        preferences().edit().putString("template_mode", "AUTO").putInt("q_count", 10)
            .putString("master_key_50", storedKey).putBoolean("has_custom_key", true).commit();
        ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup();
        char[] key = ReflectionHelpers.getField(first.get(), "masterKey");
        OmrNativeCvEngine.GradeOutcome reading = cleanReading(37, key);
        ReflectionHelpers.callInstanceMethod(first.get(), "applyOutcomeToScoreUi",
            ReflectionHelpers.ClassParameter.from(OmrNativeCvEngine.GradeOutcome.class, reading),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        assertEquals(Integer.valueOf(37), ReflectionHelpers.getField(first.get(), "activeQuestionCount"));
        TextView percent = ReflectionHelpers.getField(first.get(), "scorePercentText");
        TextView gradeBadge = ReflectionHelpers.getField(first.get(), "gradeBadgeText");
        TextView idLine = ReflectionHelpers.getField(first.get(), "studentIdText");
        ViewGroup breakdown = ReflectionHelpers.getField(first.get(), "questionBreakdownContainer");
        assertEquals("—", percent.getText().toString());
        assertEquals("", gradeBadge.getText().toString());
        assertFalse(idLine.getText().toString().contains("Mistakes"));
        assertEquals(1, breakdown.getChildCount()); // Only the key-confirmation instruction.
        click(first.get(), "Review / save grade");
        assertEquals(0, new JSONArray(preferences().getString("class_grades_v3", "[]")).length());
        click(first.get(), "Use this answer key");
        assertEquals("100%", percent.getText().toString());
        assertEquals(37, breakdown.getChildCount());
        click(first.get(), "Review / save grade");
        JSONArray saved = new JSONArray(preferences().getString("class_grades_v3", "[]"));
        assertEquals(1, saved.length());
        assertEquals(37, saved.getJSONObject(0).getInt("total"));
        first.pause().stop().destroy();

        preferences().edit().putString("template_mode", "CUSTOM_V1_37").putInt("q_count", 37).commit();
        ActivityController<MainActivity> second = Robolectric.buildActivity(MainActivity.class).setup();
        ReflectionHelpers.setField(second.get(), "lastOutcome", cleanReading(36, key));
        click(second.get(), "Review / save grade");
        assertEquals(saved.toString(), preferences().getString("class_grades_v3", ""));
        second.pause().stop().destroy();
    }
}
