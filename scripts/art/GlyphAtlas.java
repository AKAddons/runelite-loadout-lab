import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** The glyph tiles exactly as AsciiLoader paints a hi-res cell, so hires.py
 * matches against what the client draws. Run from the repo root:
 *     java -Djava.awt.headless=true scripts/art/GlyphAtlas.java
 * Writes scripts/art/atlas_9pt.png + atlas_9pt.txt (one tile per glyph). */
public class GlyphAtlas {
    static final int CW = 5, CH = 10;
    public static void main(String[] a) throws Exception {
        Font font;
        try (InputStream in = new FileInputStream("src/main/resources/com/loadoutlab/render/DejaVuSansMono.ttf")) {
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(9f);
        }
        StringBuilder chars = new StringBuilder(" ");
        for (char c : "!\"$%'()*+,-./:;=?@[\\]^_`{|}~#oOxXvVwS08".toCharArray()) chars.append(c);
        for (char c = 0x2580; c < 0x25a0; c++) chars.append(c);
        chars.append('¥').append('≡');
        BufferedImage im = new BufferedImage(CW * chars.length(), CH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        int base = baseline(font);
        System.out.println("baseline " + base);
        for (int i = 0; i < chars.length(); i++) {
            g.setClip(i * CW, 0, CW, CH);
            g.drawString(String.valueOf(chars.charAt(i)), i * CW, base);
        }
        g.dispose();
        javax.imageio.ImageIO.write(im, "png", new File("scripts/art/atlas_9pt.png"));
        Files.write(Paths.get("scripts/art/atlas_9pt.txt"), chars.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The baseline that puts the full block's ink on the cell's top row. */
    static int baseline(Font font) {
        BufferedImage im = new BufferedImage(20, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.drawString("█", 2, 20);
        g.dispose();
        int top = 0, bot = 0;
        for (int y = 39; y >= 0; y--) if ((im.getRGB(4, y) & 255) > 128) top = y;
        for (int y = 0; y < 40; y++) if ((im.getRGB(4, y) & 255) > 128) bot = y;
        System.out.println("block ink rows " + (bot - top + 1));
        return 20 - top;
    }
}
