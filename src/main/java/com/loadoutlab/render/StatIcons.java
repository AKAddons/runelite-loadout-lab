package com.loadoutlab.render;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/** The classic stat icons, baked from the original painted art (see git
 * history for the painters): the hitsplat for max hit, the crosshair for
 * accuracy, and the fixed-width wrapper that gives every stat line one
 * hard left edge. */
class StatIcons {

    /** Baked images by resource name. A missing or unreadable resource
     * caches a fully transparent placeholder so paint degrades to blank -
     * it never throws inside a Swing paint pass. */
    static final Map<String, BufferedImage> BAKED = new ConcurrentHashMap<>();

    static BufferedImage baked(String name, int size) {
        return BAKED.computeIfAbsent(name, n -> {
            try (InputStream in = StatIcons.class.getResourceAsStream(n)) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) return img;
                }
            } catch (IOException ignored) {
                // fall through to the transparent placeholder
            }
            return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        });
    }

    /**
     * A square baked icon: a single pixel size used as both width and
     * height, drawn from a PNG baked at exactly that size from the original
     * paint code - "hitsplat" (the red hitsplat for the max-hit line) and
     * "crosshair" (the accuracy line), both glyph-safe.
     */
    static class SizedIcon implements Icon {
        /** Icon edge length in pixels. */
        final int size;
        final BufferedImage image;

        SizedIcon(String bakedName, int size) {
            this.size = size;
            image = baked("stat-" + bakedName + "-" + size + ".png", size);
        }

        public int getIconWidth() { return size; }

        public int getIconHeight() { return size; }

        public void paintIcon(Component c, Graphics g, int x, int y) { g.drawImage(image, x, y, size, size, null); }
    }


    /** A fixed-width box that centres its delegate - every stat-panel
     * line's icon occupies the SAME column width, so the values start on
     * one hard left edge (field spec: a strong visual column). */
    @lombok.AllArgsConstructor
    static class FixedWidthIcon implements Icon {
        static final int WIDTH = 20;
        static final int HEIGHT = 16;
        final Icon delegate;

        public int getIconWidth() { return WIDTH; }

        public int getIconHeight() { return Math.max(HEIGHT, delegate.getIconHeight()); }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            delegate.paintIcon(c, g, x + Math.max(0, (WIDTH - delegate.getIconWidth()) / 2),
                y + Math.max(0, (getIconHeight() - delegate.getIconHeight()) / 2));
        }
    }

}
