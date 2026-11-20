package com.zipgrade.omrscanner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** Desktop integration check. Renders the actual Android export with Poppler before scanning. */
public final class CheckPrintedSheets {
    private static final String STUDENT = "0033166";
    private static final int DPI = 108;
    private static final char[] KEY = "ABCD".repeat(25).toCharArray();

    private static byte[] luminance(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        byte[] plane = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            plane[i] = (byte) ((77 * ((rgb >> 16) & 255) + 150 * ((rgb >> 8) & 255) + 29 * (rgb & 255)) >> 8);
        }
        return plane;
    }

    private static OmrNativeCvEngine.GradeOutcome scan(BufferedImage image, int override, String mode) {
        return OmrNativeCvEngine.analyzeNv21StreamFrame(luminance(image), image.getWidth(), image.getHeight(),
            false, override, KEY, mode);
    }

    private static Graphics2D canonicalPen(BufferedImage page) {
        Graphics2D pen = page.createGraphics();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pen.scale(DPI / 72d, DPI / 72d);
        pen.translate(CustomSheetPdf.OFFSET_X, CustomSheetPdf.OFFSET_Y);
        pen.scale(CustomSheetPdf.SCALE, CustomSheetPdf.SCALE);
        return pen;
    }

    private static void fillCircle(Graphics2D pen, float x, float y, float radius) {
        pen.fill(new Ellipse2D.Float(x-radius, y-radius, 2*radius, 2*radius));
    }

    private static void markSheet(BufferedImage image, int count) {
        Graphics2D pen = canonicalPen(image);
        try {
            pen.setColor(Color.BLACK);
            for (int i = 0; i < STUDENT.length(); i++) {
                fillCircle(pen, CustomSheetLayout.idX(i), CustomSheetLayout.idY(STUDENT.charAt(i) - '0'),
                    0.88f * CustomSheetLayout.ID_RADIUS);
            }
            for (int q = 1; q <= count; q++) {
                fillCircle(pen, CustomSheetLayout.answerX(count, q, (q - 1) % 4), CustomSheetLayout.answerY(count, q),
                    0.88f * CustomSheetLayout.ANSWER_RADIUS);
            }
        } finally {
            pen.dispose();
        }
    }

    private static void check(boolean pass, int count, String message) {
        if (!pass) throw new AssertionError(count + "Q: " + message);
    }

    private static void checkRejected(OmrNativeCvEngine.GradeOutcome result, int count, String reason) {
        check(!result.templateSelectionValid && result.needsReview && result.questions.isEmpty(), count, reason);
    }

    private static void saveLuma(BufferedImage image, Path destination) throws IOException {
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(destination)))) {
            data.writeInt(image.getWidth());
            data.writeInt(image.getHeight());
            data.write(luminance(image));
        }
    }

    private static void run(Path dir, int count) throws Exception {
        String name = "custom-" + count + "q";
        Path pdf = dir.resolve(name + ".pdf");
        try (OutputStream output = Files.newOutputStream(pdf)) {
            CustomSheetPdf.write(output, count);
        }
        Process process = new ProcessBuilder("pdftoppm", "-singlefile", "-png", "-r", Integer.toString(DPI),
            pdf.toString(), dir.resolve(name+"-blank").toString()).inheritIO().start();
        check(process.waitFor() == 0, count, "PDF rasterization failed");
        Path png = dir.resolve(name+"-blank.png");
        BufferedImage page = ImageIO.read(png.toFile());
        OmrNativeCvEngine.GradeOutcome blank = scan(page, 0, "AUTO");
        String code = CustomSheetLayout.code(count);
        check(code.equals(blank.detectedTemplateCode) && blank.geometryValid && blank.templateSelectionValid,
            count, "blank PDF decoded as " + blank.detectedTemplateCode + " / " + blank.detectedFormName);
        check(blank.blankCount == count && blank.totalQuestions == count && blank.needsReview &&
            blank.decodedStudentId.equals("NO-ID"), count, "blank paper produced marks or a student ID");
        boolean keep = count == 37;
        if (keep) saveLuma(page, dir.resolve(name+"-blank.luma.gz"));

        markSheet(page, count);
        OmrNativeCvEngine.GradeOutcome filled = scan(page, 0, "AUTO");
        check(code.equals(filled.detectedTemplateCode) && filled.totalQuestions == count && !filled.needsReview,
            count, "filled scan failed: " + filled.detectedFormName + " / " + filled.reviewReasons);
        check(filled.correctCount == count && filled.decodedStudentId.equals(STUDENT), count,
            "score " + filled.correctCount + " student " + filled.decodedStudentId);
        for (int i = 0; i < count; i++) {
            check(filled.questions.get(i).markedOptions.equals(Character.toString(KEY[i])), count, "wrong Q" + (i + 1));
        }
        if (keep) {
            ImageIO.write(page, "PNG", dir.resolve(name+"-filled.png").toFile());
            saveLuma(page, dir.resolve(name+"-filled.luma.gz"));
            check(scan(page, 16, "AUTO").totalQuestions == count, count, "AUTO retained a stale count");
            checkRejected(scan(page, 17, CustomSheetLayout.code(17)), count, "explicit mismatched count accepted");
            checkRejected(scan(page, 16, "SHEETX_16Q"), count, "legacy layout accepted for coded page");
            // Flip one count bit while leaving both guards, geometry, ID, and all answers intact.
            Graphics2D pen = canonicalPen(page);
            try {
                int cell = 15;
                pen.setColor(CustomSheetLayout.isDark(count, cell) ? Color.WHITE : Color.BLACK);
                float s = CustomSheetLayout.CELL_SIZE;
                pen.fill(new Rectangle2D.Float(CustomSheetLayout.cellX(cell) - s/2f,
                    CustomSheetLayout.cellY(cell) - s/2f, s, s));
            } finally { pen.dispose(); }
            checkRejected(scan(page, 0, "AUTO"), count, "damaged count code accepted");
            ImageIO.write(page, "PNG", dir.resolve(name+"-damaged.png").toFile());
        } else {
            Files.delete(png);
            Files.delete(pdf);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("Specify an empty directory for generated files");
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        // Refuse to overwrite any previous exports in the supplied directory.
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            if (files.findAny().isPresent()) throw new IllegalArgumentException("Output directory must be empty");
        }
        if (args.length == 1) {
            for (int count = 1; count <= 100; count++) run(dir, count);
            System.out.println("Verified every count 1-100 on blank and filled generated PDFs.");
        } else {
            for (int i = 1; i < args.length; i++) run(dir, Integer.parseInt(args[i]));
            System.out.println("Verified requested counts on blank and filled generated PDFs.");
        }
    }
}
