package com.messagepigeon;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/**
 * Small vector icons for menu items. They are drawn in code on a 16 x 16 grid, so they stay sharp at any display
 * scaling, and they fade when the item they belong to is disabled.
 */
final class MenuIcons {
    enum Glyph {
        NEW_PROFILE, OPEN_QUEUE, CONNECT, DISCONNECT, CLOSE_TAB, EXIT, REFRESH, INSERT_MESSAGE, ADD_FILE, ADD_FOLDER,
        EXPORT, EXPORT_SELECTED, IMPORT, TRASH, TRASH_ALL, HELP, ABOUT, EDIT, COPY, VIEW, SELECT_ALL, CLEAR_HISTORY, LIBRARY
    }

    private static final Color INK = new Color(0x3B4A5E);
    private static final Color BLUE = new Color(0x2F76C8);
    private static final Color DARK_BLUE = new Color(0x1F4E9E);
    private static final Color ORANGE = new Color(0xF6A93B);
    private static final Color GREEN = new Color(0x2E9E4F);
    private static final Color RED = new Color(0xD64541);
    private static final Color GRAY = new Color(0x8A94A3);
    private static final Color PAPER = new Color(0xFFFFFF);
    private static final int SIZE = 16;

    private MenuIcons() {}

    /** The icon at menu size (16 pixels). */
    static Icon of(Glyph glyph) {
        return of(glyph, SIZE);
    }

    /** The icon at any size; it is drawn as vectors, so it stays sharp. */
    static Icon of(Glyph glyph, int size) {
        return make(glyph, size, false);
    }

    /**
     * A permanently faded menu-size icon. Menu items need it as their disabled icon: the look and feel only fades
     * image icons by itself, and would otherwise draw nothing for a disabled item.
     */
    static Icon faded(Glyph glyph) {
        return make(glyph, SIZE, true);
    }

