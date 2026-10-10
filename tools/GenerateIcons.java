import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the MessagePigeon icon (an animated-film style pigeon, head on, holding a text file in its beak) and writes
 * src/main/resources/icons/app-N.png plus launcher/app.ico. Run from the repository root:
 *     java tools/GenerateIcons.java
 */
public class GenerateIcons {
    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        Path png = Path.of("src/main/resources/icons");
        Files.createDirectories(png);
        List<byte[]> pngBytes = new ArrayList<>();
        for (int s : SIZES) {
            BufferedImage img = render(s);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            pngBytes.add(out.toByteArray());
            Files.write(png.resolve("app-" + s + ".png"), out.toByteArray());
        }
        Files.write(Path.of("launcher/app.ico"), ico(pngBytes));
        System.out.println("Wrote " + SIZES.length + " PNGs and launcher/app.ico");
    }

    private static final Color EYE = new Color(0xE8812B);
    // A soft, animated-film look: smooth gradients for volume, thin tinted outlines, big glossy eyes.
    private static final Color RIM = new Color(0x56709A);         // thin tinted outline, not black
    private static final Color FEATHER_LIGHT = new Color(0xF5F9FE);
    private static final Color FEATHER_MID = new Color(0xC8D6EA);
    private static final Color FEATHER_DARK = new Color(0x8DA6C9);
    private static final Color BEAK_LIGHT = new Color(0xFFD071);
    private static final Color BEAK_DARK = new Color(0xF0841C);
    private static final Color INK = new Color(0x12141C);

    static BufferedImage render(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 32;        // too little room for fine detail

        // Tile: a glowing sky behind the character
        RoundRectangle2D tile = new RoundRectangle2D.Double(8, 8, 240, 240, 58, 58);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(128, 92), 200f, new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0x93D3FF), new Color(0x4A8FE0), new Color(0x183A7C)}));
        g.fill(tile);
        if (!small) {
            sparkle(g, 40, 58, 12);
            sparkle(g, 218, 70, 9);
            sparkle(g, 30, 168, 7);
        }

        // Shoulders, cut off by the edge of the tile
        Shape oldClip = g.getClip();
        g.setClip(tile);
        Shape shoulders = new Ellipse2D.Double(20, 180, 216, 140);
        g.setPaint(new GradientPaint(0, 180, new Color(0xB4C5DC), 0, 250, new Color(0x7F94B6)));
        g.fill(shoulders);
        if (!small) {
            stroke(g, new Color(RIM.getRed(), RIM.getGreen(), RIM.getBlue(), 170), 3.5, shoulders);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(128, 238), 56f, new float[] {0f, 1f},
                    new Color[] {new Color(255, 255, 255, 120), new Color(255, 255, 255, 0)}));
            g.fill(new Ellipse2D.Double(60, 196, 136, 90));
            // feather scallops on the chest
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(RIM.getRed(), RIM.getGreen(), RIM.getBlue(), 70));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 5 - row % 2; col++) {
                    double cx = 74 + col * 27 + (row % 2) * 13.5, cy = 214 + row * 14;
                    g.draw(new Arc2D.Double(cx - 11, cy - 7, 22, 16, 200, 140, Arc2D.OPEN));
                }
            }
        }
        g.setClip(oldClip);

        // Iridescent collar where the neck meets the shoulders: the most pigeon-like feature
        Shape collar = new Ellipse2D.Double(40, 142, 176, 76);
        g.setPaint(new LinearGradientPaint(40, 0, 216, 0, new float[] {0f, 0.3f, 0.5f, 0.7f, 1f},
                new Color[] {new Color(0x2FC79A), new Color(0x57DDB0), new Color(0xA98BF0), new Color(0x57DDB0), new Color(0x2FC79A)}));
        g.fill(collar);
        if (!small) {
            stroke(g, new Color(0x2A7F78), 3, collar);
            g.setPaint(new GradientPaint(0, 150, new Color(255, 255, 255, 120), 0, 176, new Color(255, 255, 255, 0)));
            g.fill(new Ellipse2D.Double(58, 150, 140, 30));
        }

        // Head: round, lit from the upper left
        Shape head = new Ellipse2D.Double(52, 32, 152, 140);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(108, 74), 130f, new float[] {0f, 0.5f, 1f},
                new Color[] {FEATHER_LIGHT, FEATHER_MID, FEATHER_DARK}));
        g.fill(head);
        if (!small) {
            stroke(g, new Color(RIM.getRed(), RIM.getGreen(), RIM.getBlue(), 190), 3.5, head);
            // soft rim of light along the upper left edge
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 120));
            g.draw(new Arc2D.Double(60, 40, 136, 124, 105, 85, Arc2D.OPEN));
            // a curly crest, like a cowlick
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x6F89AD));
            Path2D curl = new Path2D.Double();
            curl.moveTo(124, 38);
            curl.curveTo(118, 16, 142, 6, 148, 20);
            curl.curveTo(151, 28, 142, 32, 139, 26);
            g.draw(curl);
            g.draw(path(110, 42, 102, 24));
            g.draw(path(146, 42, 160, 26));
            // pink cheeks
            for (double cx : new double[] {76, 180}) {
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, 134), 26f, new float[] {0f, 1f},
                        new Color[] {new Color(255, 120, 150, 190), new Color(255, 120, 150, 0)}));
                g.fill(new Ellipse2D.Double(cx - 26, 108, 52, 52));
            }
        }

        // Big glossy eyes, looking right at you
        for (double ex : new double[] {98, 158}) {
            eye(g, ex, 96, small);
        }
        if (!small) {
            // friendly raised eyebrows
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x58729C));
            Path2D l = new Path2D.Double(); l.moveTo(76, 66); l.quadTo(94, 54, 116, 62);
            Path2D r = new Path2D.Double(); r.moveTo(140, 62); r.quadTo(162, 54, 180, 66);
            g.draw(l);
            g.draw(r);
        }

        // The text file, held by its top edge in the beak and tilted a little
        Graphics2D d = (Graphics2D) g.create();
        d.rotate(Math.toRadians(-7), 134, 190);
        Path2D page = path(92, 142, 148, 142, 176, 170, 176, 236, 92, 236);
        page.closePath();
        if (!small) {
            softShadow(d, page, 5, 8);
        }
        d.setPaint(new GradientPaint(0, 142, Color.WHITE, 0, 236, new Color(0xD9E4F5)));
        d.fill(page);
        if (!small) {
            stroke(d, new Color(0x6B84A8), 3.5, page);
        }
        Path2D fold = path(148, 142, 148, 170, 176, 170);
        fold.closePath();
        d.setPaint(new GradientPaint(148, 142, new Color(0xF2F6FC), 176, 170, new Color(0xBBCADF)));
        d.fill(fold);
        if (!small) {
            stroke(d, new Color(0x6B84A8), 2.5, fold);
        }
        bar(d, 106, 184, 58, small ? 12 : 9, new Color(0x5BA0F2), new Color(0x2C69C6));
        bar(d, 106, 201, 48, small ? 12 : 9, new Color(0xB4C4DC), new Color(0x9CB0CE));
        bar(d, 106, 218, 38, small ? 12 : 9, new Color(0xB4C4DC), new Color(0x9CB0CE));
        d.dispose();

        // The beak, in front of the page's top edge, with a pale cere above it
        if (!small) {
            Ellipse2D cere = new Ellipse2D.Double(112, 110, 32, 20);
            g.setPaint(new GradientPaint(0, 110, Color.WHITE, 0, 130, new Color(0xDCE5F1)));
            g.fill(cere);
            stroke(g, new Color(RIM.getRed(), RIM.getGreen(), RIM.getBlue(), 150), 2.5, cere);
            g.setColor(new Color(0x5B6D88));
            g.fill(new Ellipse2D.Double(120, 117, 4.5, 4.5));
            g.fill(new Ellipse2D.Double(132, 117, 4.5, 4.5));
        }
        Path2D beak = new Path2D.Double();
        beak.moveTo(108, 124);
        beak.quadTo(128, 114, 148, 124);
        beak.quadTo(142, 150, 128, 162);
        beak.quadTo(114, 150, 108, 124);
        beak.closePath();
        g.setPaint(new GradientPaint(0, 118, BEAK_LIGHT, 0, 162, BEAK_DARK));
        g.fill(beak);
        if (!small) {
            stroke(g, new Color(0xB35F10), 3, beak);
            g.setColor(new Color(255, 255, 255, 140));
            g.fill(rot(new Ellipse2D.Double(116, 122, 14, 7), -15, 123, 125));       // a little shine
        }

        g.dispose();
        return img;
    }

    /** One big, glossy, animated-style eye: shaded white, amber iris, deep pupil, two highlights, a lid line. */
    private static void eye(Graphics2D g, double cx, double cy, boolean small) {
        double rx = small ? 21 : 22, ry = small ? 24 : 27;
        Ellipse2D white = new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
        g.setPaint(new GradientPaint(0, (float) (cy - ry), new Color(0xFFFFFF), 0, (float) (cy + ry), new Color(0xE4EBF5)));
        g.fill(white);
        Shape old = g.getClip();
        g.clip(white);
        if (!small) {
            // the upper lid casts a soft shadow onto the eye
            g.setPaint(new GradientPaint(0, (float) (cy - ry), new Color(60, 70, 100, 110), 0, (float) (cy - ry + 18),
                    new Color(60, 70, 100, 0)));
            g.fill(new Rectangle2D.Double(cx - rx, cy - ry, rx * 2, 20));
        }
        double ir = small ? 17 : 18, icy = cy + 2;
        Ellipse2D iris = new Ellipse2D.Double(cx - ir, icy - ir, ir * 2, ir * 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, icy + 3), (float) ir, new float[] {0f, 0.55f, 1f},
                new Color[] {new Color(0xFFD982), new Color(0xF28B22), new Color(0x8F4310)}));
        g.fill(iris);
        double pr = small ? 9 : 9.5;
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(cx - pr, icy - pr, pr * 2, pr * 2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - 9, icy - 12, 9, 9));                 // big highlight, upper left
        if (!small) {
            g.setColor(new Color(255, 255, 255, 215));
            g.fill(new Ellipse2D.Double(cx + 4, icy + 5, 5, 5));              // small highlight, lower right
            g.setColor(new Color(255, 230, 170, 90));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(cx - ir + 4, icy - ir + 4, ir * 2 - 8, ir * 2 - 8, 200, 140, Arc2D.OPEN)); // lit lower edge
        }
        g.setClip(old);
        if (!small) {
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x3A4A6B));
            g.draw(new Arc2D.Double(cx - rx, cy - ry, rx * 2, ry * 2, 20, 140, Arc2D.OPEN));   // upper lid line
        }
    }

    private static void sparkle(Graphics2D g, double x, double y, double r) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) (r * 1.8), new float[] {0f, 1f},
                new Color[] {new Color(255, 255, 255, 120), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(x - r * 1.8, y - r * 1.8, r * 3.6, r * 3.6));
        Path2D star = new Path2D.Double();
        star.moveTo(x, y - r);
        star.quadTo(x, y, x + r, y);
        star.quadTo(x, y, x, y + r);
        star.quadTo(x, y, x - r, y);
        star.quadTo(x, y, x, y - r);
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(star);
    }

    /** A soft shadow built from a few widening, faint copies of the shape. */
    private static void softShadow(Graphics2D g, Shape s, double dx, double dy) {
        Shape moved = AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(s);
        for (int i = 6; i >= 1; i--) {
            g.setColor(new Color(10, 25, 70, 16));
            g.setStroke(new BasicStroke(i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(moved);
            g.fill(moved);
        }
    }

    private static void stroke(Graphics2D g, Color c, double width, Shape s) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    private static void bar(Graphics2D g, double x, double y, double w, double h, Color top, Color bottom) {
        g.setPaint(new GradientPaint(0, (float) y, top, 0, (float) (y + h), bottom));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    private static Shape rot(Shape s, double degrees, double cx, double cy) {
        return AffineTransform.getRotateInstance(Math.toRadians(degrees), cx, cy).createTransformedShape(s);
    }

    /** A multi-resolution .ico whose images are PNGs (supported by Windows Vista and later). */
    static byte[] ico(List<byte[]> pngs) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(new byte[]{0, 0, 1, 0});                       // reserved, type = icon
        out.write(le16(pngs.size()));
        int offset = 6 + 16 * pngs.size();
        for (int i = 0; i < pngs.size(); i++) {
            int s = SIZES[i];
            out.writeByte(s == 256 ? 0 : s);                      // width (0 means 256)
            out.writeByte(s == 256 ? 0 : s);                      // height
            out.writeByte(0);                                     // palette
            out.writeByte(0);                                     // reserved
            out.write(le16(1));                                   // planes
            out.write(le16(32));                                  // bits per pixel
            out.write(le32(pngs.get(i).length));
            out.write(le32(offset));
            offset += pngs.get(i).length;
        }
        for (byte[] p : pngs) {
            out.write(p);
        }
        return bos.toByteArray();
    }

    static byte[] le16(int v) { return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array(); }
    static byte[] le32(int v) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(); }
}
