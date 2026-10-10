package com.messagepigeon;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** The application icon, in the sizes Windows asks for (title bar, taskbar, Alt+Tab). */
final class AppIcon {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() {}

    /** All available sizes; a window picks the one that fits. Empty if the resources are missing. */
    static List<Image> images() {
        List<Image> out = new ArrayList<>();
        for (int s : SIZES) {
            BufferedImage img = load(s);
            if (img != null) {
                out.add(img);
            }
        }
        return out;
    }

    /** The icon scaled smoothly to the given size, or null if the resources are missing. */
    static Image scaled(int size) {
        BufferedImage big = load(256);
        return big == null ? null : big.getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }

    private static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("/icons/app-" + size + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
