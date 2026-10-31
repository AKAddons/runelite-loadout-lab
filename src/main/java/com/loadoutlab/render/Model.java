package com.loadoutlab.render;

import java.util.*;

// Typed accessors over the render-model's JSON-safe maps (the core
// repo's docs/COMPANION_CONTRACT.md defines the shape). Renderers read
// through these so a missing or oddly-typed field degrades to a benign
// default instead of a ClassCastException mid-paint - the contract is
// additive-versioned, and an older Core simply has fewer fields.
class Model {

static Map map(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return v instanceof Map ? (Map) v : null;
}

static List<Map> list(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return v instanceof List ? (List<Map>) v : List.of();
}

// A list of plain (non-map) values under the key.
static List<Object> list2(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return v instanceof List ? (List<Object>) v : List.of();
}

static String str(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return v instanceof String ? (String) v : null;
}

static double num(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return v instanceof Number ? ((Number) v).doubleValue() : 0;
}

static int id(Map node, String key) { return (int) num(node, key); }

static boolean flag(Map node, String key) {
    Object v = node == null ? null : node.get(key);
    return Boolean.TRUE.equals(v);
}

// The thrall fold a card carries: the page's thrall dps, or nothing
// for a card that stood down (its autocast is on another book).
static double thrallsFor(Map card, double pageThralls) {
    return str(card, "arceuusStandDown") != null ? 0 : pageThralls;
}
}
