import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.File;
import java.util.Locale;

/**
 * Regenerate the static Arabic labels as vector paths, with proper contextual shaping.
 * Use a JDK with java.desktop, and DejaVuSans.ttf as the file argument. Redirect stdout
 * to app/src/main/resources/com/zipgrade/omrscanner/custom-sheet-labels.pdfops.
 * All coordinates here are in the same A4 PDF points as CustomSheetPdf.
 */
public class GenerateSheetLabels {
    private static Font font;

    private static void path(String text, float right, float baseline, float size) {
        TextLayout layout = new TextLayout(text, font.deriveFont(size), new FontRenderContext(null, true, true));
        PathIterator points = layout.getOutline(AffineTransform.getTranslateInstance(
                right - layout.getAdvance(), baseline)).getPathIterator(null);
        double x = 0, y = 0, startX = 0, startY = 0;
        double[] a = new double[6];
        while (!points.isDone()) {
            switch (points.currentSegment(a)) {
                case PathIterator.SEG_MOVETO:
                    System.out.printf("%.2f %.2f m%n", a[0], a[1]);
                    x = startX = a[0]; y = startY = a[1]; break;
                case PathIterator.SEG_LINETO:
                    System.out.printf("%.2f %.2f l%n", a[0], a[1]);
                    x = a[0]; y = a[1]; break;
                case PathIterator.SEG_QUADTO:
                    System.out.printf("%.2f %.2f %.2f %.2f %.2f %.2f c%n",
                        (x + 2 * a[0]) / 3, (y + 2 * a[1]) / 3,
                        (a[2] + 2 * a[0]) / 3, (a[3] + 2 * a[1]) / 3, a[2], a[3]);
                    x = a[2]; y = a[3]; break;
                case PathIterator.SEG_CUBICTO:
                    System.out.printf("%.2f %.2f %.2f %.2f %.2f %.2f c%n", a[0], a[1], a[2], a[3], a[4], a[5]);
                    x = a[4]; y = a[5]; break;
                case PathIterator.SEG_CLOSE:
                    System.out.println("h"); x = startX; y = startY; break;
                default: throw new IllegalStateException();
            }
            points.next();
        }
        System.out.println("f");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        font = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));
        System.out.println("% Arabic headings for the custom V1 A4 answer sheet. DejaVu Sans.");
        path("ورقة الإجابة", 548, 43, 21);
        path("عدد الأسئلة", 548, 66, 11);
        path("اسم الطالب", 275.5f, 142, 10);
        path("الصف", 275.5f, 191, 10);
        path("المادة", 275.5f, 240, 10);
        path("رقم الطالب", 460.5f, 116, 10);
        path("الإجابات", 496.5f, 280, 11);
        path("ظلّل دائرة واحدة لكل سؤال ولكل خانة من رقم الطالب.", 548, 776, 8.3f);
        path("اطبع على ورق A4 بالحجم الفعلي (١٠٠٪).", 548, 792, 9);
    }
}
