package com.loadoutlab.render;

import java.util.*;

/**
 * Typed accessors over the render-model's JSON-safe maps (the core
 * repo's docs/COMPANION_CONTRACT.md defines the shape). Renderers read
 * through these so a missing or oddly-typed field degrades to a benign
 * default instead of a ClassCastException mid-paint - the contract is
 * additive-versioned, and an older Core simply has fewer fields.
 */
final class Model {
    private Model() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return v instanceof List ? (List<Map<String, Object>>) v : List.of();
    }

    /** A list of plain (non-map) values under the key. */
    static List<Object> list2(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return v instanceof List ? (List<Object>) v : List.of();
    }

    static String str(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return v instanceof String ? (String) v : null;
    }

    static double num(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    static int id(Map<String, Object> node, String key) { return (int) num(node, key); }

    static boolean flag(Map<String, Object> node, String key) {
        Object v = node == null ? null : node.get(key);
        return Boolean.TRUE.equals(v);
    }

    /** The thrall fold a card carries: the page's thrall dps, or nothing
     * for a card that stood down (its autocast is on another book). */
    static double thrallsFor(Map<String, Object> card, double pageThralls) {
        return str(card, "arceuusStandDown") != null ? 0 : pageThralls;
    }
}
