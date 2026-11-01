package com.ll.render;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.*;
import java.util.*;
import java.io.BufferedReader;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.io.InputStream;

// The compute animation: painted frames in render/moods/<key>.txt, one file per
// pool, a token-free resource read when that boss computes (scripts/art/hires.py).
// '--' separates frames, '==' moods. A search with no art of its own plays the
// plugin's theme, Halloween in season. The timer runs ONLY while computing and
// dies on unmount (the mascot-era lesson: a hidden component's timer still burns
// the EDT).
class AsciiLoader extends JComponent {
static final String MOODS = "/com/ll/render/moods/";
static final int COLS = 43, ROWS = 24, CELL_W = 5, CELL_H = 10, CELL_BASE = 8;
// The bundled face, not Java's logical Monospaced: on Windows that draws ASCII in
// one font and borrows blocks and shades from another at other widths (Andrew
// 2026-10-15: the flask's rows slid sideways).
static final Font CELL_FONT = loadFont("DejaVuSansMono.ttf").deriveFont(9f);
// The last resort when a resource is missing: fail soft, never a crash.
static final List<String> PLAIN = List.of("Computing...");

static Font loadFont(String resource) {
    try (InputStream in = AsciiLoader.class.getResourceAsStream(resource)) {
        return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (Exception e) { return new Font(Font.MONOSPACED, Font.PLAIN, 12); }
}

String key;

// The pool key for the pending selection, or null for land.
void setKey(String key) { this.key = key; }

Timer timer = new Timer(140, e -> advance());
List<String> frames = PLAIN;
int tick;
String cells = "";

AsciiLoader() {
    setOpaque(false);
    setFocusable(false);
    setForeground(ResultCards.ACCENT);
}

// The boss's pool, or null when it has none.
static List<List<String>> pool(String key) {
    if (key == null) return null;
    InputStream in = AsciiLoader.class.getResourceAsStream(MOODS + key + ".txt");
    return in == null ? null : load(in);
}

// A frame's runs: <font color=#rrggbb> sets the ink, any other tag is skipped.
static void paintCells(Graphics2D g, String frame) {
    g.setFont(CELL_FONT);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    char[] text = frame.toCharArray();
    int x = 0, y = CELL_BASE;
    for (int i = 0; i < text.length; i++) {
        char c = text[i];
        if (c == '<') {
            if (text[i + 1] == 'f') g.setColor(new Color(Integer.parseInt(frame.substring(i + 13, i + 19), 16)));
            i = frame.indexOf('>', i);
        } else if (c == '\n') {
            x = 0;
            y += CELL_H;
        } else {
            if (c != ' ') g.drawChars(text, i, 1, x, y);
            x += CELL_W;
        }
    }
}

public Dimension getPreferredSize() {
    // A pixel over: the face's right-edge bar draws past its cell, and clipped there the rim's right side nearly vanished.
    return new Dimension(COLS * CELL_W + 1, ROWS * CELL_H + 1);
}

protected void paintComponent(Graphics g) {
    g.setColor(getForeground());
    paintCells((Graphics2D) g, cells);
}

// The key the current frames were picked for (a sentinel while idle).
String playingKey = "-";

void setRunning(boolean running) {
    // A search started while the last one still animates re-picks from
    // the NEW selection's pool (field report 2026-09-02: an interrupted
    // search kept the first search's animation).
    boolean repick = running && !Objects.equals(key, playingKey);
    if (running && (!timer.isRunning() || repick)) {
        List<List<String>> pool = pool(key);
        if (pool == null) pool = pool(seasonal(LocalDate.now()));
        frames = pool == null || pool.isEmpty() ? PLAIN : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        playingKey = key;
        tick = 0;
        advance();
        timer.start();
    } else if (!running) {
        timer.stop();
        playingKey = "-";
    }
}

public void removeNotify() {
    timer.stop();
    super.removeNotify();
}

// The land pool for a date: Halloween from Oct 15 to Oct 31, every year
// (ISS-01M5PR1FRK0XT2PHQ7HJD0D1FZ, Andrew 2026-10-24: "a halloween themed
// loading screen that can override the main loadout lab guy from 10/15-10/31
// (yearly)"), the other holidays by holidays.tsv (scripts/art/holidays.py), the
// flask otherwise. A boss's own art outranks the season.
static String seasonal(LocalDate today) {
    String iso = today.toString();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(AsciiLoader.class.getResourceAsStream("holidays.tsv"), StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
            String[] row = line.split("\t");
            // A short row has no year and repeats every year.
            String day = row[0].length() == 5 ? iso.substring(5) : iso;
            if (day.compareTo(row[0]) >= 0 && day.compareTo(row[1]) <= 0) return row[2];
        }
    } catch (Exception e) {
        // no table: the flask
    }
    return "flask";
}

void advance() {
    cells = frames.get(tick++ % frames.size());
    repaint();
}

// Package for the parse test. Frame separators are EXACTLY "--": art may
// legitimately start a line with dashes.
static List<List<String>> load(InputStream in) {
    List<List<String>> moods = new ArrayList<>();
    List<String> mood = new ArrayList<>();
    var frame = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
            boolean next = line.startsWith("==");
            if (next || line.equals("--")) {
                if (frame.length() > 0) mood.add(frame.toString());
                frame.setLength(0);
                if (next && !mood.isEmpty()) {
                    moods.add(mood);
                    mood = new ArrayList<>();
                }
                continue;
            }
            if (frame.length() > 0) frame.append('\n');
            frame.append(line);
        }
    } catch (Exception e) {
        // keep what was read; an empty pool falls back to the plain line
    }
    if (frame.length() > 0) mood.add(frame.toString());
    if (!mood.isEmpty()) moods.add(mood);
    return moods;
}
}
