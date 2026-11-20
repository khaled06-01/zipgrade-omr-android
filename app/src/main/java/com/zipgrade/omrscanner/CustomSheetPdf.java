package com.zipgrade.omrscanner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

/** Single-page A4 vector sheets. Machine-readable positions come only from CustomSheetLayout. */
public final class CustomSheetPdf {
    // Page dimensions are PostScript points (1/72 inch).
    public static final int PAGE_WIDTH = 595, PAGE_HEIGHT = 842;
    public static final float SCALE = 1f;
    public static final float OFFSET_X = (PAGE_WIDTH - 600f * SCALE) / 2f;
    public static final float OFFSET_Y = (PAGE_HEIGHT - 780f * SCALE) / 2f;

    private CustomSheetPdf() {}

    /** Writes one blank sheet. The caller owns the destination stream. */
    public static void write(OutputStream output, int count) throws IOException {
        if (output == null) throw new IllegalArgumentException("A PDF destination is required");
        if (!CustomSheetLayout.validCount(count)) throw new IllegalArgumentException("Choose 1 to 100 questions");

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        // Convert PDF's bottom-up page coordinates to top-down drawing coordinates.
        ascii(content, "q 1 0 0 -1 0 " + PAGE_HEIGHT + " cm 0.14 g\n");
        try (InputStream labels = CustomSheetPdf.class.getResourceAsStream("custom-sheet-labels.pdfops")) {
            if (labels == null) throw new IOException("Missing printed sheet labels");
            byte[] buffer = new byte[4096];
            for (int n; (n = labels.read(buffer)) != -1; ) content.write(buffer, 0, n);
        }
        StringBuilder page = new StringBuilder();
        label(page, "Answer sheet", 47f, 43f, 22, true);
        label(page, "Questions: " + count, 48f, 66f, 11, false);
        label(page, Integer.toString(count), 466f, 66f, 11, true);
        label(page, "Fill one circle per question and per ID column.", 48f, 776f, 8.5f, false);
        label(page, "A4 - Print at actual size (100%)", 48f, 792f, 9f, false);
        page.append("q ").append(SCALE).append(" 0 0 ").append(SCALE).append(' ')
            .append(OFFSET_X).append(' ').append(OFFSET_Y).append(" cm 0 g\n");
        for (int i = 0; i < 6; i++) {
            square(page, CustomSheetLayout.fiducialX(i), CustomSheetLayout.fiducialY(i),
                    2f * CustomSheetLayout.MARKER_HALF_SIZE);
        }
        for (int i = 0; i < CustomSheetLayout.CELL_COUNT; i++) {
            if (CustomSheetLayout.isDark(count, i)) {
                square(page, CustomSheetLayout.cellX(i), CustomSheetLayout.cellY(i), CustomSheetLayout.CELL_SIZE);
            }
        }
        page.append("0.14 g 0.43 G 0.75 w\n");
        writeInField(page, "Student name", 111f);
        writeInField(page, "Class", 160f);
        writeInField(page, "Subject", 209f);
        drawId(page);
        drawQuestions(page, count);
        page.append("Q Q\n");
        ascii(content, page.toString());
        writeDocument(output, content.toByteArray());
    }

    private static void writeInField(StringBuilder p, String name, float y) {
        label(p, name, 84f, y, 9, true);
        p.append("84 ").append(y + 21f).append(" m 278 ").append(y + 21f).append(" l S\n");
    }

    private static void drawId(StringBuilder p) {
        float left = CustomSheetLayout.idX(0);
        label(p, "Student ID", left - 8f, 85, 9, true);
        for (int digit = 0; digit < 10; digit++) {
            float y = CustomSheetLayout.idY(digit);
            label(p, Integer.toString(digit), left - 17f, y + 3f, 9, false);
            for (int col = 0; col < 7; col++) {
                circle(p, CustomSheetLayout.idX(col), y, CustomSheetLayout.ID_RADIUS);
            }
        }
    }

    private static void drawQuestions(StringBuilder p, int count) {
        label(p, "Answers", 84, 249, 11, true);
        for (int q = 1; q <= count; q++) {
            float y = CustomSheetLayout.answerY(count, q);
            boolean columnStart = q == 1 || y < CustomSheetLayout.answerY(count, q - 1);
            String number = Integer.toString(q);
            label(p, number, CustomSheetLayout.answerX(count, q, 0) - 12f - number.length() * 5f,
                    y + 3f, 9, false);
            for (int option = 0; option < 4; option++) {
                float x = CustomSheetLayout.answerX(count, q, option);
                if (columnStart) label(p, String.valueOf((char) ('A' + option)), x - 3.2f, y - 10f, 9, true);
                circle(p, x, y, CustomSheetLayout.ANSWER_RADIUS);
            }
        }
    }

    private static void label(StringBuilder p, String text, float x, float y, float size, boolean bold) {
        p.append("BT /").append(bold ? "Bold" : "Regular").append(' ').append(size)
            .append(" Tf 1 0 0 -1 ").append(x).append(' ').append(y).append(" Tm (")
            .append(text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")).append(") Tj ET\n");
    }

    private static void square(StringBuilder p, float x, float y, float size) {
        p.append(x - size / 2).append(' ').append(y - size / 2).append(' ')
            .append(size).append(' ').append(size).append(" re f\n");
    }

    private static void circle(StringBuilder p, float x, float y, float r) {
        // Draw a unit circle in a translated coordinate space. The line width stays constant.
        float k = r * 0.55228475f;
        p.append("q 1 0 0 1 ").append(x).append(' ').append(y).append(" cm ")
            .append(r).append(" 0 m ").append(r).append(' ').append(k).append(' ')
            .append(k).append(' ').append(r).append(" 0 ").append(r).append(" c ")
            .append(-k).append(' ').append(r).append(' ').append(-r).append(' ').append(k).append(' ').append(-r).append(" 0 c ")
            .append(-r).append(' ').append(-k).append(' ').append(-k).append(' ').append(-r).append(" 0 ").append(-r).append(" c ")
            .append(k).append(' ').append(-r).append(' ').append(r).append(' ').append(-k).append(' ').append(r).append(" 0 c h S Q\n");
    }

    // Minimal, deterministic one-page PDF: standard Helvetica fonts, a compressed content stream,
    // and a byte-offset cross-reference table. Buffering one page also supports non-seekable URIs.
    private static void writeDocument(OutputStream destination, byte[] content) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(compressed)) {
            deflate.write(content);
        }
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT
                + "] /Resources << /Font << /Regular 4 0 R /Bold 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"
        };
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        ascii(pdf, "%PDF-1.4\n");
        int[] offsets = new int[7];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = pdf.size();
            ascii(pdf, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        offsets[6] = pdf.size();
        ascii(pdf, "6 0 obj\n<< /Length " + compressed.size() + " /Filter /FlateDecode >>\nstream\n");
        compressed.writeTo(pdf);
        ascii(pdf, "\nendstream\nendobj\n");
        int xref = pdf.size();
        ascii(pdf, "xref\n0 7\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) ascii(pdf, String.format(Locale.US, "%010d 00000 n \n", offsets[i]));
        ascii(pdf, "trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        pdf.writeTo(destination);
    }

    private static void ascii(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }
}