    private static Icon make(Glyph glyph, int size, boolean alwaysFaded) {
        final double scale = size / (double) SIZE;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g2.translate(x, y);
                    g2.scale(scale, scale);
                    boolean disabled = alwaysFaded || (c instanceof AbstractButton b && !b.isEnabled());
                    if (disabled) {
                        g2.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                    }
                    paint(g2, glyph);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** Draws a glyph on the 16 x 16 grid whose top-left is the graphics origin. */
    static void paint(Graphics2D g, Glyph glyph) {
        switch (glyph) {
            case NEW_PROFILE -> { server(g); plusBadge(g); }
            case OPEN_QUEUE -> rows(g);
            case CONNECT -> plug(g, GREEN, false);
            case DISCONNECT -> plug(g, GRAY, true);
            case CLOSE_TAB -> closeTab(g);
            case EXIT -> exit(g);
            case REFRESH -> refresh(g);
            case INSERT_MESSAGE -> { typedNote(g); plusBadge(g); }
            case ADD_FILE -> { page(g, 2, 1); plusBadge(g); }
            case ADD_FOLDER -> { folder(g); plusBadge(g); }
            case EXPORT -> { tray(g); arrow(g, true, BLUE); }
            case EXPORT_SELECTED -> { tray(g); arrow(g, true, BLUE); fill(g, ORANGE, rr(5.5, 11.2, 5, 1.8, 1)); }
            case IMPORT -> { tray(g); arrow(g, false, GREEN); }
            case TRASH -> trash(g, RED, 0);
            case TRASH_ALL -> { trash(g, GRAY, -2.5); trash(g, RED, 1); }
            case HELP -> badgeText(g, DARK_BLUE, "?");
            case ABOUT -> badgeText(g, BLUE, "i");
            case EDIT -> pencil(g);
            case COPY -> copy(g);
            case VIEW -> eye(g);
            case SELECT_ALL -> selectAll(g);
            case CLEAR_HISTORY -> clearHistory(g);
            case LIBRARY -> library(g);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    private static void fill(Graphics2D g, Color c, Shape s) {
        g.setColor(c);
        g.fill(s);
    }

    private static void stroke(Graphics2D g, Color c, double w, Shape s) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    /** A small green circle with a white plus, bottom right: "new". */
    private static void plusBadge(Graphics2D g) {
        fill(g, GREEN, new Ellipse2D.Double(8, 8, 8, 8));
        stroke(g, PAPER, 1.5, path(12, 9.8, 12, 14.2));
        stroke(g, PAPER, 1.5, path(9.8, 12, 14.2, 12));
    }

    private static void server(Graphics2D g) {
        fill(g, DARK_BLUE, rr(1.5, 2, 10, 10, 2));
        stroke(g, PAPER, 1.2, path(3.5, 5.2, 9.5, 5.2));
        stroke(g, PAPER, 1.2, path(3.5, 8.2, 9.5, 8.2));
        fill(g, ORANGE, new Ellipse2D.Double(3.3, 9.6, 1.6, 1.6));
    }

    private static void rows(Graphics2D g) {
        fill(g, ORANGE, rr(2, 2, 12, 3.4, 1.4));
        fill(g, BLUE, rr(2, 6.3, 12, 3.4, 1.4));
        fill(g, GRAY, rr(2, 10.6, 12, 3.4, 1.4));
    }

    private static void plug(Graphics2D g, Color body, boolean broken) {
        stroke(g, INK, 1.5, path(6, 1.5, 6, 5.5));
        stroke(g, INK, 1.5, path(10, 1.5, 10, 5.5));
        fill(g, body, rr(3.5, 5, 9, 5.5, 2));
        stroke(g, INK, 1.5, path(8, 10.5, 8, 14.5));
        if (broken) {
            stroke(g, RED, 1.9, path(9.5, 9.5, 14.5, 14.5));
            stroke(g, RED, 1.9, path(14.5, 9.5, 9.5, 14.5));
        }
    }

    private static void closeTab(Graphics2D g) {
        fill(g, GRAY, rr(1.5, 2, 6, 3, 1));
        fill(g, PAPER, rr(1.5, 4, 13, 10, 1.5));
        stroke(g, GRAY, 1.3, rr(1.5, 4, 13, 10, 1.5));
        stroke(g, RED, 1.6, path(5.5, 7, 10.5, 12));
        stroke(g, RED, 1.6, path(10.5, 7, 5.5, 12));
    }

    private static void exit(Graphics2D g) {
        stroke(g, INK, 1.5, path(7.5, 2, 2.5, 2, 2.5, 14, 7.5, 14));
        stroke(g, RED, 1.7, path(6, 8, 14, 8));
        stroke(g, RED, 1.7, path(11, 5, 14, 8, 11, 11));
    }

    private static void refresh(Graphics2D g) {
        stroke(g, BLUE, 1.9, new Arc2D.Double(2.5, 2.5, 11, 11, 40, 280, Arc2D.OPEN));
        Path2D head = path(8.6, 0.8, 14.8, 3.4, 9.6, 6.6);
        head.closePath();
        fill(g, BLUE, head);
    }

    /** A note with a line of typed text and a text caret: "type a message here". */
    private static void typedNote(Graphics2D g) {
        fill(g, new Color(0xFFF4CF), rr(1.5, 2.5, 11.5, 9.5, 1.8));
        stroke(g, new Color(0xC27A12), 1.2, rr(1.5, 2.5, 11.5, 9.5, 1.8));
        stroke(g, INK, 1.3, path(3.6, 5.6, 9.6, 5.6));   // a full line of text
        stroke(g, INK, 1.3, path(3.6, 8.6, 5.6, 8.6));   // a second, still being typed
        stroke(g, BLUE, 1.5, path(7, 7.2, 7, 10.2));     // the caret
    }

    private static void page(Graphics2D g, double x, double y) {
        Path2D p = path(x, y, x + 6.5, y, x + 10, y + 3.5, x + 10, y + 14, x, y + 14);
        p.closePath();
        fill(g, PAPER, p);
        stroke(g, INK, 1.3, p);
        stroke(g, INK, 1.1, path(x + 6.5, y, x + 6.5, y + 3.5, x + 10, y + 3.5));
        stroke(g, GRAY, 1.1, path(x + 2, y + 6.5, x + 8, y + 6.5));
        stroke(g, GRAY, 1.1, path(x + 2, y + 9, x + 6, y + 9));
    }

    private static void folder(Graphics2D g) {
        fill(g, ORANGE, rr(1, 3, 6, 3.5, 1));
        fill(g, ORANGE, rr(1, 4.8, 13, 8.7, 1.6));
        stroke(g, new Color(0xC27A12), 1.0, rr(1, 4.8, 13, 8.7, 1.6));
    }

    private static void tray(Graphics2D g) {
        stroke(g, INK, 1.6, path(2.5, 9.5, 2.5, 13.5, 13.5, 13.5, 13.5, 9.5));
    }

    private static void arrow(Graphics2D g, boolean up, Color c) {
        if (up) {
            stroke(g, c, 1.8, path(8, 10.5, 8, 2.3));
            stroke(g, c, 1.8, path(4.7, 5.4, 8, 2.1, 11.3, 5.4));
        } else {
            stroke(g, c, 1.8, path(8, 1.5, 8, 9.7));
            stroke(g, c, 1.8, path(4.7, 6.6, 8, 9.9, 11.3, 6.6));
        }
    }

    private static void trash(Graphics2D g, Color c, double dx) {
        Graphics2D t = (Graphics2D) g.create();
        t.translate(dx, 0);
        fill(t, c, rr(6, 1.3, 4, 2, 0.8));
        stroke(t, c, 1.7, path(2.7, 3.9, 13.3, 3.9));
        Path2D body = path(4, 5, 12, 5, 11.2, 14.4, 4.8, 14.4);
        body.closePath();
        fill(t, c, body);
        if (c == RED) {
            stroke(t, PAPER, 1.1, path(6.6, 6.7, 6.8, 12.6));
            stroke(t, PAPER, 1.1, path(9.4, 6.7, 9.2, 12.6));
        }
        t.dispose();
    }

    private static void badgeText(Graphics2D g, Color c, String text) {
        fill(g, c, new Ellipse2D.Double(1, 1, 14, 14));
        g.setColor(PAPER);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, 8 - fm.stringWidth(text) / 2f, 8 + (fm.getAscent() - fm.getDescent()) / 2f);
    }

    private static void pencil(Graphics2D g) {
        Path2D body = path(3, 13, 3.6, 10, 11, 2.6, 13.4, 5, 6, 12.4);
        body.closePath();
        fill(g, ORANGE, body);
        stroke(g, INK, 1.2, body);
        fill(g, INK, path(3, 13, 3.6, 10, 6, 12.4));
    }

    private static void copy(Graphics2D g) {
        fill(g, PAPER, rr(2, 2, 8, 9, 1.4));
        stroke(g, GRAY, 1.3, rr(2, 2, 8, 9, 1.4));
        fill(g, PAPER, rr(6, 5, 8, 9, 1.4));
        stroke(g, DARK_BLUE, 1.3, rr(6, 5, 8, 9, 1.4));
        stroke(g, BLUE, 1.0, path(8, 8.5, 12, 8.5));
        stroke(g, BLUE, 1.0, path(8, 10.8, 11, 10.8));
    }

    private static void eye(Graphics2D g) {
        Path2D lens = new Path2D.Double();
        lens.moveTo(1, 8);
        lens.quadTo(8, 0.5, 15, 8);
        lens.quadTo(8, 15.5, 1, 8);
        lens.closePath();
        fill(g, PAPER, lens);
        stroke(g, DARK_BLUE, 1.3, lens);
        fill(g, DARK_BLUE, new Ellipse2D.Double(5.5, 5.5, 5, 5));
        fill(g, PAPER, new Ellipse2D.Double(7.2, 6.6, 1.4, 1.4));
    }

    /** A clock (history) with a red cross badge: "forget". */
    private static void clearHistory(Graphics2D g) {
        fill(g, PAPER, new Ellipse2D.Double(1.5, 1.5, 11, 11));
        stroke(g, DARK_BLUE, 1.4, new Ellipse2D.Double(1.5, 1.5, 11, 11));
        stroke(g, DARK_BLUE, 1.3, path(7, 4, 7, 7, 9.2, 8.4));
        fill(g, RED, new Ellipse2D.Double(8, 8, 8, 8));
        stroke(g, PAPER, 1.4, path(10.2, 10.2, 13.8, 13.8));
        stroke(g, PAPER, 1.4, path(13.8, 10.2, 10.2, 13.8));
    }

    /** A jar with a label: "the library file". */
    private static void library(Graphics2D g) {
        fill(g, GRAY, rr(4, 1.5, 8, 3.2, 1));
        fill(g, new Color(0xCFE2F7), rr(3, 4.2, 10, 10.3, 2.2));
        stroke(g, DARK_BLUE, 1.2, rr(3, 4.2, 10, 10.3, 2.2));
        fill(g, DARK_BLUE, rr(5, 7.2, 6, 4.2, 1));
        stroke(g, PAPER, 1, path(6.2, 9.3, 9.8, 9.3));
    }

    private static void selectAll(Graphics2D g) {
        g.setColor(BLUE);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {2f, 1.6f}, 0f));
        g.draw(rr(1.5, 1.5, 13, 13, 1.5));
        fill(g, BLUE, rr(4, 4, 3.3, 3.3, 0.7));
        fill(g, BLUE, rr(8.7, 4, 3.3, 3.3, 0.7));
        fill(g, BLUE, rr(4, 8.7, 3.3, 3.3, 0.7));
        fill(g, BLUE, rr(8.7, 8.7, 3.3, 3.3, 0.7));
    }
}
