package com.ll.render;

import java.awt.*;
import javax.swing.border.AbstractBorder;

// The classic pill border (ported verbatim from the panel).
@lombok.AllArgsConstructor
class RoundedBorder extends AbstractBorder {
Color color;
int vPad;
int hPad;

public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D gg = Ui.smooth(g);
    try {
        gg.setColor(color);
        gg.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
    } finally {
        gg.dispose();
    }
}

// The one-arg form AbstractBorder routes here, so this is the only override.
public Insets getBorderInsets(Component c, Insets insets) {
    insets.set(vPad, hPad, vPad, hPad);
    return insets;
}
}
